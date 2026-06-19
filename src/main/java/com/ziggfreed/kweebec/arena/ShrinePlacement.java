package com.ziggfreed.kweebec.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.FromWorldGen;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Efficiently reconciles the SURFACE shrine count for a Chase round: it detects how many shrine-host
 * prefabs the {@code KweebecNightmare_Grove} biome actually scattered (and where), then runtime-places
 * ONLY the missing ones, spaced out, so every round has at least {@link ArenaLayout#SURFACE_WORLDGEN_SHRINES}
 * reachable, well-distributed surface shrines.
 *
 * <p><b>Why this exists.</b> Surface shrines are random {@code Mesh2D} biome scatter (no count or spacing
 * guarantee). Rather than blindly over-provision, we DETECT the real placements and top up the deficit.
 *
 * <p><b>How detection stays super-efficient.</b> Each shrine-host prefab bakes ONE hidden MARKER entity
 * at the shrine (a {@link PersistentDisplayName} carrying the {@link #MARKER_SENTINEL} raw text; see the
 * {@code Shrine_*} prefab {@code entities} arrays). Worldgen auto-tags baked entities with
 * {@link FromWorldGen}, so a SINGLE {@code store.forEachChunk} query over
 * {@code Query.and(FromWorldGen, PersistentDisplayName)} - filtered to the sentinel - yields every shrine
 * position in O(matching entities), no block scan.
 *
 * <p><b>The one catch (handled here): queries see only LOADED chunks</b>, and the Chase instance unloads
 * chunks ({@code IsUnloadingChunks:true}). So we first FORCE-LOAD the play core
 * ({@link #DETECT_RADIUS}) via {@link World#getChunkAsync(long)} (generates if missing), then query on the
 * world thread once the loads settle. A timeout backstops a slow/hung load.
 *
 * <p><b>Degrade-to-deterministic safety.</b> If detection finds nothing (load slow, marker missing), the
 * deficit equals the full target and we place all of them at spaced anchors - the round is ALWAYS winnable
 * and Moonbloom always gets valid positions. Best-effort throughout: every failure path logs and continues.
 *
 * <p>Threading: the force-load is async; all entity/chunk reads and prefab pastes run on the world thread
 * (via {@link World#execute}). Mutates {@link ChaseState} (world-thread only) to publish the resolved
 * positions, then plants the initial Moonbloom supply clustered at them.
 */
public final class ShrinePlacement {

    /**
     * The {@link PersistentDisplayName} raw-text sentinel baked into each shrine-host prefab's marker
     * entity. MUST stay in sync with the {@code PersistentDisplayName.DisplayName.RawText} authored in the
     * {@code Shrine_Crypt_01/02}, {@code Shrine_Crypt_Large}, {@code Shrine_House_01/02} prefab
     * {@code entities} arrays. Distinct from the houses' patrol-marker names so the query filters those out.
     */
    static final String MARKER_SENTINEL = "kn_shrine_marker";

    /** The gameplay-core radius (blocks) we force-load + detect within. Shrines beyond it are left to worldgen. */
    private static final double DETECT_RADIUS = 70.0;
    private static final double DETECT_RADIUS_SQ = DETECT_RADIUS * DETECT_RADIUS;

    /** Minimum spacing (blocks) a topped-up shrine keeps from every other shrine (detected or topped-up). */
    private static final double MIN_SHRINE_SPACING = 18.0;
    private static final double MIN_SHRINE_SPACING_SQ = MIN_SHRINE_SPACING * MIN_SHRINE_SPACING;

    /** Conservative footprint (blocks) a topped-up shrine keeps clear of the gameplay-beat exclusion discs. */
    private static final double SHRINE_FOOTPRINT = 5.0;

    /** Backstop so a slow/hung force-load never blocks the round: detect on whatever loaded after this. */
    private static final long FORCE_LOAD_TIMEOUT_SEC = 8L;

    /** The host prefab pasted to top up a missing surface shrine (carries the baked furnace + marker). */
    private static final String TOPUP_HOST_PREFAB = "KweebecNightmare/Shrine_Crypt_01";

    private ShrinePlacement() {
    }

    /**
     * Kick off detect -> top-up -> publish -> plant-Moonbloom for a round. Safe to call from any thread:
     * force-loads the play core asynchronously, then hops to the world thread for the query + pastes.
     */
    public static void detectAndTopUp(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            forceLoadCore(world)
                    .orTimeout(FORCE_LOAD_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .whenComplete((v, ex) -> world.execute(() -> detectAndPlace(round, world)));
        } catch (Throwable t) {
            // Could not even start the force-load: still try to detect on whatever is already loaded.
            SafeLog.warn("[Kweebec] shrine force-load kickoff failed: " + t.getMessage());
            world.execute(() -> detectAndPlace(round, world));
        }
    }

    /** Generate + load every chunk overlapping the play core, returning a future that completes when all settle. */
    @Nonnull
    private static CompletableFuture<Void> forceLoadCore(@Nonnull World world) {
        int cx = (int) Math.floor(ArenaLayout.SPAWN.x());
        int cz = (int) Math.floor(ArenaLayout.SPAWN.z());
        int r = (int) Math.ceil(DETECT_RADIUS);
        int minCX = (cx - r) >> 4;
        int maxCX = (cx + r) >> 4;
        int minCZ = (cz - r) >> 4;
        int maxCZ = (cz + r) >> 4;
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int chX = minCX; chX <= maxCX; chX++) {
            for (int chZ = minCZ; chZ <= maxCZ; chZ++) {
                futures.add(world.getChunkAsync(ChunkUtil.indexChunk(chX, chZ)));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /** World-thread: query the baked shrine markers, top up the deficit, publish positions, plant Moonbloom. */
    private static void detectAndPlace(@Nonnull RoundInstance round, @Nonnull World world) {
        if (round.isResolved()) {
            return;
        }
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        try {
            List<Vector3i> resolved = new ArrayList<>(detectMarkers(world));
            int target = ArenaLayout.SURFACE_WORLDGEN_SHRINES;
            int deficit = Math.max(0, target - resolved.size());
            if (deficit > 0) {
                IPrefabBuffer host = ArenaBuilder.load(TOPUP_HOST_PREFAB);
                List<Anchor> topUp = planTopUp(resolved, deficit, round.worldSeed());
                for (Anchor a : topUp) {
                    if (host != null) {
                        ArenaBuilder.paste(world, host, a, true, false); // floor-snapped, additive (like structures)
                    }
                    resolved.add(new Vector3i((int) Math.floor(a.x()),
                            (int) Math.floor(ArenaLayout.STAND_Y), (int) Math.floor(a.z())));
                }
            }
            chase.setSurfaceShrinePositions(resolved);
            SafeLog.info("[Kweebec] surface shrines: detected=" + (resolved.size() - deficit)
                    + " target=" + target + " toppedUp=" + deficit + " round=" + round.roundId());

            // Positions are known: plant the initial Moonbloom supply clustered at the REAL shrines.
            ArenaBuilder.plantMoonbloom(round, world, round.ruleSet().moonbloomPerShrine(),
                    round.ruleSet().moonbloomScatter(), 0L);
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] shrine detect/top-up failed: " + t.getMessage());
        }
    }

    /** One ECS query for the baked shrine markers within the play core; returns their world positions. */
    @Nonnull
    private static List<Vector3i> detectMarkers(@Nonnull World world) {
        List<Vector3i> found = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();
        double cx = ArenaLayout.SPAWN.x();
        double cz = ArenaLayout.SPAWN.z();
        Query<EntityStore> query = Query.and(
                FromWorldGen.getComponentType(), PersistentDisplayName.getComponentType());
        store.forEachChunk(query, (chunk, buffer) -> {
            int n = chunk.size();
            for (int i = 0; i < n; i++) {
                PersistentDisplayName name = chunk.getComponent(i, PersistentDisplayName.getComponentType());
                if (name == null) {
                    continue;
                }
                Message msg = name.getDisplayName();
                if (msg == null || !MARKER_SENTINEL.equals(msg.getRawText())) {
                    continue;
                }
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }
                Vector3d p = transform.getPosition();
                double dx = p.x() - cx;
                double dz = p.z() - cz;
                if (dx * dx + dz * dz > DETECT_RADIUS_SQ) {
                    continue; // a shrine scattered outside the core; left to worldgen, not counted here
                }
                found.add(new Vector3i((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z())));
            }
        });
        return found;
    }

    /**
     * Pick {@code count} spaced anchors for the missing shrines from the seeded ring candidates, keeping
     * clear of the gameplay beats ({@link ExclusionMask#shrinePlacementMask()}) and a minimum distance
     * from every already-present shrine and each other. Pure (engine-free) math, so it is unit-testable.
     * Relaxes the spacing requirement if heavy clustering would otherwise leave us short - reaching the
     * target count (winnability) beats perfect spacing.
     */
    @Nonnull
    static List<Anchor> planTopUp(@Nonnull List<Vector3i> present, int count, long seed) {
        List<Anchor> candidates = ArenaLayout.shrineAnchors(Math.max(8, count * 3), seed);
        ExclusionMask mask = ExclusionMask.shrinePlacementMask();
        List<double[]> avoid = new ArrayList<>();
        for (Vector3i p : present) {
            avoid.add(new double[]{p.x() + 0.5, p.z() + 0.5});
        }
        List<Anchor> chosen = new ArrayList<>(count);
        // Strict pass: clears the keep-clear mask AND holds min spacing from present + already-chosen.
        for (Anchor cand : candidates) {
            if (chosen.size() >= count) {
                break;
            }
            if (mask.isClear(cand.x(), cand.z(), SHRINE_FOOTPRINT)
                    && farFromAll(cand, avoid, MIN_SHRINE_SPACING_SQ)) {
                chosen.add(cand);
                avoid.add(new double[]{cand.x(), cand.z()});
            }
        }
        // Relaxation pass: if still short, fill from any mask-clear candidate (drop the spacing rule).
        for (Anchor cand : candidates) {
            if (chosen.size() >= count) {
                break;
            }
            if (!chosen.contains(cand) && mask.isClear(cand.x(), cand.z(), SHRINE_FOOTPRINT)) {
                chosen.add(cand);
            }
        }
        return chosen;
    }

    /** True iff {@code a} is at least {@code minSq} (squared distance) from every point in {@code avoid}. */
    private static boolean farFromAll(@Nonnull Anchor a, @Nonnull List<double[]> avoid, double minSq) {
        for (double[] p : avoid) {
            double dx = a.x() - p[0];
            double dz = a.z() - p[1];
            if (dx * dx + dz * dz < minSq) {
                return false;
            }
        }
        return true;
    }
}
