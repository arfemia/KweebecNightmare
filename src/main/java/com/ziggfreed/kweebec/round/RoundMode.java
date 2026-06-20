package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.event.DifficultyScore;

/**
 * One gameplay mode's behaviour over the shared round engine. The engine
 * ({@link RoundStateMachine} / {@link RoundService}) is mode-agnostic: it dispatches every per-round
 * call through the {@link ModeRegistry} ({@code registry.get(round.mode())}), so adding a mode is one
 * implementation + one registration in {@code KweebecNightmarePlugin.setup()} - no engine edit, no
 * {@code switch}, no mode imports in {@code round/}.
 *
 * <p>All callbacks run on the instance WORLD thread (the engine hops there before invoking them). An
 * implementation hangs its per-round state on {@link RoundInstance#modeState()} (cast in the impl) and
 * keeps the engine free of mode-specific types. Defaults give the co-op (Chase) behaviour so a mode only
 * overrides what differs.
 */
public interface RoundMode {

    /**
     * Stand the round up on its freshly-spawned instance world (build mode state, spawn actors, build the
     * arena, apply atmosphere/markers/model swaps, assign teams). Called from {@code onInstanceReady} after
     * the world + seed are stamped and BEFORE the round goes {@code ACTIVE}.
     */
    void onStart(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * One 1 Hz tick on the world thread. Return the resolving {@link RoundCompletedEvent.Outcome}, or
     * {@code null} to keep the round running.
     */
    @Nullable
    RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                     @Nonnull Store<EntityStore> store);

    /** Whether this outcome counts as the round COMPLETING successfully (drives {@code InstanceState}). */
    default boolean isWin(@Nonnull RoundCompletedEvent.Outcome outcome) {
        return outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
    }

    /** Objective progress for the native {@code RoundCompletedEvent} (chase: shrines lit; PvP: a score). */
    default int objectiveProgress(@Nonnull RoundInstance round) {
        return 0;
    }

    /** The 0-based winning team for the native event (PvP {@code TEAM_ELIMINATED}), or {@code null} (co-op / draw). */
    @Nullable
    default Integer winnerTeam(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        return null;
    }

    /** The difficulty score the native event carries (an MMO scales rewards by it). */
    default int difficultyScore(@Nonnull RuleSet ruleSet) {
        return DifficultyScore.compute(ruleSet);
    }

    /**
     * Mode-specific resolution: fire the scored event, record the leaderboard, stash + open results, show
     * the end titles. Called from {@code RoundService.resolve} after the generic
     * {@code RoundCompletedEvent} fires. {@code world}/{@code store} are non-null only when the instance
     * world is still up (the normal path); they are null on the headless teardown path (record the
     * leaderboard, skip the in-instance UI).
     */
    default void onResolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                           int duration, int difficultyScore,
                           @Nullable World world, @Nullable Store<EntityStore> store) {
    }

    /**
     * Mode-specific teardown alongside the generic HUD-restore (e.g. restore a swapped player model,
     * remove worldmap markers). Called on the world thread during {@code RoundService} teardown.
     */
    default void onTeardown(@Nonnull RoundInstance round, @Nonnull World world,
                            @Nonnull Store<EntityStore> store) {
    }
}
