package com.ziggfreed.kweebec.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.encounter.run.SpawnOptions;
import com.ziggfreed.kweebec.round.KweebecMode;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The round-to-framework mapping {@code HunterEncounter} hands {@code EncounterSpawner}: the round
 * owns the run (which is how the factors and the wave action find it), the preset labels it, nothing
 * scales (there is no subject), and only the survivors still in the round are seeded.
 */
class HunterEncounterOptionsTest {

    @Test
    void theRoundOwnsTheRunAndSeedsItsSurvivors() {
        RuleSet rules = RuleSet.builder("hardcore").bossHealthMultiplier(2.0).build();
        RoundInstance round = new RoundInstance("round-9", KweebecMode.CHASE, rules, 0L);
        UUID stayed = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        round.addPlayer(stayed);
        round.addPlayer(left);
        round.markLeft(left);

        SpawnOptions options = HunterEncounter.optionsFor(round);

        assertEquals("round-9", options.ownerKey(), "the round id owns the run");
        assertEquals("hardcore", options.difficulty(), "the preset id is the run's difficulty label");
        assertEquals(1.0, options.healthMultiplier(), 1.0e-9, "the hunter encounter scales nothing");
        assertFalse(options.showMarker(), "a round never shows the creative encounter marker");
        assertEquals(List.of(stayed), options.seedMembers(), "only survivors still in the round are seeded");
        assertTrue(round.presentPlayerIds().contains(stayed));
        assertFalse(round.presentPlayerIds().contains(left));
    }
}
