package com.ziggfreed.kweebec.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Public completion signal emitted when a Kweebec Nightmare round ends.
 *
 * <p>Deliberately shaped to mirror a dungeon-completion event so MMOSkillTree
 * (or a future shared dungeon framework) can consume it across the jar boundary
 * without a rewrite. This is the one type that lives in the {@code api} module so
 * a consumer can compile against it without the full plugin jar.
 *
 * <p>Scaffold stage: a plain immutable carrier. It gains an engine event-bus base
 * (or a listener-registry hook) when the round engine and the soft-dep bridge land.
 */
public final class RoundCompletedEvent {

    /** How the round ended. */
    public enum Outcome {
        ESCAPED,
        SURVIVED,
        CAUGHT,
        TIMED_OUT,
        ABORTED
    }

    private final String roundId;
    private final Outcome outcome;
    private final List<UUID> participants;
    private final int durationSeconds;

    public RoundCompletedEvent(@Nonnull String roundId,
                               @Nonnull Outcome outcome,
                               @Nonnull List<UUID> participants,
                               int durationSeconds) {
        this.roundId = roundId;
        this.outcome = outcome;
        this.participants = List.copyOf(participants);
        this.durationSeconds = durationSeconds;
    }

    @Nonnull
    public String roundId() {
        return roundId;
    }

    @Nonnull
    public Outcome outcome() {
        return outcome;
    }

    /** Whether the round ended in a player win (escape or survival). */
    public boolean isWin() {
        return outcome == Outcome.ESCAPED || outcome == Outcome.SURVIVED;
    }

    @Nonnull
    public List<UUID> participants() {
        return participants;
    }

    public int durationSeconds() {
        return durationSeconds;
    }
}
