package com.ziggfreed.kweebec.mode.domination;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.worldmap.WorldMapMarkers;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.event.DifficultyScore;
import com.ziggfreed.kweebec.experience.KweebecClashExperience;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.mode.clash.ClashModelSwapper;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundMode;
import com.ziggfreed.kweebec.round.RoundModeSupport;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The {@link RoundMode} adapter for {@link com.ziggfreed.kweebec.round.KweebecMode#DOMINATION}: delegates to
 * {@link DominationMode}, scores the PvP difficulty, derives the winning team for the native event, shows
 * the per-team end titles, and restores swapped models + clears the capture-point markers on teardown.
 */
public final class DominationRoundMode implements RoundMode {

    @Override
    public void onStart(@Nonnull RoundInstance round, @Nonnull World world,
                        @Nonnull Store<EntityStore> store) {
        DominationMode.onStart(round, world, store);
    }

    @Override
    @Nullable
    public RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                            @Nonnull Store<EntityStore> store) {
        return DominationMode.tick(round, world, store);
    }

    @Override
    public boolean isWin(@Nonnull RoundCompletedEvent.Outcome outcome) {
        return outcome == RoundCompletedEvent.Outcome.TEAM_ELIMINATED;
    }

    @Override
    public int difficultyScore(@Nonnull RuleSet ruleSet) {
        return DifficultyScore.computeClash(ruleSet);
    }

    @Override
    public int objectiveProgress(@Nonnull RoundInstance round) {
        DominationState ds = (DominationState) round.modeState();
        return ds != null ? Math.max(ds.teamScore(0), ds.teamScore(1)) : 0;
    }

    @Override
    @Nullable
    public Integer winnerTeam(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        DominationState ds = (DominationState) round.modeState();
        int w = ds != null ? ds.winningTeam() : -1;
        return w >= 0 ? w : null;
    }

    @Override
    public void onResolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                          int duration, int difficultyScore,
                          @Nullable World world, @Nullable Store<EntityStore> store) {
        if (world == null) {
            return;
        }
        DominationState ds = (DominationState) round.modeState();
        int winner = ds != null ? ds.winningTeam() : -1;
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
        try {
            WorldMapMarkers.clearAll(world);
        } catch (Throwable ignored) {
            // best effort
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (PlayerRoundState st : round.playerStates()) {
            Ref<EntityStore> ref = RoundModeSupport.presentRef(st.playerId(), worldUuid);
            if (ref != null && ref.isValid()) {
                ClashModelSwapper.restore(ref, store, st.playerId());
            }
        }
    }
}
