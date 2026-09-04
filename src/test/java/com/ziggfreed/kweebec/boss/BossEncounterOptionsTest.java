package com.ziggfreed.kweebec.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ziggfreed.common.encounter.run.SpawnOptions;
import com.ziggfreed.kweebec.round.KweebecMode;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The round-to-framework mapping {@code BossEncounter} hands {@code EncounterSpawner}: what a round
 * says about itself becomes what the fight is owned by, labelled with, scaled by and seeded with.
 * The rule set here is authored by the test (not a shipped preset), so a balance pass never touches it.
 */
class BossEncounterOptionsTest {

    @Test
    void theRoundBecomesTheRunsOwnerDifficultyMultiplierAndParty() {
        RuleSet rules = RuleSet.builder("nightmare")
                .bossEnabled(true)
                .bossId("KweebecNightmare_Warden_Encounter")
                .bossHealthMultiplier(1.4)
                .build();
        RoundInstance round = new RoundInstance("round-7", KweebecMode.CHASE, rules, 0L);
        UUID stayed = UUID.randomUUID();
        UUID alsoStayed = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        round.addPlayer(stayed);
        round.addPlayer(left);
        round.addPlayer(alsoStayed);
        round.markLeft(left);

        SpawnOptions options = BossEncounter.optionsFor(round);

        assertEquals("round-7", options.ownerKey(), "the round id owns the run");
        assertEquals("nightmare", options.difficulty(), "the preset id is the run's difficulty label");
        assertEquals(1.4, options.healthMultiplier(), 1.0e-9, "the preset's boss multiplier is the run multiplier");
        assertFalse(options.showMarker(), "a round never shows the creative encounter marker");
        assertEquals(2, options.seedMembers().size(), "only survivors still in the round are seeded");
        assertTrue(options.seedMembers().containsAll(List.of(stayed, alsoStayed)));
        assertFalse(options.seedMembers().contains(left), "a player who left is not seeded into the fight");
    }

    @Test
    void anUnauthoredMultiplierReadsAsNoScaling() {
        RuleSet rules = RuleSet.builder("amateur").bossEnabled(true).bossId("KweebecNightmare_Warden_Encounter").build();
        RoundInstance round = new RoundInstance("round-1", KweebecMode.CHASE, rules, 0L);

        SpawnOptions options = BossEncounter.optionsFor(round);

        assertEquals(1.0, options.healthMultiplier(), 1.0e-9);
        assertTrue(options.seedMembers().isEmpty(), "an empty party seeds nobody");
    }

    @Test
    void aRoundWithoutAFightMatchesNoRunId() {
        RuleSet rules = RuleSet.builder("amateur").build();
        RoundInstance round = new RoundInstance("round-2", KweebecMode.CHASE, rules, 0L);

        assertNull(BossEncounter.runIdOf(round.bossEncounter()), "no fight, no run id");
        assertNull(BossEncounterListener.roundFor(List.of(round), UUID.randomUUID()),
                "a beat for an unknown run belongs to no round");
    }
}
