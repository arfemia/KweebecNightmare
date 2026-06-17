package com.ziggfreed.kweebec.arena;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.world.SurfaceProbe;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.mode.chase.ShrineState;
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
    /**
     * A native Kweebec structure pasted as the grove's village-ruin CENTERPIECE (the hybrid "ruined
     * village in a dead forest" identity). Offset SOUTH of spawn (+z) so it never traps a spawning
     * player and stays clear of the gate corridor (-z). A healthy-green Oak well for now; a
     * corruption-repaint (dead-leaf reskin) is a follow-up. Pasted ONCE (not in the objective re-paste
     * loop) so it never stacks on its own pasted top the way a re-probed cave shaft would.
     */
    private static final String CENTERPIECE_PREFAB = "Npc/Kweebec/Oak/Well/Kweebec_Oak_Well_001";
    private static final double CENTERPIECE_Z_OFFSET = 10.0;
    /**
     * The underground-objective prefabs: a carved descent shaft + lit chamber + the cave shrine pillar.
     * Two interchangeable descent styles, alternated per cave shrine (by index) for variety - a
     * facing-independent spiral staircase and the playtest-proven ladder.
     */
    private static final String[] SHAFT_PREFABS = {
            "KweebecNightmare/Relight_Shaft",          // [0] spiral staircase
            "KweebecNightmare/Relight_Shaft_Ladder",   // [1] ladder
    };

    // The decorative dead/blighted grove TREES are now scattered by the worldgen biome itself
    // (KweebecNightmare_Grove Props[] - a Type:Prefab prop over native Poisoned / Ash_twisted /
    // Petrified_Dead / Dry_Dead / Ash_Dead prefabs, floor-snapped by the biome scanner), per the
    // design's "trees from worldgen, not manual paste". The old runtime foliage ring is gone: it cost
    // a synchronous world-thread paste stall at round start and contended with the worldgen chunk
    // race, both of which the biome scatter avoids. ArenaBuilder now stamps ONLY the gameplay beats.

    private ArenaBuilder() {
    }

    /**
     * Resolve + paste the arena prefabs. Safe to call from the instance world
     * thread; the blocking prefab I/O runs on a background thread first.
     */
    /** Seconds after the initial paste to re-stamp the objective prefabs, beating the worldgen race. */
    private static final long[] OBJECTIVE_REPASTE_DELAYS_SEC = {4L, 9L};

    public static void build(@Nonnull RoundInstance round, @Nonnull World world) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        // Immediate pass: the gameplay objective beats + the village-ruin centerpiece (pasted once).
        // Ambient grove trees are now scattered by the worldgen biome (KweebecNightmare_Grove Props[]).
        CompletableFuture.runAsync(() -> {
            pasteObjectives(world, chase);
            pasteCenterpiece(world);
        });
        // Re-stamp the OBJECTIVES a couple of times during PREP. On a busy relaunch the first paste
        // can be overwritten by chunk generation still filling those chunks (the objectives paste
        // before their chunks finish; the later foliage already survives). Pastes are idempotent
        // (fixed anchors + seeded RNG), so a re-paste is a safe no-op once the prefab has stuck.
        for (long delay : OBJECTIVE_REPASTE_DELAYS_SEC) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                if (round.isResolved()) {
                    return;
                }
                World w = round.world();
                if (w != null) {
                    CompletableFuture.runAsync(() -> pasteObjectives(w, chase));
                }
            }, delay, TimeUnit.SECONDS);
        }
    }

    /**
     * Resolve + paste the authored objective beats (surface shrine ring, exit, and the underground
     * cave). Idempotent. NOTE: the Heartwood Gate is deliberately NOT pasted here - it is revealed by
     * {@link com.ziggfreed.kweebec.mode.chase.ChaseMode}'s {@code openGate} only once every shrine is
     * lit (the dramatic "the gate opens" beat), via {@link #pasteGate(World)}.
     */
    private static void pasteObjectives(@Nonnull World world, @Nonnull ChaseState chase) {
        try {
            IPrefabBuffer shrine = load(SHRINE_PREFAB);
            IPrefabBuffer exit = load(EXIT_PREFAB);
            IPrefabBuffer[] shafts = new IPrefabBuffer[SHAFT_PREFABS.length];
            for (int i = 0; i < SHAFT_PREFABS.length; i++) {
                shafts[i] = load(SHAFT_PREFABS[i]);
            }

            int caveIndex = 0;
            for (var s : chase.shrines()) {
                Anchor a = s.anchor();
                if (a.y() >= ArenaLayout.STAND_Y - 1.0) {
                    // Surface ring shrine.
                    if (shrine != null) {
                        paste(world, shrine, a);
                    }
                } else {
                    // Underground shrine: carve the descent shaft + lit chamber + baked shrine pillar
                    // straight into the solid terrain, alternating the descent style (spiral / ladder)
                    // per cave for variety. force=true so the prefab's Empty cells UNCONDITIONALLY clear
                    // the rock (PrefabUtil's setBlock path) - the descend-and-return shrine MUST be
                    // reachable or allShrinesLit() never fires and the round is unwinnable. On the
                    // now-rolling grove terrain the shaft top is floor-snapped to the LOCAL surface and
                    // the shrine's chamber stand Y is re-pointed to match (see pasteCaveShaft).
                    IPrefabBuffer shaft = shafts[caveIndex % shafts.length];
                    if (shaft != null) {
                        pasteCaveShaft(world, shaft, s);
                    }
                    caveIndex++;
                }
            }
            if (exit != null) {
                paste(world, exit, ArenaLayout.ESCAPE);
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] arena objective paste failed: " + t.getMessage());
        }
    }

    /**
     * Reveal the Heartwood Gate - called from {@code ChaseMode.openGate} when every shrine is lit, so
     * the gate appears only at the climactic moment (it never exists during the round). Best-effort:
     * the blocking prefab load runs off-thread, then {@link #paste} hops to the world thread. The
     * escape win is pure-anchor logic ({@code checkEscapes} crossing {@code GATE.z}), so a missing
     * prefab only costs the visual, never the win.
     */
    public static void pasteGate(@Nonnull World world) {
        CompletableFuture.runAsync(() -> {
            try {
                IPrefabBuffer gate = load(GATE_PREFAB);
                if (gate != null) {
                    paste(world, gate, ArenaLayout.GATE);
                } else {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] gate reveal: prefab '" + GATE_PREFAB + "' not found");
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] gate reveal paste failed: " + t.getMessage());
            }
        });
    }

    /**
     * Paste the village-ruin centerpiece ONCE (never re-pasted), surface-snapped and offset south of
     * spawn. Purely decorative: a missing prefab is logged + skipped, and an occasional worldgen-race
     * clobber only costs the cosmetic - the round plays regardless.
     */
    private static void pasteCenterpiece(@Nonnull World world) {
        try {
            IPrefabBuffer centerpiece = load(CENTERPIECE_PREFAB);
            if (centerpiece != null) {
                paste(world, centerpiece, new Anchor(ArenaLayout.SPAWN.x(),
                        ArenaLayout.STAND_Y, ArenaLayout.SPAWN.z() + CENTERPIECE_Z_OFFSET, 0f));
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] centerpiece paste failed: " + t.getMessage());
        }
    }

    @Nullable
    private static IPrefabBuffer load(@Nonnull String key) {
        try {
            // findAssetPrefabPath does Files.exists on the literal key, so the
            // extension-less key never matches '<name>.prefab.json'. Mirror the
            // vanilla resolver (PastePrefabEffect.resolveDirectPrefabPath): try
            // the key, then fall back to key + ".prefab.json".
            Path path = PrefabStore.get().findAssetPrefabPath(key);
            if (path == null && !key.endsWith(".prefab.json")) {
                path = PrefabStore.get().findAssetPrefabPath(key + ".prefab.json");
            }
            if (path == null) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] prefab '" + key + "' NOT FOUND (no Server/Prefabs/" + key
                                + ".prefab.json in any loaded pack)");
                return null;
            }
            IPrefabBuffer buffer = PrefabBufferUtil.getCached(path);
            KweebecNightmarePlugin.LOGGER.atInfo().log(
                    "[Kweebec] prefab '" + key + "' resolved -> " + path);
            return buffer;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] prefab '" + key + "' load failed: " + t.getMessage());
            return null;
        }
    }

    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at) {
        paste(world, buffer, at, true, false);
    }

    /**
     * Paste an authored beat (shrine / exit / gate) at an anchor, FLOOR-SNAPPED to the real generated
     * surface. {@code verbose} INFO-logs the placement; a FAILURE always WARNs.
     *
     * <p>Placement: prefab buffer coords are stored ANCHOR-RELATIVE (the authored anchor sits at stored
     * (0,0,0)), and {@code PrefabUtil.paste} places stored (0,0,0) exactly at {@code position}. The
     * grove is now natural rolling terrain (the flat play disc is gone), so a hardcoded Y would float
     * or bury every beat; instead we probe the LOCAL top-solid Y with {@link SurfaceProbe} on the world
     * thread (after PREP chunk-gen, so the column is queryable) and land the anchor there. If the probe
     * misses (unloaded chunk) it degrades to the anchor's authored floor block ({@code at.y - 1}).
     */
    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at,
                              boolean verbose, boolean force) {
        int x = (int) Math.floor(at.x());
        int z = (int) Math.floor(at.z());
        int fallbackTop = (int) Math.floor(at.y() - 1.0);
        world.execute(() -> {
            try {
                int topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop);
                Vector3i pos = new Vector3i(x, topY, z);
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(buffer, world, pos, store, force);
                if (verbose) {
                    KweebecNightmarePlugin.LOGGER.atInfo().log(
                            "[Kweebec] prefab pasted at " + pos + " (surface-snapped)");
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] prefab paste failed at (" + x + "," + z + "): " + t.getMessage());
            }
        });
    }

    /** The cave-shaft prefab's surface-to-chamber depth (authored for the old flat case: floor Y79 -> chamber stand Y66). */
    private static final double CAVE_SHAFT_DEPTH = ArenaLayout.FLOOR_Y - ArenaLayout.CAVE_STAND_Y;

    /**
     * Carve one underground shrine's descent shaft + chamber, floor-snapped to the rolling surface:
     * probe the LOCAL top-solid Y, force-paste the shaft top there (so the entrance meets the surface),
     * and RE-POINT the shrine's chamber stand Y to {@code surface - shaftDepth} so the underground
     * channel Y-band in {@code ChaseMode} matches the carved chamber. Idempotent: a fixed surface gives
     * the same Y on the +4s/+9s re-paste. On a probe miss it falls back to the old flat-disc floor (Y79)
     * -> chamber stand Y66, the original behavior.
     */
    private static void pasteCaveShaft(@Nonnull World world, @Nonnull IPrefabBuffer shaft, @Nonnull ShrineState cave) {
        Anchor a = cave.anchor();
        int x = (int) Math.floor(a.x());
        int z = (int) Math.floor(a.z());
        int fallbackTop = (int) Math.floor(ArenaLayout.FLOOR_Y);
        world.execute(() -> {
            try {
                // IDEMPOTENT re-paste: probe the NATURAL surface ONCE (first pass) and remember it on
                // the shrine. The +4s/+9s re-pastes reuse that Y instead of re-probing - because the
                // first carve REPLACES the surface at this (x,z) with the shaft's own blocks, so a
                // re-probe would find a different top and stack a second/third shaft on top.
                int topY = cave.caveSurfaceTopY();
                if (topY == Integer.MIN_VALUE) {
                    topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop);
                    cave.setCaveSurfaceTopY(topY);
                    // Re-point the chamber stand Y ONLY on the first resolve (the channel Y-band match).
                    cave.setAnchor(new Anchor(a.x(), topY - CAVE_SHAFT_DEPTH, a.z(), a.yaw()));
                }
                Vector3i pos = new Vector3i(x, topY, z);
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(shaft, world, pos, store, true);
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] cave shaft carved at " + pos + ", chamber stand y=" + (topY - CAVE_SHAFT_DEPTH));
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] cave shaft carve failed at (" + x + "," + z + "): " + t.getMessage());
            }
        });
    }

    /** Tiny indirection so the verified paste signature lives in one place. */
    private static final class PrefabUtilPaste {
        private static final Random RNG = new Random(0xC0C0L);

        /**
         * @param force {@code true} routes the paste through {@code chunk.setBlock} (UNCONDITIONAL -
         *              writes every cell incl. {@code Empty}, so the prefab can CARVE solid terrain);
         *              {@code false} uses {@code placeBlock} (respects placement rules, for surface
         *              decoration that only adds blocks).
         */
        static void paste(@Nonnull IPrefabBuffer buffer, @Nonnull World world,
                          @Nonnull Vector3i pos, @Nonnull Store<EntityStore> store, boolean force) {
            com.hypixel.hytale.server.core.util.PrefabUtil.paste(
                    buffer, world, pos, Rotation.None, force, RNG, store);
        }
    }
}
