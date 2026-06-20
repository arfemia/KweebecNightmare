package com.ziggfreed.kweebec.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;

/**
 * Native completion signal fired on the shared Hytale event bus when a Kweebec
 * Nightmare round ends.
 *
 * <p>It is a sync {@code IEvent<Void>} POJO: the plugin fires it via
 * {@code HytaleServer.get().getEventBus().dispatchFor(RoundCompletedEvent.class)}
 * guarded by {@code hasListener()}. A consumer (e.g. MMO Skill Tree) listens via
 * the reflective {@code registerGlobal} adapter - it loads THIS class from this
 * mod's classloader so {@code Class} identity matches across the jar boundary.
 * No shared library, no bundling on the consumer side.
 *
 * <p>Deliberately shaped to mirror a dungeon-completion event so a future shared
 * dungeon framework can consume it without a rewrite.
 */
public final class RoundCompletedEvent implements IEvent<Void> {

    /** How the round ended. */
    public enum Outcome {
        /** Chase: the required survivor group held the Heartwood extraction platform together long enough to escape. */
        ESCAPED,
        /** Survival: the party reached dawn. */
        SURVIVED,
        /** Every survivor was cocooned. */
        CAUGHT,
        /** The night timer / round cap expired with no win. */
        TIMED_OUT,
        /** The round was force-ended (admin / last player left). */
        ABORTED
    }

    private final String roundId;
    private final String mode;
    private final Outcome outcome;
    private final List<UUID> participants;
    private final int partySize;
    private final int durationSeconds;
    private final int objectiveProgress;
    private final int difficultyScore;

    /**
     * @param roundId           unique id of the round instance
     * @param mode              gameplay mode id ({@code "chase"} / {@code "survival"})
     * @param outcome           how the round ended
     * @param participants      every player who took part (by UUID)
     * @param durationSeconds   wall-clock length of the round in seconds
     * @param objectiveProgress mode objective progress at end (chase: shrines lit; survival: wave reached)
     * @param difficultyScore   stable difficulty score of the round's resolved rule-set
     *                          (see {@code DifficultyScore.compute}); a consumer scales
     *                          rewards by it. {@code 0} when the firing path supplies none.
     */
    public RoundCompletedEvent(@Nonnull String roundId,
                               @Nonnull String mode,
                               @Nonnull Outcome outcome,
                               @Nonnull List<UUID> participants,
                               int durationSeconds,
                               int objectiveProgress,
                               int difficultyScore) {
        this.roundId = roundId;
        this.mode = mode;
        this.outcome = outcome;
        this.participants = List.copyOf(participants);
        this.partySize = this.participants.size();
        this.durationSeconds = durationSeconds;
        this.objectiveProgress = objectiveProgress;
        this.difficultyScore = difficultyScore;
    }

    @Nonnull
    public String roundId() {
        return roundId;
    }

    /** Gameplay mode id: {@code "chase"} or {@code "survival"}. */
    @Nonnull
    public String mode() {
        return mode;
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

    public int partySize() {
        return partySize;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    /** Chase: number of shrines relit. Survival: highest wave reached. */
    public int objectiveProgress() {
        return objectiveProgress;
    }

    /**
     * Stable difficulty score of the round's resolved rule-set (more hunters / faster
     * hunter / deeper caves / steeper corruption ramp / more shrines = higher). An
     * installed MMO Skill Tree multiplies a round's reward by it. {@code 0} when the
     * firing path supplied none (the legacy no-score fire path).
     */
    public int difficultyScore() {
        return difficultyScore;
    }
}
