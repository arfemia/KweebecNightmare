package com.ziggfreed.kweebec.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.encounter.run.EncounterFactors;
import com.ziggfreed.common.encounter.run.SpawnOptions;
import com.ziggfreed.common.encounter.run.ZigEncounterRun;
import com.ziggfreed.common.factor.FactorContext;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.KweebecMode;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The two contributed readings resolve from the run in the context to the chase round that owns it,
 * and answer nothing (never zero) at every rung of that walk that has no answer, so a script sensor
 * on them stays shut outside a live chase round. The round and its state are the test's own.
 */
class HunterFactorsTest {

    private static final Function<String, RoundInstance> NO_ROUNDS = id -> null;

    private static FactorContext ownedBy(String roundId) {
        ZigEncounterRun run = ZigEncounterRun.forSpawn(SpawnOptions.forRound(roundId, "nightmare", 1.0, List.of()));
        return FactorContext.builder().payload(new EncounterFactors.RunReading(run, 0, 0L)).build();
    }

    private static RoundInstance chaseRound(String roundId) {
        return new RoundInstance(roundId, KweebecMode.CHASE, RuleSet.builder("nightmare").build(), 0L);
    }

    @Test
    void aContextWithNoRunReadsNothing() {
        FactorContext ctx = FactorContext.builder().build();

        assertNull(HunterFactors.read(ctx, NO_ROUNDS, ChaseState::corruptionTier));
        assertNull(HunterFactors.read(ctx, NO_ROUNDS, ChaseState::litShrines));
    }

    @Test
    void aRunNobodyOwnsReadsNothing() {
        ZigEncounterRun unowned = ZigEncounterRun.forSpawn(SpawnOptions.defaults());
        FactorContext ctx = FactorContext.builder().payload(new EncounterFactors.RunReading(unowned, 0, 0L)).build();

        assertNull(HunterFactors.read(ctx, id -> chaseRound(id), ChaseState::corruptionTier));
    }

    @Test
    void anOwnerNoRoundAnswersToReadsNothing() {
        assertNull(HunterFactors.read(ownedBy("round-gone"), NO_ROUNDS, ChaseState::corruptionTier));
    }

    @Test
    void aRoundWithoutChaseStateReadsNothing() {
        RoundInstance round = chaseRound("round-1");

        assertNull(HunterFactors.read(ownedBy("round-1"), id -> round, ChaseState::litShrines));
    }

    @Test
    void theTierAndTheShrineCountReadOffTheLiveRound() {
        RoundInstance round = chaseRound("round-3");
        ChaseState chase = new ChaseState(5);
        chase.setCorruption(1.0);
        chase.shrineForBlock(1, 80, 1).setLit(true);
        chase.shrineForBlock(2, 80, 2);
        round.setChaseState(chase);
        Function<String, RoundInstance> rounds = id -> "round-3".equals(id) ? round : null;

        Double tier = HunterFactors.read(ownedBy("round-3"), rounds, ChaseState::corruptionTier);
        Double lit = HunterFactors.read(ownedBy("round-3"), rounds, ChaseState::litShrines);

        assertEquals((double) chase.corruptionTier(), tier, "the tier is the chase state's own reading");
        assertTrue(tier > 0.0, "a meter at its ceiling is past the first tier");
        assertEquals(1.0, lit, "one shrine lit, one discovered and dark");
        assertNull(HunterFactors.read(ownedBy("round-4"), rounds, ChaseState::corruptionTier),
                "another run's owner key never reads this round");
    }
}
