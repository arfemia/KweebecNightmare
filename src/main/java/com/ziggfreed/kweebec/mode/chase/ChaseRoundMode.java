package com.ziggfreed.kweebec.mode.chase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.worldmap.DiscoveryMode;
import com.ziggfreed.common.worldmap.MapDiscovery;
import com.ziggfreed.kweebec.api.PlayerScore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.atmosphere.AtmosphereService;
import com.ziggfreed.kweebec.event.RoundEvents;
import com.ziggfreed.kweebec.experience.KweebecExperience;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.feedback.ScareDirector;
import com.ziggfreed.kweebec.hunter.AiHunterController;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.integration.KweebecNightmareAPI;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundMode;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.score.ScoreCalculator;
import com.ziggfreed.kweebec.score.ScoringConfig;

/**
 * The {@link RoundMode} adapter for {@link com.ziggfreed.kweebec.round.KweebecMode#CHASE}: it owns Chase's
 * instance stand-up (mode state, hunter, shrine-discovery markers, frozen atmosphere, arena build), its
 * 1 Hz loop (delegated to {@link ChaseMode#tick}), its per-player scoring + results, and its teardown.
 * The behaviour is exactly what the engine used to do inline for Chase, relocated here so the engine is
 * mode-agnostic.
 */
public final class ChaseRoundMode implements RoundMode {

    @Override
    public void onStart(@Nonnull RoundInstance round, @Nonnull World world,
                        @Nonnull Store<EntityStore> store) {
        ChaseMode.onStart(round);
        round.setHunterController(new AiHunterController(RoundService.HUNTER_ROLE));
        // Shrine-discovery markers: stand up the per-player tracker BEFORE the first interaction (attach also
        // enables the compass). OFF presets pay nothing.
        if (round.ruleSet().shrineDiscovery() != DiscoveryMode.OFF) {
            MapDiscovery discovery = new MapDiscovery("kweebec_discovery");
            round.setMapDiscovery(discovery);
            discovery.attach(world);
        }
        AtmosphereService.lock(world);
        ArenaBuilder.build(round, world);
    }

    @Override
    @Nullable
    public RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                            @Nonnull Store<EntityStore> store) {
        return ChaseMode.tick(round, world, store);
    }

    @Override
    public int objectiveProgress(@Nonnull RoundInstance round) {
        ChaseState chase = round.chaseState();
        return chase != null ? chase.litShrines() : 0;
    }

    @Override
    public void onResolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                          int duration, int difficultyScore,
                          @Nullable World world, @Nullable Store<EntityStore> store) {
        boolean win = isWin(outcome);
        Map<UUID, PlayerScore> scores = computeScores(round, outcome, win, duration);
        RoundEvents.fireRoundScored(round.roundId(), round.mode().id(), outcome, duration, difficultyScore, scores);
        KweebecExperience.recordScores(round.partySize(), round, scores);
        if (world != null) {
            showResult(round, outcome);
            // Stash the per-player results snapshot + open the BUTTON-LESS in-instance preview during the
            // hold. The reward grant + the full interactive page are deferred to overworld return.
            KweebecExperience.stashResults(round, outcome, win, duration, difficultyScore, scores);
        }
    }

    @Override
    public void onTeardown(@Nonnull RoundInstance round, @Nonnull World world,
                           @Nonnull Store<EntityStore> store) {
        if (round.hunterController() != null) {
            round.hunterController().despawnAll(world, store);
        }
        // Tear the boss capstone (the Warden + adds + boss HUD) down so it never leaks past the round.
        if (round.bossController() != null) {
            round.bossController().despawnAll(world, store);
        }
        // Drop the shrine-discovery marker provider (defensive; the world is destroyed right after).
        if (round.mapDiscovery() != null) {
            round.mapDiscovery().detach(world);
        }
        // Clear every survivor's scare vignette + jumpscare throttle + whisper schedule.
        ScareDirector.clear(round, store);
    }

    /**
     * Compute every participant's {@link PlayerScore} from their {@link PlayerRoundState} (damage taken,
     * Moonbloom stuns) plus the round duration, against the runtime-resolved {@link ScoringConfig}. A
     * player's win = the round was a win AND they personally escaped (chase) / were not downed at dawn
     * (survival). Relocated verbatim from the old {@code RoundService.computeScores}.
     */
    @Nonnull
    private static Map<UUID, PlayerScore> computeScores(@Nonnull RoundInstance round,
                                                        @Nonnull RoundCompletedEvent.Outcome outcome,
                                                        boolean win, int duration) {
        ScoringConfig scoring = KweebecNightmareAPI.resolveScoring(round.ruleSet().scoring());
        ChaseState chase = round.chaseState();
        boolean allShrinesLit = chase != null && chase.allShrinesLit();
        Map<UUID, PlayerScore> scores = new HashMap<>();
        for (PlayerRoundState st : round.playerStates()) {
            boolean playerWin = win && (st.hasEscaped()
                    || (outcome == RoundCompletedEvent.Outcome.SURVIVED
                        && !st.isCocooned() && !st.hasLeftRound()));
            scores.put(st.playerId(), ScoreCalculator.compute(
                    duration, st.damageTaken(), st.mobsStunned(),
                    st.moonbloomCollected(), st.shrinesLit(), allShrinesLit, playerWin, scoring));
        }
        return scores;
    }

    /** Show the end-of-round title banner to every present survivor. Relocated from {@code RoundService}. */
    private static void showResult(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        boolean win = outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr == null) {
                continue;
            }
            if (win) {
                RoundFeedback.title(pr, Lang.TITLE_YOU_SURVIVED, Lang.TITLE_YOU_SURVIVED_SUB, true);
            } else if (outcome == RoundCompletedEvent.Outcome.TIMED_OUT) {
                RoundFeedback.title(pr, Lang.TITLE_NIGHT_FALLS, Lang.TITLE_NIGHT_FALLS_SUB, true);
            } else {
                RoundFeedback.title(pr, Lang.TITLE_CAUGHT, Lang.TITLE_CAUGHT_SUB, true);
            }
        }
    }
}
