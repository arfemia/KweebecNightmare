package com.ziggfreed.kweebec.hunter;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The hunter seam: everything the round engine needs to drive a pursuer without
 * caring whether it is AI-driven (the only implementation today,
 * {@link AiHunterController}) or human-driven (the asymmetric "play as the
 * Kweebec" mode, architected-for but built post-jam).
 *
 * <p>ALL methods run on the instance world thread (the state machine hops via
 * {@code world.execute} before calling them).
 */
public interface HunterController {

    /** Spawn the hunter(s) for this round (called once when the ritual / hunt begins). */
    void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * Per-tick update: drive the hunter toward the chosen survivor (the gate-alert lock, else
     * the loudest shrine channeller, else the nearest active survivor) and apply the
     * corruption-scaled speed ramp. The AI implementation lures via Perfect Utils' aggro API.
     */
    void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * The Heartwood Gate alert: hard-lock every hunter onto the single nearest
     * survivor for the final chase to the exit.
     */
    void onAlert(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /** Remove all hunters (round end / teardown). */
    void despawnAll(@Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * Live world positions of every active hunter (empty if none / not spawned). The
     * {@code ScareDirector} consumes this to band a survivor's dread by their distance
     * to the NEAREST hunter, so a multi-hunter roster makes the whole grove menacing.
     * Skips invalid refs; never throws. World-thread only (reads {@code TransformComponent}).
     */
    @Nonnull
    List<Vector3d> hunterPositions(@Nonnull Store<EntityStore> store);

    /**
     * Resolve the on-hit punishment bundle for the hunter {@code attacker} - the slow + outgoing-damage
     * scaling + proximity-stack window/cap + the desperation-enrage knobs, folded from the hunter's
     * archetype over the round's {@link RuleSet} baseline (a non-null/non-zero archetype field wins).
     * The enrage damage multiplier is already baked into {@link OnHitConfig#damageMult()} when this unit
     * is currently enraged (the controller tracks enrage per live hunter).
     *
     * <p>Returns {@code null} when {@code attacker} is not one of this controller's live hunters, so the
     * damage observer applies no punishment to a non-hunter (or stale) attacker. World-thread only.
     *
     * @param attacker the entity that landed the hit (the damage source ref)
     * @return the resolved on-hit config, or {@code null} if {@code attacker} is not a live hunter here
     */
    @Nullable
    OnHitConfig resolveOnHitConfigFor(@Nullable Ref<EntityStore> attacker);

    /**
     * Record that the hunter {@code attacker} just landed a hit on a survivor (resets that hunter's
     * desperation-enrage idle timer, so a hunter only enrages after going {@code enrageAfterSeconds}
     * WITHOUT connecting). A no-op for a non-hunter ref. Called from the damage observer on the world
     * thread.
     *
     * @param attacker the hunter that landed the hit
     * @param nowMs    the wall-clock time of the hit (the same clock {@code tick} compares against)
     */
    void noteHunterLandedHit(@Nullable Ref<EntityStore> attacker, long nowMs);

    /**
     * Put one wave of hunters down NEAR the survivors, as the hunter encounter script asks for it at
     * each rung of its escalation (the {@code KweebecHunterWave} action): the wave's count, scaled by
     * the party when it says so and clamped to the room under the round's live-hunter ceiling, placed
     * on a ring around or scattered near one survivor or the party's centre, each body the archetype
     * the wave names or the round's own tier-gated weighted pick. A no-op by default (the human-driven
     * mode has no roster); the AI controller implements it.
     *
     * <p>Called on the instance world thread, between ticks.
     *
     * @param round the live round
     * @param world the instance world (world thread)
     * @param store the entity store (world thread)
     * @param wave  what the script asked for
     * @return how many hunters actually went down (0 when the wave found no room or no survivor)
     */
    default int spawnWave(@Nonnull RoundInstance round, @Nonnull World world,
                          @Nonnull Store<EntityStore> store, @Nonnull HunterWave wave) {
        return 0; // the human-driven hunter mode has no roster to draw a wave from
    }
}
