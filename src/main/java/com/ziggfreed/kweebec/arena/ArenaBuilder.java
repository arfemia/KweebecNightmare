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
    /**
     * The underground-objective prefabs: a carved descent shaft + lit chamber + the cave shrine pillar.
     * Two interchangeable descent styles, alternated per cave shrine (by index) for variety - a
     * facing-independent spiral staircase and the playtest-proven ladder.
     */
    private static final String[] SHAFT_PREFABS = {
            "KweebecNightmare/Relight_Shaft",          // [0] spiral staircase
            "KweebecNightmare/Relight_Shaft_Ladder",   // [1] ladder
    };

    /**
     * Real VANILLA dead/blighted tree + stump prefabs stamped OUTSIDE the shrine ring as the grove's
     * dead-forest frame (atmosphere only, never logic). Keys validated against hytale-resources; they
     * resolve via {@link #load}'s key + ".prefab.json" fallback. Ambient ground cover (dead grass,
     * brambles, glowing mushrooms) is scattered by the worldgen biome itself; trees stay on this
     * controlled ring so they never land on spawn / a shrine / the gate corridor / the hunter den.
     */
    private static final String[] FOLIAGE_PREFABS = {
            "Trees/Burnt_dead/Stage_2/Burnt_dead_Stage2_001",
            "Trees/Ash_Dead/Stage_2/Ash_Dead_Stage2_001",
            "Trees/Fir_Dead/Stage_1/Fir_Dead_Stage1_001",
            "Plants/Twisted_Wood/Fire/Twisted_Wood_Fire_001",
            "Trees/Dry_Dead/Dry_Dead_001",
            "Trees/Oak/Stumps/Oak_Stumps_001",
    };
    /** Radius (blocks) of the foliage ring. Outside the 26-block shrine ring; clear of the gate corridor. */
    private static final double FOLIAGE_RING_RADIUS = 36.0;
    /**
     * Number of tree clumps evenly placed around the ring. Kept modest: each {@code PrefabUtil.paste}
     * is a synchronous world-thread block write (~0.5s during initial chunk-gen), and these vanilla
     * trees are larger than the old block-stacks, so a sparse ring frames the arena without a long
     * world-thread stall at round start.
     */
    private static final int FOLIAGE_RING_COUNT = 10;
    /** Foliage RNG seed - deterministic so every client agrees on the dressing. */
    private static final long FOLIAGE_SEED = 0x6B776565L;
    /** Keep foliage clear of the gate/exit corridor (negative Z near arena-center X). */
    private static final double GATE_CORRIDOR_HALF_WIDTH = 8.0;
    /** Keep foliage this many blocks (squared) clear of the hunter den so no clump paints the spawn. */
    private static final double DEN_CLEARANCE_SQ = 8.0 * 8.0;

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
        // Immediate pass: objectives + the decorative foliage ring.
        CompletableFuture.runAsync(() -> {
            pasteObjectives(world, chase);
            pasteFoliageRing(world);
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
                    // straight into the solid play disc, alternating the descent style (spiral / ladder)
                    // per cave for variety. force=true so the prefab's Empty cells UNCONDITIONALLY clear
                    // the rock (PrefabUtil's setBlock path, not placeBlock) - the descend-and-return
                    // shrine MUST be reachable or allShrinesLit() never fires and the round is unwinnable.
                    // Pasted at STAND_Y so the shared paste() lands the prefab origin at FLOOR_Y (Y79).
                    IPrefabBuffer shaft = shafts[caveIndex % shafts.length];
                    if (shaft != null) {
                        paste(world, shaft, new Anchor(a.x(), ArenaLayout.STAND_Y, a.z()), true, true);
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

    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at,
                              boolean verbose) {
        paste(world, buffer, at, verbose, false);
    }

    /**
     * Paste a prefab at an anchor. {@code verbose} controls the success-log level:
     * the authored beats (shrine/gate/exit) log at INFO so the next playtest log
     * confirms they pasted; the decorative foliage ring pastes quietly (one INFO
     * summary is logged by the caller) so its clumps do not bury that signal.
     * A paste FAILURE always logs at WARNING regardless.
     *
     * <p>Placement: prefab buffer coords are stored ANCHOR-RELATIVE (the authored anchor sits at
     * stored (0,0,0)), and {@code PrefabUtil.paste} places stored (0,0,0) exactly at {@code position}.
     * So we paste the anchor at the floor block ({@code FLOOR_Y}, one below stand-Y): the authored
     * beats (anchor at their base) sit on the surface, and real vanilla trees (anchor at the trunk
     * base, a few root blocks below) bury those roots underground and rise the trunk above - the
     * intended look. The X/Z anchor already lands on the target, so it is not subtracted.
     */
    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at,
                              boolean verbose, boolean force) {
        Vector3i pos = new Vector3i(
                (int) Math.floor(at.x()),
                (int) Math.floor(at.y() - 1.0), // floor surface (FLOOR_Y), one below stand-Y; anchor lands here
                (int) Math.floor(at.z()));
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(buffer, world, pos, store, force);
                if (verbose) {
                    KweebecNightmarePlugin.LOGGER.atInfo().log(
                            "[Kweebec] prefab pasted at " + pos);
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] prefab paste failed at " + pos + ": " + t.getMessage());
            }
        });
    }

    /**
     * Stamp a deterministic ring of decorative blighted-grove foliage OUTSIDE the
     * shrine ring, skipping the gate/exit corridor so the play space stays clear.
     * Pure atmosphere - missing prefabs are skipped and never affect the round.
     */
    private static void pasteFoliageRing(@Nonnull World world) {
        IPrefabBuffer[] foliage = new IPrefabBuffer[FOLIAGE_PREFABS.length];
        boolean any = false;
        for (int i = 0; i < FOLIAGE_PREFABS.length; i++) {
            foliage[i] = load(FOLIAGE_PREFABS[i]);
            any |= foliage[i] != null;
        }
        if (!any) {
            return;
        }
        Random rng = new Random(FOLIAGE_SEED);
        int placed = 0;
        for (int i = 0; i < FOLIAGE_RING_COUNT; i++) {
            double theta = (2.0 * Math.PI * i) / FOLIAGE_RING_COUNT;
            double radius = FOLIAGE_RING_RADIUS + rng.nextDouble() * 6.0; // 36..42 jitter
            double x = ArenaLayout.SPAWN.x() + radius * Math.sin(theta);
            double z = ArenaLayout.SPAWN.z() - radius * Math.cos(theta);
            // Keep the gate/exit corridor (negative Z, near arena-center X) clear.
            if (z < ArenaLayout.GATE.z() + 6.0 && Math.abs(x - ArenaLayout.SPAWN.x()) < GATE_CORRIDOR_HALF_WIDTH) {
                continue;
            }
            // Keep the hunter den / PREP entry (positive Z) clear so a clump never
            // paints over the hunter spawn point.
            if (ArenaLayout.HUNTER_DEN.horizontalDistanceSq(x, z) < DEN_CLEARANCE_SQ) {
                continue;
            }
            IPrefabBuffer buffer = foliage[rng.nextInt(foliage.length)];
            if (buffer == null) {
                continue;
            }
            paste(world, buffer, new Anchor(x, ArenaLayout.STAND_Y, z, 0f), false);
            placed++;
        }
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] pasted " + placed + " grove foliage clump(s)");
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
