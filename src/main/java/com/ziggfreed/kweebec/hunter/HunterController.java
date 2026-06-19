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
     * Evaluate the asset-driven EXTRA-SPAWN RULES for the given {@code trigger} and spawn any extra
     * hunters whose rule fires now (placed NEAR the survivors per the rule's placement, respecting each
     * rule's cooldown / max-per-round / cap and the controller's global hunter cap). A no-op by default
     * (the human-driven mode has no roster); the AI controller implements it.
     *
     * <p>Called from {@link com.ziggfreed.kweebec.mode.chase.ChaseMode} on the instance world thread at
     * each trigger moment (round start, a shrine lit, a corruption-tier crossing, time elapsed, a survivor
     * nearing the gate).
     *
     * @param round   the live round
     * @param world   the instance world (world thread)
     * @param store   the entity store (world thread)
     * @param trigger the gameplay moment that just occurred
     * @param tierOrSeconds context for the trigger: the new corruption tier (CORRUPTION_TIER), the
     *                      round-elapsed seconds (TIME_ELAPSED), else ignored (pass 0)
     */
    default void evaluateSpawnRules(@Nonnull RoundInstance round, @Nonnull World world,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull SpawnTrigger trigger, int tierOrSeconds) {
        // no-op default - the human-driven hunter mode has no asset roster
    }
}
