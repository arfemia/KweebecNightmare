package com.ziggfreed.kweebec.boss;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.encounter.run.EncounterLifecycle;
import com.ziggfreed.common.encounter.run.EncounterRun;
import com.ziggfreed.common.encounter.run.EncounterRuntime;
import com.ziggfreed.common.encounter.run.EncounterSpawner;
import com.ziggfreed.common.encounter.run.EncounterSubjects;
import com.ziggfreed.common.encounter.run.SpawnOptions;
import com.ziggfreed.common.world.SpawnPlacement;
import com.ziggfreed.common.worldmap.WorldMapMarkers;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.moonbloom.GlowThrowables;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * A round's handle on its Warden fight. The fight itself is the native encounter script
 * {@code Server/EncounterManager/KweebecNightmare_Warden_Encounter.json}, which the engine runs (the phases, the
 * thresholds, the in-place role changes, the adds, the boss bar); the credit, the party health scale and
 * the notices are ziggfreed-common's, off the binding row {@code Server/ZiggfreedCommon/Encounters/
 * KweebecNightmare_Warden_Encounter.json}. What the round keeps for itself is here: {@link #raise standing the
 * encounter up} at the Heartwood Gate with the round's own options, the world-map marker that follows the
 * Warden under the preset's {@code bossMarker} knob, and the Emberbloom supply rings the survivors kill it
 * with, placed as the fight moves through its phases. The round hears the fight through
 * {@link BossEncounterListener}, which routes each framework event to the handle by the run id the
 * framework stamped on the encounter before it was added.
 *
 * <p>World-thread only once created; {@link #raise} completes on the world thread as well.
 */
public final class BossEncounter {

    /** World-map POI id for the Warden marker (mod-prefixed; avoids the engine's reserved POI keys). */
    private static final String MARKER_ID = "kweebec_boss";
    private static final String MARKER_ICON = "Home.png";
    /** Throttle (ms) between marker re-placements so it tracks the moving Warden without a per-tick write. */
    private static final long MARKER_FOLLOW_MS = 3000L;

    /** Emberbloom clusters ringed around the Warden as each phase begins, by phase (the first is the rise). */
    private static final int[] EMBERBLOOM_PER_PHASE = {8, 10, 12};
    /** Radius (blocks) of the close Emberbloom ring around the Warden. */
    private static final double EMBERBLOOM_RING_RADIUS = 10.0;
    /**
     * Per-wave ring rotation (radians): the golden angle, so each successive ring around a Warden that has
     * not moved lands on fresh tiles instead of stacking exactly atop the previous ring.
     */
    private static final double RING_WAVE_ROTATION = 2.0 * Math.PI * 0.6180339887498949;

    private final Ref<EntityStore> encounterRef;
    private final UUID runId;
    private final boolean marker;
    /** Whether the marker has been placed this fight (so a follow never places one the preset turned off). */
    private boolean markerPlaced;
    private long markerMovedAtMs;
    /** Monotonic count of Emberbloom placements, the ring rotation and the scatter salt. */
    private int emberbloomWave;

    private BossEncounter(@Nonnull Ref<EntityStore> encounterRef, @Nonnull UUID runId, boolean marker) {
        this.encounterRef = encounterRef;
        this.runId = runId;
        this.marker = marker;
    }

    // --- standing the fight up ---

    /**
     * Stand the round's Warden encounter up at the Heartwood Gate, once its chunk is loaded and ticking
     * (the gate is far from where the party stands when the last shrine lights, and an entity added into a
     * chunk that is not ticking is unloaded on the spot). Completes on the world thread with {@code true}
     * when the encounter is up and the round holds its handle, {@code false} when the preset names no
     * encounter, the framework refused the spawn, or the round ended first (in which case the encounter is
     * taken straight back down). The caller decides what to do with a {@code false}; this never opens the
     * gate itself.
     */
    @Nonnull
    public static CompletableFuture<Boolean> raise(@Nonnull RoundInstance round, @Nonnull World world) {
        RuleSet rules = round.ruleSet();
        String assetId = rules.bossId();
        if (assetId == null || assetId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        Vector3d at = gatePosition(world);
        TransformComponent transform = new TransformComponent(at, new Rotation3f(0f, ArenaLayout.GATE.yaw(), 0f));
        return EncounterSpawner.spawnWhenLoaded(world, assetId, transform, optionsFor(round))
                .thenApplyAsync(outcome -> adopt(round, world, outcome, rules.bossMarker()), world);
    }

    /**
     * What the round asks the framework for: the round id as the run's owner, the preset id as its
     * difficulty (so a quest step can name the Warden on one difficulty), the preset's boss health
     * multiplier as the run multiplier the binding row's party scale composes with, and every survivor
     * still in the round as a seeded member, so the whole party gets the bar and the credit wherever
     * each of them stands.
     */
    @Nonnull
    static SpawnOptions optionsFor(@Nonnull RoundInstance round) {
        RuleSet rules = round.ruleSet();
        return SpawnOptions.forRound(round.roundId(), rules.presetId(), rules.bossHealthMultiplier(),
                round.presentPlayerIds());
    }

    private static boolean adopt(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull EncounterSpawner.Outcome outcome, boolean marker) {
        Ref<EntityStore> ref = outcome.ref();
        if (ref == null) {
            SafeLog.warn("[Kweebec][boss] the Warden encounter did not rise for round " + round.roundId()
                    + " (" + outcome.refusal() + ")");
            return false;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (round.isResolved()) {
            EncounterSpawner.despawn(store, ref, "the round ended before the Warden rose");
            return false;
        }
        EncounterRun run = EncounterRuntime.runOf(store, ref);
        if (run == null) {
            SafeLog.warn("[Kweebec][boss] the Warden encounter carries no run in round " + round.roundId());
            EncounterSpawner.despawn(store, ref, "no run on the Warden encounter");
            return false;
        }
        round.setBossEncounter(new BossEncounter(ref, run.runId(), marker));
        SafeLog.info("[Kweebec][boss] Warden encounter up for round " + round.roundId()
                + " (run " + run.shortId() + ")");
        return true;
    }

    /**
     * The Warden's rise point: the Heartwood Gate anchor, floor-snapped past the grove canopy so the
     * encounter (and the Warden the marker beside it raises) stands on genuine ground.
     */
    @Nonnull
    private static Vector3d gatePosition(@Nonnull World world) {
        Anchor gate = ArenaLayout.GATE;
        return SpawnPlacement.snapToSurface(world, gate.x(), gate.z(), (int) ArenaLayout.STAND_Y,
                ArenaBuilder.surfaceDecorationKeys());
    }

    // --- the handle ---

    /** The run id the framework stamped on the encounter; every encounter event carries it. */
    @Nonnull
    public UUID runId() {
        return runId;
    }

    /** The encounter entity, valid until the round tears it down. */
    @Nonnull
    public Ref<EntityStore> encounterRef() {
        return encounterRef;
    }

    // --- the round's reactions ---

    /** The Warden is up and vulnerable: drop the marker on it and ring the first Emberbloom supply. */
    public void onEngaged(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (marker) {
            // Compass updating is the world-map render precondition (the exit marker may be off this
            // preset); enable it so the Warden POI shows.
            world.setCompassUpdating(true);
            placeMarker(world, store, System.currentTimeMillis());
        }
        supplyEmberbloom(round, world, store, EMBERBLOOM_PER_PHASE[0]);
    }

    /**
     * The fight moved into its next phase ({@code phaseIndex} counts the phase beats so far, so the first
     * phase change reads 1): ring that phase's Emberbloom supply around wherever the Warden stands now.
     */
    public void onPhase(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store,
                        int phaseIndex) {
        int index = Math.max(0, Math.min(phaseIndex, EMBERBLOOM_PER_PHASE.length - 1));
        supplyEmberbloom(round, world, store, EMBERBLOOM_PER_PHASE[index]);
    }

    /**
     * Keep the marker on the moving Warden, throttled to {@link #MARKER_FOLLOW_MS}. A no-op until the
     * fight has engaged, and always when the preset turned the marker off.
     */
    public void followMarker(@Nonnull World world, @Nonnull Store<EntityStore> store, long nowMs) {
        if (!marker || !markerPlaced || nowMs - markerMovedAtMs < MARKER_FOLLOW_MS) {
            return;
        }
        placeMarker(world, store, nowMs);
    }

    /** The fight is over, won or torn down: take the marker down. Safe when it was never placed. */
    public void onEnded(@Nonnull World world) {
        if (markerPlaced) {
            WorldMapMarkers.remove(world, MARKER_ID);
            markerPlaced = false;
        }
    }

    /**
     * Round teardown: remove the encounter entity (the script's {@code CleanupOnRemove} takes the Warden
     * and its adds with it, and the framework settles a fight still open as a wipe first) and the marker.
     */
    public void dismiss(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (encounterRef.isValid()) {
            EncounterSpawner.despawn(store, encounterRef, "round teardown");
        }
        onEnded(world);
    }

    // --- policies ---

    private void placeMarker(@Nonnull World world, @Nonnull Store<EntityStore> store, long nowMs) {
        Vector3d p = bossPosition(store);
        if (WorldMapMarkers.place(world, MARKER_ID, p.x(), p.y(), p.z(), MARKER_ICON, Lang.msg(Lang.NPC_WARDEN))) {
            markerPlaced = true;
            markerMovedAtMs = nowMs;
        }
    }

    /**
     * The phase-entry Emberbloom placement: a {@code count} close ring at the Warden (the ammo right where
     * the fight is) plus a smaller one-time scatter across the wider grove, so survivors are not pinned to
     * the Warden's feet yet the arena is not carpeted. Each ring is rotated by the golden angle per wave so
     * it never stacks on the previous one. Best-effort through {@link ArenaBuilder} (a blocking prefab load
     * off-thread, each paste hopping back onto the world thread).
     */
    private void supplyEmberbloom(@Nonnull RoundInstance round, @Nonnull World world,
                                  @Nonnull Store<EntityStore> store, int count) {
        if (count <= 0) {
            return;
        }
        Vector3d center = bossPosition(store);
        int wave = ++emberbloomWave;
        ArenaBuilder.plantClusterRing(world, GlowThrowables.EMBER_PREFAB, center.x(), center.z(),
                EMBERBLOOM_RING_RADIUS, count, RING_WAVE_ROTATION * wave);
        ArenaBuilder.plantClusters(round, world, GlowThrowables.EMBER_PREFAB, 0, Math.max(1, count / 2), wave);
    }

    /**
     * Where the Warden stands: the subject bound in the script's {@code Boss} slot, else the encounter
     * entity itself (the gate), else the gate anchor. Re-resolved on every read because an in-place role
     * change reissues the Warden's reference.
     */
    @Nonnull
    private Vector3d bossPosition(@Nonnull Store<EntityStore> store) {
        Vector3d fallback = new Vector3d(ArenaLayout.GATE.x(), ArenaLayout.STAND_Y, ArenaLayout.GATE.z());
        if (!encounterRef.isValid()) {
            return fallback;
        }
        try {
            Ref<EntityStore> subject = EncounterSubjects.resolve(store, encounterRef, null, true);
            TransformComponent at = EncounterLifecycle.anchorOf(store, encounterRef, subject);
            return at == null ? fallback : at.getPosition();
        } catch (Throwable t) {
            return fallback;
        }
    }

    @Nullable
    static UUID runIdOf(@Nullable BossEncounter encounter) {
        return encounter == null ? null : encounter.runId;
    }
}
