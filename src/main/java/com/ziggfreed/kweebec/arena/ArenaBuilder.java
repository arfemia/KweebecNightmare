package com.ziggfreed.kweebec.arena;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import com.ziggfreed.common.world.BlockTypeLists;
import com.ziggfreed.common.world.SurfaceProbe;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.asset.GroveThrowableAsset;
import com.ziggfreed.kweebec.asset.GroveThrowableConfig;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

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

    /**
     * The co-op extraction platform (the reworked exit) the survivor group holds together to escape: a vanilla
     * Frost Elemental Circle medallion copied into the pack (see {@code tools/build_extraction_pad.py}), an
     * 11x11 standable basalt-and-blue-crystal ring with a raised center, anchored so its walkable plane
     * floor-snaps flush to the surface at {@link ArenaLayout#ESCAPE}. Replaces the old 3x3 orange-light pad.
     */
    private static final String EXIT_PREFAB = "KweebecNightmare/Extraction_Pad";
    /**
     * The corruption-repainted Kweebec well pasted as the grove's village-ruin CENTERPIECE (the hybrid
     * "ruined village in a dead forest" identity). Offset SOUTH of spawn (+z) so it never traps a
     * spawning player and stays clear of the gate corridor (-z). Cycle-3 swaps the old healthy-green
     * {@code Npc/Kweebec/Oak/Well} for the committed {@code KweebecNightmare/Corrupted_Well} (the
     * build-time block-swap repaint) so the centerpiece reads as blight. Pasted ONCE (not in the
     * objective re-paste loop) so it never stacks on its own pasted top the way a re-probed cave shaft
     * would.
     */
    private static final String CENTERPIECE_PREFAB = "KweebecNightmare/Corrupted_Well";
    private static final double CENTERPIECE_Z_OFFSET = 10.0;
    /**
     * The underground cave-shrine descent prefab(s): a carved shaft + lit chamber + the baked cave shrine
     * furnace deep at the chamber, force-carved into the solid grove by {@link #pasteCaveShaft} (which
     * ignores the anchor Y and surface-snaps the prefab top, so a deeper descent just carves deeper).
     * Currently the facing-independent spiral staircase; indexed by cave so more styles can be alternated.
     *
     * <p>The multi-level underground DUNGEON ({@code Shrine_Dungeon_Underground_01}) that briefly lived here
     * was MOVED to the WORLDGEN biome scatter: a {@code Type:Prefab} prop (KweebecNightmare_Grove,
     * {@code KN_ShrineDungeon}) writes its {@code Empty} descent cells at generation time, carving the shaft
     * straight out of the rock with no runtime force-paste and no worldgen race. It is no longer cave-carved
     * here.
     */
    private static final String[] SHAFT_PREFABS = {
            "KweebecNightmare/Relight_Shaft",  // spiral staircase (the runtime force-carved cave style)
    };

    /** The single Moonbloom plant prefab (one harvestable glowing-mushroom block, floor-snapped per paste). */
    private static final String MOONBLOOM_PREFAB = "KweebecNightmare/Moonbloom";
    /** Cluster ring radius (blocks) Moonbloom plants ring a surface shrine at. */
    private static final double MOONBLOOM_CLUSTER_RADIUS = 2.5;
    /** Min / max scatter radius (blocks) from spawn for grove-scattered Moonbloom (inside the r112 edge cliff, clear of the r6 spawn courtyard). */
    private static final double MOONBLOOM_SCATTER_MIN_R = 12.0;
    private static final double MOONBLOOM_SCATTER_MAX_R = 90.0;

    /**
     * Engine {@code BlockTypeList} ids whose blocks are SURFACE DECORATION the worldgen scatters ON TOP of
     * the terrain (the grove's dead trees - trunks/branches/leaves - plus ground scatter: grass, bushes,
     * sticks, mushrooms), NOT the real ground. The {@link SurfaceProbe} skips them so every runtime paste
     * floor-snaps to the genuine surface UNDER the canopy instead of anchoring on a tree trunk or a leaf
     * block (the cave shaft / spiral / ladder were landing on foliage). Worldgen's own height snap never
     * hit this because it runs against the terrain buffer BEFORE the tree/prop phase; the runtime probe
     * runs after, so it must scan past the decoration. Asset-driven: the lists are vanilla data resolved
     * via {@link BlockTypeLists#keys(String...)}, so new tree/scatter blocks are skipped automatically.
     */
    private static final String[] SURFACE_DECORATION_LISTS = {"TreeWoodAndLeaves", "AllScatter"};

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
        // Immediate pass: the gameplay objective beats + the village-ruin centerpiece + the seeded
        // corrupted-structure ruins (all pasted once). Ambient grove trees are scattered by the
        // worldgen biome (KweebecNightmare_Grove Props[]); these are the GAMEPLAY-anchored ruins.
        CompletableFuture.runAsync(() -> {
            pasteObjectives(round, world);
            pasteCenterpiece(world);
            pasteStructures(round, world);
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
                    CompletableFuture.runAsync(() -> pasteObjectives(round, w));
                }
            }, delay, TimeUnit.SECONDS);
        }
        // Detect the WORLDGEN-scattered surface shrines, top up ONLY the missing ones (spaced),
        // publish their real positions onto ChaseState, and THEN plant the initial Moonbloom supply
        // clustered at those real shrines. Self-sequences off a force-load of the play core (so it runs
        // after chunk-gen has placed the shrine hosts); see ShrinePlacement. Best-effort and
        // degrade-to-deterministic: if detection finds nothing it places the full target at spaced
        // anchors, so the round is always winnable and Moonbloom always gets valid positions.
        ShrinePlacement.detectAndTopUp(round, world);
    }

    /**
     * Stamp Moonbloom plants into the grove (the canonical cluster planter): a thin delegate over the
     * generalized {@link #plantClusters} with the Moonbloom prefab. {@code perShrine} rings EACH surface
     * shrine (the guaranteed cleanse supply); {@code scatter} seeds grove positions (the exploration
     * supply); reused for the initial supply and each mid-match respawn wave.
     */
    public static void plantMoonbloom(@Nonnull RoundInstance round, @Nonnull World world,
                                      int perShrine, int scatter, long seedSalt) {
        plantClusters(round, world, MOONBLOOM_PREFAB, perShrine, scatter, seedSalt);
    }

    /**
     * GENERALIZED cluster planter (the Moonbloom planter, parameterized on the plant-cluster prefab): paste
     * {@code perShrine} of {@code prefabKey} in a ring at EACH surface shrine anchor plus {@code scatter} at
     * seed-deterministic grove positions. ONE core so Moonbloom, the data-driven grove throwables, and (via
     * {@link #plantClusterRing}) the boss-phase Emberbloom all share it. Best-effort: the blocking prefab
     * load runs off-thread, then each plant floor-snaps + pastes on the world thread (a missing prefab or
     * unplantable column is skipped). Safe to call from any thread; {@code seedSalt} varies the scatter per
     * wave (and per prefab, so two throwables do not stack on the same scattered tile).
     */
    public static void plantClusters(@Nonnull RoundInstance round, @Nonnull World world,
                                     @Nonnull String prefabKey, int perShrine, int scatter, long seedSalt) {
        if (perShrine <= 0 && scatter <= 0) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                IPrefabBuffer buffer = load(prefabKey);
                if (buffer == null) {
                    return; // load() already WARNs the missing key
                }
                // Cluster the guaranteed supply at the REAL surface shrines resolved by ShrinePlacement
                // (worldgen-detected hosts + any runtime top-up). Falls back to the authored
                // WORLDGEN_SHRINE_XZ guesses only if detection has not published positions yet.
                if (perShrine > 0) {
                    ChaseState chase = round.chaseState();
                    List<double[]> centers = new ArrayList<>();
                    if (chase != null && !chase.surfaceShrinePositions().isEmpty()) {
                        for (Vector3i s : chase.surfaceShrinePositions()) {
                            centers.add(new double[]{s.x() + 0.5, s.z() + 0.5});
                        }
                    } else {
                        for (double[] xz : ArenaLayout.WORLDGEN_SHRINE_XZ) {
                            centers.add(xz);
                        }
                    }
                    for (double[] c : centers) {
                        for (int i = 0; i < perShrine; i++) {
                            double theta = 2.0 * Math.PI * i / perShrine;
                            double px = c[0] + MOONBLOOM_CLUSTER_RADIUS * Math.sin(theta);
                            double pz = c[1] + MOONBLOOM_CLUSTER_RADIUS * Math.cos(theta);
                            paste(world, buffer, new Anchor(px, ArenaLayout.STAND_Y, pz, 0f), false, false);
                        }
                    }
                }
                // Scatter across the grove, deterministic off the round seed + the per-wave/per-prefab salt.
                if (scatter > 0) {
                    Random rng = new Random(round.worldSeed() ^ (0x4D6F6F6EL + seedSalt + prefabKey.hashCode()));
                    for (int i = 0; i < scatter; i++) {
                        double r = MOONBLOOM_SCATTER_MIN_R
                                + rng.nextDouble() * (MOONBLOOM_SCATTER_MAX_R - MOONBLOOM_SCATTER_MIN_R);
                        double theta = rng.nextDouble() * 2.0 * Math.PI;
                        double px = ArenaLayout.SPAWN.x() + r * Math.sin(theta);
                        double pz = ArenaLayout.SPAWN.z() + r * Math.cos(theta);
                        paste(world, buffer, new Anchor(px, ArenaLayout.STAND_Y, pz, 0f), false, false);
                    }
                }
                SafeLog.info("[Kweebec] planted clusters (prefab=" + prefabKey + ", perShrine=" + perShrine
                        + ", scatter=" + scatter + ", wave=" + seedSalt + ") in " + round.roundId());
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] cluster planting failed (" + prefabKey + "): " + t.getMessage());
            }
        });
    }

    /**
     * Paste {@code count} of {@code prefabKey} in a ring of {@code radius} blocks around {@code (cx, cz)},
     * each floor-snapped to the local surface - the boss-phase Emberbloom cluster placer ({@code BossController}
     * rings these around the Warden, mirroring its {@code summonAdds} ring math). Best-effort + thread-safe
     * like {@link #plantClusters} (blocking load off-thread, each paste hops to the world thread).
     */
    public static void plantClusterRing(@Nonnull World world, @Nonnull String prefabKey,
                                        double cx, double cz, double radius, int count) {
        if (count <= 0) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                IPrefabBuffer buffer = load(prefabKey);
                if (buffer == null) {
                    return;
                }
                for (int i = 0; i < count; i++) {
                    double theta = 2.0 * Math.PI * i / count;
                    double px = cx + radius * Math.sin(theta);
                    double pz = cz + radius * Math.cos(theta);
                    paste(world, buffer, new Anchor(px, ArenaLayout.STAND_Y, pz, 0f), false, false);
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] cluster-ring planting failed (" + prefabKey + "): " + t.getMessage());
            }
        });
    }

    /**
     * Distribute every ENABLED + placeable {@link GroveThrowableConfig} entry (the data-driven utility
     * glow-throwables - Gust/Mire + any pack-authored variant), each gated by its {@code MinCorruptionTier}
     * against the round's current corruption tier. Called for the initial supply (from {@code ShrinePlacement}
     * once shrine positions resolve, {@code wavesOnly=false}) and each Moonbloom respawn wave (from
     * {@code ChaseMode}, {@code wavesOnly=true} so only {@code RespawnWithWaves} entries regrow). A no-op when
     * nothing is enabled (the shipped default), so the gather loop ships dormant. World-thread caller OK.
     */
    public static void plantGroveThrowables(@Nonnull RoundInstance round, @Nonnull World world,
                                            long seedSalt, boolean wavesOnly) {
        ChaseState chase = round.chaseState();
        int tier = chase != null ? chase.corruptionTier() : 0;
        for (GroveThrowableAsset gt : GroveThrowableConfig.getInstance().placeable()) {
            if (wavesOnly && !gt.respawnWithWaves()) {
                continue;
            }
            if (tier < gt.minCorruptionTier()) {
                continue;
            }
            String prefabKey = gt.prefabKey();
            if (prefabKey == null || prefabKey.isBlank()) {
                continue;
            }
            plantClusters(round, world, prefabKey, gt.perShrineCount(), gt.scatterCount(), seedSalt);
        }
    }

    /**
     * Resolve + paste the authored objective beats (the extraction platform + the underground cave). Idempotent.
     * The extraction platform ({@code Extraction_Pad}, with its no-op purple void-portal) is the escape goal,
     * pasted here at {@link ArenaLayout#ESCAPE}. There is no separate Heartwood Gate prefab any more (the old
     * light archway was removed); {@code ChaseMode.openGate} fires the gate-open beat (titles / exit marker /
     * hunter alert) logically without a pasted arch.
     */
    private static void pasteObjectives(@Nonnull RoundInstance round, @Nonnull World world) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        try {
            IPrefabBuffer exit = load(EXIT_PREFAB);
            IPrefabBuffer[] shafts = new IPrefabBuffer[SHAFT_PREFABS.length];
            for (int i = 0; i < SHAFT_PREFABS.length; i++) {
                shafts[i] = load(SHAFT_PREFABS[i]);
            }

            // SURFACE shrines are WORLDGEN-placed (the furnace baked into copied vanilla host prefabs by the
            // KweebecNightmare_Grove biome shrine-host List props) and discovered via their interaction -
            // ArenaBuilder no longer rings them. ArenaBuilder still CARVES the cave shrines (force-paste into
            // solid terrain so the descent is reachable); their furnace is BAKED into the shaft prefab too
            // (no runtime block placement, no cyan beam). Both kinds are discovered by ChaseState.shrineForBlock
            // when a survivor offers Moonbloom at the furnace, so neither needs a pre-tracked position here.
            List<Anchor> caves = ArenaLayout.caveShrineAnchors(round.ruleSet().caveShrineCount(), round.worldSeed());
            for (int caveIndex = 0; caveIndex < caves.size(); caveIndex++) {
                IPrefabBuffer shaft = shafts[caveIndex % shafts.length];
                if (shaft != null) {
                    pasteCaveShaft(world, shaft, caves.get(caveIndex), caveIndex, chase);
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

    /**
     * Paste the seeded corrupted-structure ruins ONCE (never re-pasted, like the centerpiece - these
     * are decorative cover / chokepoints / beacons / landmarks, never a logic dependency). Best-effort:
     * a missing prefab or a worldgen-race clobber only costs the cosmetic; the round plays regardless.
     *
     * <p>Deterministic per round: uses the per-round world seed ({@code round.worldSeed()}, the same
     * coherent source as the terrain and the seeded shrine layout), asks {@link StructureCatalog} for the
     * seed-shuffled subset whose footprint clears the shared {@link ExclusionMask}, and floor-snaps each via the existing
     * {@code paste(...)} surface path ({@code force=false}, like the centerpiece). Each structure's
     * FACING is varied by the same seed (one of the four cardinal {@link Rotation}s) so the chosen set
     * AND orientation differ per round.
     */
    private static void pasteStructures(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            // The per-round world seed: the SAME coherent source as the terrain and the seeded shrine
            // layout (ChaseState), so the structure subset + facing match this round's terrain. Set by
            // RoundService.onInstanceReady from the instance world's getWorldConfig().getSeed().
            long seed = round.worldSeed();
            ExclusionMask mask = ExclusionMask.defaultMask();
            List<StructureCatalog.Placement> placements = StructureCatalog.select(seed, mask);
            if (placements.isEmpty()) {
                SafeLog.info("[Kweebec] no corrupted structures selected for round " + round.roundId());
                return;
            }
            // A second RNG off the same seed drives the per-placement facing, independent of the
            // selection shuffle so adding/removing candidates does not rotate the kept ones.
            Random facingRng = new Random(seed * 0x9E3779B97F4A7C15L);
            for (StructureCatalog.Placement p : placements) {
                IPrefabBuffer buffer = load(p.prefabKey());
                if (buffer == null) {
                    // load() already WARNs the missing key; skip best-effort.
                    continue;
                }
                Rotation yaw = Rotation.NORMAL[facingRng.nextInt(Rotation.NORMAL.length)];
                SafeLog.info("[Kweebec] placing corrupted structure '" + p.prefabKey() + "' ("
                        + p.role() + ") at (" + p.x() + "," + p.z() + ") yaw=" + yaw);
                // Surface-decoration paste: floor-snapped, force=false (only adds blocks), facing varied.
                Anchor at = new Anchor(p.x(), ArenaLayout.STAND_Y, p.z(), 0f);
                paste(world, buffer, at, true, false, yaw);
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] corrupted structure placement failed: " + t.getMessage());
        }
    }

    @Nullable
    static IPrefabBuffer load(@Nonnull String key) {
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
    static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at,
                      boolean verbose, boolean force) {
        paste(world, buffer, at, verbose, force, Rotation.None);
    }

    /**
     * Paste an authored beat (shrine / exit / gate / structure) at an anchor, FLOOR-SNAPPED to the real
     * generated surface, rotated by {@code rotation} (one of the four cardinal {@link Rotation}s).
     * {@code verbose} INFO-logs the placement; a FAILURE always WARNs.
     *
     * <p>Placement: prefab buffer coords are stored ANCHOR-RELATIVE (the authored anchor sits at stored
     * (0,0,0)), and {@code PrefabUtil.paste} places stored (0,0,0) exactly at {@code position}. The
     * grove is now natural rolling terrain (the flat play disc is gone), so a hardcoded Y would float
     * or bury every beat; instead we probe the LOCAL top-solid Y with {@link SurfaceProbe} on the world
     * thread (after PREP chunk-gen, so the column is queryable) and land the anchor there. If the probe
     * misses (unloaded chunk) it degrades to the anchor's authored floor block ({@code at.y - 1}).
     */
    private static void paste(@Nonnull World world, @Nonnull IPrefabBuffer buffer, @Nonnull Anchor at,
                              boolean verbose, boolean force, @Nonnull Rotation rotation) {
        int x = (int) Math.floor(at.x());
        int z = (int) Math.floor(at.z());
        int fallbackTop = (int) Math.floor(at.y() - 1.0);
        world.execute(() -> {
            try {
                int topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop,
                        BlockTypeLists.keys(SURFACE_DECORATION_LISTS));
                Vector3i pos = new Vector3i(x, topY, z);
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(buffer, world, pos, store, force, rotation);
                if (verbose) {
                    KweebecNightmarePlugin.LOGGER.atInfo().log(
                            "[Kweebec] prefab pasted at " + pos + " (surface-snapped, rot=" + rotation + ")");
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] prefab paste failed at (" + x + "," + z + "): " + t.getMessage());
            }
        });
    }

    /**
     * Carve one underground shrine's descent shaft + chamber into the solid grove, floor-snapped to the
     * rolling surface: probe the LOCAL top-solid Y and force-paste the shaft top there (so the entrance meets
     * the surface). The shrine FURNACE is BAKED into the shaft prefab at the chamber (no runtime block
     * placement, no cyan beam) and discovered via its interaction when a survivor descends and offers
     * Moonbloom. Idempotent across the +4s/+9s re-carve: the resolved surface Y is remembered per cave
     * ({@code ChaseState.caveCarveY}) so a re-paste reuses it instead of re-probing the already-carved
     * surface (which would stack a second shaft). On a probe miss it falls back to the flat-disc floor.
     */
    private static void pasteCaveShaft(@Nonnull World world, @Nonnull IPrefabBuffer shaft,
                                       @Nonnull Anchor a, int caveIndex, @Nonnull ChaseState chase) {
        int x = (int) Math.floor(a.x());
        int z = (int) Math.floor(a.z());
        int fallbackTop = (int) Math.floor(ArenaLayout.FLOOR_Y);
        world.execute(() -> {
            try {
                Integer stored = chase.caveCarveY(caveIndex);
                int topY;
                if (stored == null) {
                    topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop,
                            BlockTypeLists.keys(SURFACE_DECORATION_LISTS));
                    chase.setCaveCarveY(caveIndex, topY);
                } else {
                    topY = stored;
                }
                Vector3i pos = new Vector3i(x, topY, z);
                Store<EntityStore> store = world.getEntityStore().getStore();
                // force=true so the shaft's Empty cells UNCONDITIONALLY carve the rock (PrefabUtil setBlock
                // path) - the descend-and-return shrine MUST be reachable or allShrinesLit() never fires.
                PrefabUtilPaste.paste(shaft, world, pos, store, true);
                KweebecNightmarePlugin.LOGGER.atInfo().log("[Kweebec] cave shaft carved at " + pos);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] cave shaft carve failed at (" + x + "," + z + "): " + t.getMessage());
            }
        });
    }

    /** Tiny indirection so the verified paste signature lives in one place. */
    private static final class PrefabUtilPaste {
        private static final Random RNG = new Random(0xC0C0L);

        /** Unrotated paste (the cave-shaft / facing-independent path). */
        static void paste(@Nonnull IPrefabBuffer buffer, @Nonnull World world,
                          @Nonnull Vector3i pos, @Nonnull Store<EntityStore> store, boolean force) {
            paste(buffer, world, pos, store, force, Rotation.None);
        }

        /**
         * @param force    {@code true} routes the paste through {@code chunk.setBlock} (UNCONDITIONAL -
         *                 writes every cell incl. {@code Empty}, so the prefab can CARVE solid terrain);
         *                 {@code false} uses {@code placeBlock} (respects placement rules, for surface
         *                 decoration that only adds blocks).
         * @param rotation the cardinal yaw the prefab is rotated by before pasting.
         */
        static void paste(@Nonnull IPrefabBuffer buffer, @Nonnull World world,
                          @Nonnull Vector3i pos, @Nonnull Store<EntityStore> store, boolean force,
                          @Nonnull Rotation rotation) {
            com.hypixel.hytale.server.core.util.PrefabUtil.paste(
                    buffer, world, pos, rotation, force, RNG, store);
        }
    }
}
