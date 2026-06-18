package com.ziggfreed.kweebec.npc;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * File-backed idempotency marker for the auto-spawned "Grove Warden" guide
 * ({@link KweebecGuideSpawn}). The kweebec-owned mirror of MMO Skill Tree's
 * {@code MmoNpcPlacementStore} once-per-world marker, slimmed to the two things the
 * guide needs:
 * <ul>
 *   <li><b>Once-per-world marker</b> - which worlds already have a guide, so a
 *       <b>server reboot</b> (or a second player joining) never spawns a duplicate.
 *       Spawned NPCs persist natively in the world's entity store, so without a
 *       <b>persisted</b> marker a fresh boot would place ANOTHER guide beside the
 *       saved one, stacking up one per restart - the shipped bug. An in-memory
 *       {@code Set} (the prior approach) resets every boot and cannot prevent this.</li>
 *   <li><b>Per-world guide UUID</b> - recorded via the spawn callback so the
 *       {@code /kweebec spawnguide} debug hatch can despawn the existing guide
 *       (even one from a prior session) before placing a fresh one.</li>
 * </ul>
 *
 * <p>Persists to {@code <data dir>/guide-placements.json}. This is kweebec-owned (its
 * own file in its own data dir), NOT a shared/lifted store: the reason the marker was
 * never lifted into {@code ziggfreed-common} is that a single shared file would clobber
 * across mods, NOT that it should be transient. World keyed by {@code World.getName()}.
 *
 * <p>All mutators run on the world thread (inside {@code world.execute}); {@code save()}
 * does one small synchronous JSON write, the same pattern hyMMO uses.
 */
public final class KweebecGuidePlacementStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static KweebecGuidePlacementStore instance;

    public static synchronized KweebecGuidePlacementStore getInstance() {
        if (instance == null) {
            instance = new KweebecGuidePlacementStore();
        }
        return instance;
    }

    private KweebecGuidePlacementStore() {
    }

    /** Gson root document; unknown keys ignored so future fields stay additive. */
    private static final class Doc {
        List<String> spawnedWorlds = new ArrayList<>();
        Map<String, String> guideUuidByWorld = new HashMap<>();
    }

    @Nullable
    private Path dataPath;
    private final java.util.Set<String> spawnedWorlds = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> guideByWorld = new ConcurrentHashMap<>();

    /** Resolve {@code <dataDir>/guide-placements.json} and load it (a null dir keeps the defaults). */
    public void init(@Nullable Path dataDir) {
        if (dataDir == null) {
            SafeLog.warn("[Kweebec] guide placements: no data dir, marker is in-memory only");
            return;
        }
        this.dataPath = dataDir.resolve("guide-placements.json");
        load();
    }

    private void load() {
        spawnedWorlds.clear();
        guideByWorld.clear();
        if (dataPath == null || !Files.exists(dataPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataPath)) {
            Doc doc = GSON.fromJson(reader, Doc.class);
            if (doc == null) {
                return;
            }
            if (doc.spawnedWorlds != null) {
                spawnedWorlds.addAll(doc.spawnedWorlds);
            }
            if (doc.guideUuidByWorld != null) {
                for (Map.Entry<String, String> e : doc.guideUuidByWorld.entrySet()) {
                    try {
                        guideByWorld.put(e.getKey(), UUID.fromString(e.getValue()));
                    } catch (IllegalArgumentException ignored) {
                        // skip a malformed UUID rather than failing the whole load
                    }
                }
            }
            SafeLog.info("[Kweebec] guide placements loaded: " + spawnedWorlds.size() + " world(s)");
        } catch (Exception e) {
            SafeLog.warn("[Kweebec] guide placements: failed to load " + dataPath + ": " + e.getMessage());
        }
    }

    private synchronized void save() {
        if (dataPath == null) {
            return;
        }
        try {
            Files.createDirectories(dataPath.getParent());
            Doc doc = new Doc();
            doc.spawnedWorlds = new ArrayList<>(spawnedWorlds);
            doc.guideUuidByWorld = new HashMap<>();
            for (Map.Entry<String, UUID> e : guideByWorld.entrySet()) {
                doc.guideUuidByWorld.put(e.getKey(), e.getValue().toString());
            }
            try (Writer writer = Files.newBufferedWriter(dataPath)) {
                GSON.toJson(doc, writer);
            }
        } catch (Exception e) {
            SafeLog.warn("[Kweebec] guide placements: failed to save " + dataPath + ": " + e.getMessage());
        }
    }

    /** True when {@code world} already has a guide (this boot or a persisted prior one). */
    public boolean hasSpawned(@Nonnull String world) {
        return spawnedWorlds.contains(world);
    }

    /** Marks {@code world} as having a guide and persists (no-op if already marked). */
    public void markSpawned(@Nonnull String world) {
        if (spawnedWorlds.add(world)) {
            save();
        }
    }

    /** Records (and persists) the placed guide's UUID for {@code world} (used by the debug reposition). */
    public void recordGuide(@Nonnull String world, @Nonnull UUID uuid) {
        guideByWorld.put(world, uuid);
        save();
    }

    /** The recorded guide UUID for {@code world}, or null if none. */
    @Nullable
    public UUID getGuide(@Nonnull String world) {
        return guideByWorld.get(world);
    }
}
