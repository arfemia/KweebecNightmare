package com.ziggfreed.kweebec.mode.clash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.entity.PlayerModelService;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Kweebec policy over the generic {@link PlayerModelService}: reskin a Clash player to the rule-set's
 * model (e.g. {@code Kweebec_Sapling}) on entry/respawn and restore their real model on exit. The swapped
 * model is PERSISTED to the player's entity on disk (the engine writes it to {@code PersistentModel}), so a
 * missed restore would strand a tiny-hitbox Sapling in the overworld across restarts. The guard: a
 * file-backed "still swapped" set (mirroring {@code RoundInventoryGuard}) drives an unconditional
 * restore-on-PlayerReady catch-all that covers every exit path - normal eject, voluntary leave, disconnect,
 * relog, and a crash mid-match. All engine calls run on the world thread (the caller guarantees it) and are
 * try-guarded.
 */
public final class ClashModelSwapper {

    private static final Set<UUID> SWAPPED = ConcurrentHashMap.newKeySet();
    @Nullable
    private static volatile Path file;

    private ClashModelSwapper() {
    }

    /** Load the persisted "still swapped" set on plugin setup; call before the PlayerReady catch-all binds. */
    public static void init(@Nullable Path dataDir) {
        if (dataDir == null) {
            return;
        }
        Path f = dataDir.resolve("clash-model-swapped.txt");
        file = f;
        try {
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (!s.isEmpty()) {
                        try {
                            SWAPPED.add(UUID.fromString(s));
                        } catch (IllegalArgumentException ignored) {
                            // skip a corrupt line
                        }
                    }
                }
            }
        } catch (IOException e) {
            SafeLog.warn("[Kweebec] failed to load clash model-swap store: " + e.getMessage());
        }
    }

    /**
     * Apply the model swap to a player (on entry / respawn). No-op (and not marked) when the model id is
     * blank or unresolved, so a typo never leaves a player invisible or needing a bogus restore.
     */
    public static void apply(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                             @Nonnull UUID uuid, @Nullable String modelId, double scale) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        boolean applied = PlayerModelService.apply(ref, store, modelId, (float) scale);
        if (applied) {
            SWAPPED.add(uuid);
            save();
        }
    }

    /** Restore a player's real model (rebuilt from their skin) and clear the swapped flag. Idempotent. */
    public static void restore(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull UUID uuid) {
        if (!SWAPPED.contains(uuid)) {
            return;
        }
        PlayerModelService.restore(ref, store);
        SWAPPED.remove(uuid);
        save();
    }

    /**
     * The PlayerReady catch-all: if this player is flagged still-swapped (any exit path, incl. relog/crash),
     * restore their real model now that they are back in a normal world. The single guarantee against a
     * stranded Sapling. World thread.
     */
    public static void restoreOnReady(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref,
                                      @Nonnull Store<EntityStore> store) {
        restore(ref, store, uuid);
    }

    /** Whether a player is currently flagged as swapped (for diagnostics / the catch-all). */
    public static boolean isSwapped(@Nonnull UUID uuid) {
        return SWAPPED.contains(uuid);
    }

    private static void save() {
        Path f = file;
        if (f == null) {
            return;
        }
        try {
            List<String> lines = new ArrayList<>(SWAPPED.size());
            for (UUID u : SWAPPED) {
                lines.add(u.toString());
            }
            Files.write(f, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            SafeLog.warn("[Kweebec] failed to save clash model-swap store: " + e.getMessage());
        }
    }
}
