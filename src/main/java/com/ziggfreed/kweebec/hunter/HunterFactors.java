package com.ziggfreed.kweebec.hunter;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.encounter.run.EncounterFactors;
import com.ziggfreed.common.factor.FactorContext;
import com.ziggfreed.common.factor.FactorContributions;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * The two readings a chase round contributes to every factor vocabulary on the server, so the hunter
 * encounter script can gate its rungs on the round with a plain {@code ZigFactor} sensor and no Java:
 * <ul>
 *   <li>{@value #CORRUPTION_TIER}: {@link ChaseState#corruptionTier()}, 0 / 1 / 2;</li>
 *   <li>{@value #SHRINES_LIT}: {@link ChaseState#litShrines()}, the shrines cleansed so far.</li>
 * </ul>
 *
 * <p>Each provider finds the round the same way the wave action does: the run in the context's
 * payload names its owner, the owner key is a round id, the registry gives the round, and its chase
 * state gives the number. A null anywhere answers null, which is the vocabulary's own fail-closed
 * rule: a sensor on either id simply never matches outside a live chase round, with no guard to write.
 */
public final class HunterFactors {

    public static final String CORRUPTION_TIER = "kweebecnightmare:corruption_tier";
    public static final String SHRINES_LIT = "kweebecnightmare:shrines_lit";

    private HunterFactors() {
    }

    /** Contribute both ids, once, from plugin setup. */
    public static void contribute() {
        contribute(id -> RoundService.getInstance().registry().byId(id));
    }

    /** {@link #contribute()} over an explicit round lookup (a test's, or a private registry's). */
    static void contribute(@Nonnull Function<String, RoundInstance> rounds) {
        FactorContributions.register(CORRUPTION_TIER, KweebecNightmarePlugin.REGISTRY_OWNER,
                ctx -> read(ctx, rounds, ChaseState::corruptionTier));
        FactorContributions.register(SHRINES_LIT, KweebecNightmarePlugin.REGISTRY_OWNER,
                ctx -> read(ctx, rounds, ChaseState::litShrines));
    }

    /**
     * One reading off the chase round that owns the run in {@code ctx}, or null when the context
     * carries no run, the run has no owner, no round answers to it, or the round is not a chase.
     */
    @Nullable
    static Double read(@Nonnull FactorContext ctx, @Nonnull Function<String, RoundInstance> rounds,
            @Nonnull ToIntFunction<ChaseState> reading) {
        EncounterFactors.RunReading run = ctx.payload(EncounterFactors.RunReading.class);
        String owner = run == null ? null : run.run().ownerKey();
        RoundInstance round = owner == null ? null : rounds.apply(owner);
        ChaseState chase = round == null ? null : round.chaseState();
        return chase == null ? null : (double) reading.applyAsInt(chase);
    }
}
