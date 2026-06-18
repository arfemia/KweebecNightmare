package com.ziggfreed.kweebec.arena;

import java.nio.file.Path;
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
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.mode.chase.Shrine;
import com.ziggfreed.kweebec.mode.chase.ShrineState;
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

    private static final String GATE_PREFAB = "KweebecNightmare/Gate";
    private static final String EXIT_PREFAB = "KweebecNightmare/Exit";
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
     * The underground-objective prefabs: a carved descent shaft + lit chamber + the cave shrine pillar.
     * Two interchangeable descent styles, alternated per cave shrine (by index) for variety - a
     * facing-independent spiral staircase and the playtest-proven ladder.
     */
    private static final String[] SHAFT_PREFABS = {
            "KweebecNightmare/Relight_Shaft",          // [0] spiral staircase
            "KweebecNightmare/Relight_Shaft_Ladder",   // [1] ladder
    };

    /** The single Moonbloom plant prefab (one harvestable glowing-mushroom block, floor-snapped per paste). */
    private static final String MOONBLOOM_PREFAB = "KweebecNightmare/Moonbloom";
    /** Seconds after build to stamp the initial Moonbloom supply (after PREP chunk-gen, so the surface probe hits). */
    private static final long MOONBLOOM_INITIAL_DELAY_SEC = 5L;
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
            pasteObjectives(world, chase);
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
                    CompletableFuture.runAsync(() -> pasteObjectives(w, chase));
                }
            }, delay, TimeUnit.SECONDS);
        }
        // Stamp the initial Moonbloom supply once the PREP chunk-gen has settled (so the surface probe
        // hits real ground, like the objective re-paste): a guaranteed cluster at each surface shrine
        // plus a seed-deterministic grove scatter. Amounts are rule-set knobs (pack/runtime tunable).
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            if (round.isResolved()) {
                return;
            }
            World w = round.world();
            if (w != null) {
                plantMoonbloom(round, w, round.ruleSet().moonbloomPerShrine(),
                        round.ruleSet().moonbloomScatter(), 0L);
            }
        }, MOONBLOOM_INITIAL_DELAY_SEC, TimeUnit.SECONDS);
    }

    /**
     * Stamp Moonbloom plants into the grove: {@code perShrine} in a ring at EACH still-unlit SURFACE
     * shrine (the guaranteed cleanse supply, withdrawn as shrines are cleansed) plus {@code scatter}
     * at seed-deterministic grove positions (the exploration supply). Reused for the initial supply and
     * each mid-match respawn wave; {@code seedSalt} varies the scatter per wave. Best-effort: the
     * blocking prefab load runs off-thread, then each plant floor-snaps + pastes on the world thread
     * (a missing prefab or unplantable column is skipped). Safe to call from any thread.
     */
    public static void plantMoonbloom(@Nonnull RoundInstance round, @Nonnull World world,
                                      int perShrine, int scatter, long seedSalt) {
        if (perShrine <= 0 && scatter <= 0) {
            return;
        }
        ChaseState chase = round.chaseState();
        CompletableFuture.runAsync(() -> {
            try {
                IPrefabBuffer buffer = load(MOONBLOOM_PREFAB);
                if (buffer == null) {
                    return; // load() already WARNs the missing key
                }
                // Cluster at each unlit surface shrine.
                if (perShrine > 0 && chase != null) {
                    for (ShrineState s : chase.shrines()) {
                        if (s.isLit()) {
                            continue;
                        }
                        Anchor a = s.anchor();
                        if (a.y() < ArenaLayout.STAND_Y - 1.0) {
                            continue; // cave shrine: no surface cluster (players carry charges down)
                        }
                        for (int i = 0; i < perShrine; i++) {
                            double theta = 2.0 * Math.PI * i / perShrine;
                            double px = a.x() + MOONBLOOM_CLUSTER_RADIUS * Math.sin(theta);
                            double pz = a.z() + MOONBLOOM_CLUSTER_RADIUS * Math.cos(theta);
                            paste(world, buffer, new Anchor(px, ArenaLayout.STAND_Y, pz, 0f), false, false);
                        }
                    }
                }
                // Scatter across the grove, deterministic off the round seed + the per-wave salt.
                if (scatter > 0) {
                    Random rng = new Random(round.worldSeed() ^ (0x4D6F6F6EL + seedSalt));
                    for (int i = 0; i < scatter; i++) {
                        double r = MOONBLOOM_SCATTER_MIN_R
                                + rng.nextDouble() * (MOONBLOOM_SCATTER_MAX_R - MOONBLOOM_SCATTER_MIN_R);
                        double theta = rng.nextDouble() * 2.0 * Math.PI;
                        double px = ArenaLayout.SPAWN.x() + r * Math.sin(theta);
                        double pz = ArenaLayout.SPAWN.z() + r * Math.cos(theta);
                        paste(world, buffer, new Anchor(px, ArenaLayout.STAND_Y, pz, 0f), false, false);
                    }
                }
                SafeLog.info("[Kweebec] planted Moonbloom (perShrine=" + perShrine + ", scatter="
                        + scatter + ", wave=" + seedSalt + ") in " + round.roundId());
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] Moonbloom planting failed: " + t.getMessage());
            }
        });
    }

    /**
     * Resolve + paste the authored objective beats (surface shrine ring, exit, and the underground
     * cave). Idempotent. NOTE: the Heartwood Gate is deliberately NOT pasted here - it is revealed by
     * {@link com.ziggfreed.kweebec.mode.chase.ChaseMode}'s {@code openGate} only once every shrine is
     * lit (the dramatic "the gate opens" beat), via {@link #pasteGate(World)}.
     */
    private static void pasteObjectives(@Nonnull World world, @Nonnull ChaseState chase) {
        try {
            IPrefabBuffer exit = load(EXIT_PREFAB);
            IPrefabBuffer[] shafts = new IPrefabBuffer[SHAFT_PREFABS.length];
            for (int i = 0; i < SHAFT_PREFABS.length; i++) {
                shafts[i] = load(SHAFT_PREFABS[i]);
            }

            int caveIndex = 0;
            for (var s : chase.shrines()) {
                Anchor a = s.anchor();
                if (a.y() >= ArenaLayout.STAND_Y - 1.0) {
                    // Surface ring shrine: the interactable furnace block (REPLACES the old pillar prefab).
                    placeShrineBlock(world, s);
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
     * Place (or re-place) the interactable shrine FURNACE block at a SURFACE shrine anchor, floor-snapped to
     * the local surface. This REPLACES the old pillar prefab - the furnace IS the shrine now (dark until a
     * survivor offers Moonbloom at it, then green fire). Records the block position on the shrine so the
     * cleanse interaction + the lit reconciler resolve it. Idempotent across the +4s/+9s re-paste (same
     * probed surface -> same cell). Best-effort: the probe + set run on the world thread.
     */
    private static void placeShrineBlock(@Nonnull World world, @Nonnull ShrineState s) {
        Anchor a = s.anchor();
        int x = (int) Math.floor(a.x());
        int z = (int) Math.floor(a.z());
        int fallbackTop = (int) Math.floor(a.y() - 1.0);
        world.execute(() -> {
            try {
                int topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop,
                        BlockTypeLists.keys(SURFACE_DECORATION_LISTS));
                setShrineFurnace(world, s, x, topY + 1, z);
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] shrine furnace placed at (" + x + "," + (topY + 1) + "," + z + ")");
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] shrine furnace placement failed at (" + x + "," + z + "): " + t.getMessage());
            }
        });
    }

    /**
     * Set the shrine furnace block at {@code (x,y,z)} (the authored UNLIT default state) and record it on the
     * shrine. Clears {@code litRendered} so the {@code ChaseMode} reconciler re-asserts the lit state if the
     * shrine is somehow already cleansed (re-pastes finish during PREP, before any cleanse can happen, so in
     * practice this just places a fresh unlit furnace). World-thread only.
     */
    private static void setShrineFurnace(@Nonnull World world, @Nonnull ShrineState s, int x, int y, int z) {
        world.setBlock(x, y, z, Shrine.SHRINE_BLOCK);
        s.setBlockPos(new Vector3i(x, y, z));
        s.setLitRendered(false);
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
                    topY = SurfaceProbe.topSolidY(world, x, z, fallbackTop,
                            BlockTypeLists.keys(SURFACE_DECORATION_LISTS));
                    cave.setCaveSurfaceTopY(topY);
                    // Re-point the chamber stand Y ONLY on the first resolve (the channel Y-band match).
                    cave.setAnchor(new Anchor(a.x(), topY - CAVE_SHAFT_DEPTH, a.z(), a.yaw()));
                }
                Vector3i pos = new Vector3i(x, topY, z);
                Store<EntityStore> store = world.getEntityStore().getStore();
                PrefabUtilPaste.paste(shaft, world, pos, store, true);
                // The cave shrine is ALSO a furnace: place it at the chamber stand level, AFTER the carve (a
                // re-carve clears the chamber, so re-adding it here each pass keeps it). The player must
                // descend the shaft to press F on it - the descend-and-return objective is preserved.
                int standY = (int) Math.floor(cave.anchor().y());
                setShrineFurnace(world, cave, x, standY, z);
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] cave shaft carved at " + pos + ", furnace at chamber stand y=" + standY);
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
