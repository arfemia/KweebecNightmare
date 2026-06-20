package com.ziggfreed.kweebec.mode.clash;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.ziggfreed.common.instance.match.TeamSplit;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Splits a Clash round's flat participant list into the two teams, keeping a pre-made party block together,
 * and writes the assignment onto the {@link RoundInstance}. The split itself is the generic
 * {@code ziggfreed-common} {@link TeamSplit}; this kweebec adapter holds only the policy (two teams of
 * {@code RuleSet.teamSize()}, and the party-block boundaries the lobby recorded). Runs on the spawning
 * world thread BEFORE the round goes ACTIVE (so off-thread reads of the team map are safe).
 */
public final class ClashTeamAssigner {

    private ClashTeamAssigner() {
    }

    public static void assign(@Nonnull RoundInstance round) {
        List<UUID> roster = round.participantList();
        int teamSize = round.ruleSet().teamSize();
        List<Integer> blocks = round.partyBlocks();
        if (blocks == null || blocks.isEmpty()) {
            // No lobby block info: treat the whole party as ONE pre-made block (a command-started stack).
            // TeamSplit clamps a block too big for one team and overflows the remainder to the other side.
            blocks = List.of(roster.size());
        }
        Map<UUID, Integer> map = TeamSplit.assign(roster, blocks, ClashState.TEAM_COUNT, teamSize);
        for (Map.Entry<UUID, Integer> e : map.entrySet()) {
            round.setTeam(e.getKey(), e.getValue());
        }
    }
}
