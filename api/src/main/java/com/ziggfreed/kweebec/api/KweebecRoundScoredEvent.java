package com.ziggfreed.kweebec.api;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;

/**
 * Native scoring signal fired on the shared Hytale event bus when a Kweebec Nightmare
 * round ends, AFTER {@link RoundCompletedEvent}. It carries the per-player
 * {@link PlayerScore} breakdown (time / damage-avoided / stun bonus / total) so a
 * consumer (e.g. an installed MMO Skill Tree) can reward each player by their score
 * without re-deriving it - the capstone reward hook for the eventual Emerald Wilds
 * questline integration.
 *
 * <p>Sync {@code IEvent<Void>} POJO with the same fire/listen contract as every other
 * event here (see {@link RoundCompletedEvent}). Additive: the existing
 * {@code RoundCompletedEvent} still fires unchanged, so a consumer that only wants the
 * outcome ignores this one.
 */
public final class KweebecRoundScoredEvent implements IEvent<Void> {

    private final String roundId;
    private final String mode;
    private final RoundCompletedEvent.Outcome outcome;
    private final int partySize;
    private final int durationSeconds;
    private final int difficultyScore;
    private final Map<UUID, PlayerScore> scores;

    /**
     * @param roundId         unique id of the round instance
     * @param mode            gameplay mode id ({@code "chase"} / {@code "survival"})
     * @param outcome         how the round ended (mirrors {@link RoundCompletedEvent})
     * @param durationSeconds wall-clock length of the round in seconds
     * @param difficultyScore the round's resolved rule-set difficulty score (a reward multiplier)
     * @param scores          per-player score breakdown, keyed by UUID
     */
    public KweebecRoundScoredEvent(@Nonnull String roundId,
                                   @Nonnull String mode,
                                   @Nonnull RoundCompletedEvent.Outcome outcome,
                                   int durationSeconds,
                                   int difficultyScore,
                                   @Nonnull Map<UUID, PlayerScore> scores) {
        this.roundId = roundId;
        this.mode = mode;
        this.outcome = outcome;
        this.durationSeconds = durationSeconds;
        this.difficultyScore = difficultyScore;
        this.scores = Map.copyOf(scores);
        this.partySize = this.scores.size();
    }

    @Nonnull
    public String roundId() {
        return roundId;
    }

    @Nonnull
    public String mode() {
        return mode;
    }

    @Nonnull
    public RoundCompletedEvent.Outcome outcome() {
        return outcome;
    }

    /** Whether the round ended in a player win (escape or survival). */
    public boolean isWin() {
        return outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
    }

    public int partySize() {
        return partySize;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public int difficultyScore() {
        return difficultyScore;
    }

    /** Per-player score breakdown, keyed by player UUID (immutable copy). */
    @Nonnull
    public Map<UUID, PlayerScore> scores() {
        return scores;
    }
}
