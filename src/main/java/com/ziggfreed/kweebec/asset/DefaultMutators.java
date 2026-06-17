package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline round mutators, the in-memory {@code defaults} floor
 * {@link MutatorConfig} folds packs on top of. Each is a small bundle of ADDITIVE
 * deltas onto existing {@link com.ziggfreed.kweebec.round.RuleSet} knobs (see
 * {@link MutatorAsset}); a preset names a list of these ids and they stack
 * commutatively. These give instant procedural variety without authoring a full new
 * preset for every twist.
 *
 * <p>The matching {@code Server/KweebecNightmare/Mutators/*.json} files are the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 *
 * <p>The {@code of(...)} args are, in order:
 * {@code id, nameKey, hunterCountDelta, hunterSpeedMaxDelta, caveShrineCountDelta,
 * corruptionPerSecondDelta, shrineRelightSecondsDelta, roundCapSecondsDelta,
 * shrineBaseDelta}.
 */
public final class DefaultMutators {

    /** One extra hunter on the roster. */
    public static final String SWARM = "swarm";
    /** A faster corruption-ramp speed ceiling - the hunter peaks harder late-round. */
    public static final String RELENTLESS = "relentless";
    /** Two extra underground relight shrines - more descend-and-return objectives. */
    public static final String CAVERNS = "caverns";
    /** A steeper passive corruption climb - darkness and pace tighten sooner. */
    public static final String DECAY = "decay";
    /** A shorter cap with faster relights - a frantic sprint variant. */
    public static final String BLITZ = "blitz";

    private DefaultMutators() {
    }

    /** +1 hunter. */
    @Nonnull
    public static MutatorAsset swarm() {
        return MutatorAsset.of(SWARM, "kweebecnightmare.mutator.swarm.name",
                1, 0.0, 0, 0.0, 0.0, 0, 0);
    }

    /** +0.2 to the hunter speed ceiling at full corruption. */
    @Nonnull
    public static MutatorAsset relentless() {
        return MutatorAsset.of(RELENTLESS, "kweebecnightmare.mutator.relentless.name",
                0, 0.2, 0, 0.0, 0.0, 0, 0);
    }

    /** +2 underground cave shrines. */
    @Nonnull
    public static MutatorAsset caverns() {
        return MutatorAsset.of(CAVERNS, "kweebecnightmare.mutator.caverns.name",
                0, 0.0, 2, 0.0, 0.0, 0, 0);
    }

    /** A bump to the passive corruption-per-second ramp. */
    @Nonnull
    public static MutatorAsset decay() {
        return MutatorAsset.of(DECAY, "kweebecnightmare.mutator.decay.name",
                0, 0.0, 0, 0.0008, 0.0, 0, 0);
    }

    /** Shorter round cap and faster shrine relights - a frantic sprint. */
    @Nonnull
    public static MutatorAsset blitz() {
        return MutatorAsset.of(BLITZ, "kweebecnightmare.mutator.blitz.name",
                0, 0.0, 0, 0.0, -2.0, -300, 0);
    }

    /** All baseline mutators, in display order. */
    @Nonnull
    public static List<MutatorAsset> all() {
        return List.of(swarm(), relentless(), caverns(), decay(), blitz());
    }
}
