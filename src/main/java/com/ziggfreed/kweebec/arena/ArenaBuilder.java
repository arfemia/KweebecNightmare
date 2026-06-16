package com.ziggfreed.kweebec.arena;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Stamps the authored horror beats (shrine pillars, the Heartwood Gate, the exit)
 * onto the procedurally-generated grove via {@code PrefabUtil.paste} at the
 * {@link ArenaLayout} anchors. Best-effort: prefab resolution does blocking I/O
 * off the world thread, then each paste hops onto the world thread. Missing
 * prefabs are logged and skipped - the round is fully playable off the anchors
 * regardless (the geometry is a visual layer, never a logic dependency).
 *
 * <p>Verification note: that a JSON-content instance generates a floor and that
 * {@code PrefabUtil.paste} renders faithfully into a fresh instance are both on
 * the in-game handoff checklist.
 */
public final class ArenaBuilder {

    private static final String SHRINE_PREFAB = "KweebecNightmare/Shrine";
    private static final String GATE_PREFAB = "KweebecNightmare/Gate";
    private static final String EXIT_PREFAB = "KweebecNightmare/Exit";

    private ArenaBuilder() {
    }

    /**
     * Resolve + paste the arena prefabs. Safe to call from the instance world
     * thread; the blocking prefab I/O runs on a background thread first.
     */
    public static void build(@Nonnull RoundInstance round, @Nonnull World world) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                IPrefabBuffer shrine = load(SHRINE_PREFAB);
                IPrefabBuffer gate = load(GATE_PREFAB);
                IPrefabBuffer exit = load(EXIT_PREFAB);

                if (shrine != null) {
                    chase.shrines().forEach(s -> paste(world, shrine, s.anchor()));
                }
                if (gate != null) {
                    paste(world, gate, ArenaLayout.GATE);
                }
                if (exit != null) {
                    paste(world, exit, ArenaLayout.ESCAPE);
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] arena build failed: " + t.getMessage());
            }
        });
    }

    @Nullable
    private static IPrefabBuffer load(@Nonnull String key) {
        try {
            Path path = PrefabStore.get().findAssetPrefabPath(key);
            if (path == null) {
                return null;
            }
            return PrefabBufferUtil.getCached(path);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] prefab '" + key + "' not loaded: " + t.getMessage());
            return null;
        }
    }

    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at) {
        Vector3i pos = new Vector3i(
                (int) Math.floor(at.x()),
                (int) Math.floor(at.y() - 1.0), // floor surface, one below stand-Y
                (int) Math.floor(at.z()));
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(buffer, world, pos, store);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec] prefab paste failed at " + pos + ": " + t.getMessage());
            }
        });
    }

    /** Tiny indirection so the verified paste signature lives in one place. */
    private static final class PrefabUtilPaste {
        private static final Random RNG = new Random(0xC0C0L);

        static void paste(@Nonnull IPrefabBuffer buffer, @Nonnull World world,
                          @Nonnull Vector3i pos, @Nonnull Store<EntityStore> store) {
            com.hypixel.hytale.server.core.util.PrefabUtil.paste(
                    buffer, world, pos, Rotation.None, false, RNG, store);
        }
    }
}
