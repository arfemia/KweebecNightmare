package com.ziggfreed.kweebec.score;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.io.FileUtil;
import com.ziggfreed.kweebec.api.PlayerScore;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The per-playercount, UUID-keyed leaderboard, persisted to the plugin's own data
 * directory as {@code leaderboard.json}. It buckets by PARTY SIZE so a solo run and a
 * four-player run are ranked separately, and records each player's best score and best
 * winning completion time. No UI this pass; {@link #forPartySize(int)} is the query
 * seam a future in-game board or an installed MMO surfaces.
 *
 * <p><b>Durability + threading.</b> Loaded once at plugin setup. Writes are deferred:
 * {@link #record} mutates the in-memory {@link ConcurrentHashMap} immediately (safe from
 * the world-thread resolve path) and schedules a DEBOUNCED atomic flush off-thread
 * ({@link FileUtil#writeStringAtomic} temp-file rename + {@code .bak} fallback), so the
 * round-resolve thread never blocks on disk and concurrent round-ends coalesce into one
 * write. A corrupt file degrades to the {@code .bak}, then to an empty board - a
 * leaderboard write failure never affects the round.
 */
public final class Leaderboard {

    private static final String FILE_NAME = "leaderboard.json";
    private static final long FLUSH_DEBOUNCE_SECONDS = 3L;

    private static final Leaderboard INSTANCE = new Leaderboard();

    @Nonnull
    public static Leaderboard getInstance() {
        return INSTANCE;
    }

    /** One player's best result in a party-size bucket. Public mutable fields for Gson. */
    public static final class Entry {
        public int bestScore;
        /** Best (lowest) WINNING completion time in seconds; 0 = no win recorded yet. */
        public int bestTimeSeconds;
        public int plays;
        public long lastUpdatedMs;
        /** Last-known display name of the player, captured at record time; null on legacy entries. */
        public String name;
    }

    /** On-disk shape: partySize(string) -> uuid(string) -> entry. */
    private static final class Dto {
        Map<String, Map<String, Entry>> buckets;
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<UUID, Entry>> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    @Nullable
    private volatile Path file;

    private Leaderboard() {
    }

    /** Resolve the data file under the plugin data dir and load any existing board. Call once at setup. */
    public void init(@Nullable Path dataDir) {
        if (dataDir == null) {
            SafeLog.warn("[Kweebec] leaderboard: no data directory; persistence disabled this session.");
            return;
        }
        this.file = dataDir.resolve(FILE_NAME);
        load();
    }

    private void load() {
        Path f = file;
        if (f == null || !Files.exists(f)) {
            return;
        }
        try {
            populate(gson.fromJson(Files.readString(f), Dto.class));
            return;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] leaderboard.json unreadable (" + t.getMessage() + "); trying .bak");
        }
        try {
            Path bak = f.resolveSibling(f.getFileName().toString() + ".bak");
            if (Files.exists(bak)) {
                populate(gson.fromJson(Files.readString(bak), Dto.class));
                SafeLog.info("[Kweebec] leaderboard recovered from .bak");
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] leaderboard .bak unreadable; starting empty: " + t.getMessage());
        }
    }

    private void populate(@Nullable Dto dto) {
        buckets.clear();
        if (dto == null || dto.buckets == null) {
            return;
        }
        for (Map.Entry<String, Map<String, Entry>> be : dto.buckets.entrySet()) {
            int ps;
            try {
                ps = Integer.parseInt(be.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            ConcurrentHashMap<UUID, Entry> bucket = new ConcurrentHashMap<>();
            if (be.getValue() != null) {
                for (Map.Entry<String, Entry> pe : be.getValue().entrySet()) {
                    if (pe.getValue() == null) {
                        continue;
                    }
                    try {
                        bucket.put(UUID.fromString(pe.getKey()), pe.getValue());
                    } catch (Throwable ignored) {
                        // skip a malformed uuid key
                    }
                }
            }
            buckets.put(ps, bucket);
        }
    }

    /**
     * Record a player's round result into the party-size bucket: bump their play count, store their
     * latest display name, keep the higher score, and (for a win) keep the lower completion time.
     * Schedules a debounced flush. Safe to call from the world-thread resolve path.
     */
    public void record(int partySize, @Nonnull UUID uuid, @Nullable String name, @Nonnull PlayerScore score) {
        if (partySize <= 0) {
            return;
        }
        ConcurrentHashMap<UUID, Entry> bucket = buckets.computeIfAbsent(partySize, k -> new ConcurrentHashMap<>());
        bucket.compute(uuid, (k, existing) -> {
            Entry e = existing != null ? existing : new Entry();
            e.plays++;
            e.lastUpdatedMs = System.currentTimeMillis();
            if (name != null && !name.isBlank()) {
                e.name = name;
            }
            if (score.total() > e.bestScore) {
                e.bestScore = score.total();
            }
            if (score.win()) {
                int t = score.durationSeconds();
                if (e.bestTimeSeconds <= 0 || t < e.bestTimeSeconds) {
                    e.bestTimeSeconds = t;
                }
            }
            return e;
        });
        scheduleFlush();
    }

    /** A snapshot of one party-size bucket (uuid -> best entry). Never null. */
    @Nonnull
    public Map<UUID, Entry> forPartySize(int partySize) {
        Map<UUID, Entry> bucket = buckets.get(partySize);
        return bucket == null ? Map.of() : Map.copyOf(bucket);
    }

    private void scheduleFlush() {
        if (file == null) {
            return;
        }
        if (flushPending.compareAndSet(false, true)) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                flushPending.set(false);
                flushNow();
            }, FLUSH_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void flushNow() {
        Path f = file;
        if (f == null) {
            return;
        }
        try {
            Dto dto = new Dto();
            dto.buckets = new HashMap<>();
            for (Map.Entry<Integer, ConcurrentHashMap<UUID, Entry>> be : buckets.entrySet()) {
                Map<String, Entry> bucket = new HashMap<>();
                for (Map.Entry<UUID, Entry> pe : be.getValue().entrySet()) {
                    bucket.put(pe.getKey().toString(), pe.getValue());
                }
                dto.buckets.put(be.getKey().toString(), bucket);
            }
            if (f.getParent() != null) {
                Files.createDirectories(f.getParent());
            }
            FileUtil.writeStringAtomic(f, gson.toJson(dto), true);
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] leaderboard flush failed: " + t.getMessage());
        }
    }
}
