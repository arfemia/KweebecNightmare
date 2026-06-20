package com.ziggfreed.kweebec.mode.clash;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.event.DifficultyScore;
import com.ziggfreed.kweebec.experience.KweebecClashExperience;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundMode;
import com.ziggfreed.kweebec.round.RoundModeSupport;

/**
 * The {@link RoundMode} adapter for {@link com.ziggfreed.kweebec.round.KweebecMode#CLASH}: delegates the
 * loop + stand-up to {@link ClashMode}, derives the win flag + winning team for the native event, scores
 * the PvP difficulty, shows the per-team end titles, and restores swapped models on teardown.
 */
public final class ClashRoundMode implements RoundMode {

    @Override
    public void onStart(@Nonnull RoundInstance round, @Nonnull World world,
                        @Nonnull Store<EntityStore> store) {
        ClashMode.onStart(round, world, store);
    }

    @Override
    @Nullable
    public RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                            @Nonnull Store<EntityStore> store) {
        return ClashMode.tick(round, world, store);
    }

    @Override
    public boolean isWin(@Nonnull RoundCompletedEvent.Outcome outcome) {
        return outcome == RoundCompletedEvent.Outcome.TEAM_ELIMINATED;
    }

    @Override
    public int difficultyScore(@Nonnull com.ziggfreed.kweebec.round.RuleSet ruleSet) {
        return DifficultyScore.computeClash(ruleSet);
    }

    @Override
    public int objectiveProgress(@Nonnull RoundInstance round) {
        ClashState cs = (ClashState) round.modeState();
        if (cs == null) {
            return 0;
        }
        int[] scores = cs.scoresFor(round, round.ruleSet().clash().winCondition());
        int max = 0;
        for (int s : scores) {
            max = Math.max(max, s);
        }
        return max;
    }

    @Override
    @Nullable
    public Integer winnerTeam(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        ClashState cs = (ClashState) round.modeState();
        int w = cs != null ? cs.winningTeam() : -1;
        return w >= 0 ? w : null;
    }

    @Override
    public void onResolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                          int duration, int difficultyScore,
                          @Nullable World world, @Nullable Store<EntityStore> store) {
        if (world == null) {
            return;
        }
        ClashState cs = (ClashState) round.modeState();
        int winner = cs != null ? cs.winningTeam() : -1;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr == null) {
                continue;
            }
            int team = round.teamOf(st.playerId());
            if (outcome == RoundCompletedEvent.Outcome.DRAW || winner < 0) {
                RoundFeedback.title(pr, Lang.CLASH_TITLE_DRAW, Lang.CLASH_TITLE_DRAW_SUB, true);
            } else if (team == winner) {
                RoundFeedback.title(pr, Lang.CLASH_TITLE_VICTORY, Lang.CLASH_TITLE_VICTORY_SUB, true);
            } else {
                RoundFeedback.title(pr, Lang.CLASH_TITLE_DEFEAT, Lang.CLASH_TITLE_DEFEAT_SUB, true);
            }
        }
        // Record the team result into the PvP leaderboard + open the deferred two-team results page.
        KweebecClashExperience.recordResult(round, outcome, duration, difficultyScore, world, store);
    }

    @Override
    public void onTeardown(@Nonnull RoundInstance round, @Nonnull World world,
                           @Nonnull Store<EntityStore> store) {
        // Proactively restore swapped models for present players (the PlayerReady catch-all is the guarantee).
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (PlayerRoundState st : round.playerStates()) {
            Ref<EntityStore> ref = RoundModeSupport.presentRef(st.playerId(), worldUuid);
            if (ref != null && ref.isValid()) {
                ClashModelSwapper.restore(ref, store, st.playerId());
            }
        }
    }
}
