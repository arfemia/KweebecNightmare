package com.ziggfreed.kweebec.api;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;

/**
 * Native signal fired on the shared Hytale event bus when a Kweebec Nightmare
 * round begins (all players teleported in, the hunt about to start).
 *
 * <p>Sync {@code IEvent<Void>} POJO. See {@link RoundCompletedEvent} for the
 * fire/listen contract.
 */
public final class RoundStartedEvent implements IEvent<Void> {

    private final String roundId;
    private final String mode;
    private final String presetName;
    private final List<UUID> participants;
    private final int partySize;

    /**
     * @param roundId      unique id of the round instance
     * @param mode         gameplay mode id ({@code "chase"} / {@code "survival"})
     * @param presetName   the active rule-set preset (e.g. {@code "nightmare"})
     * @param participants every player in the round (by UUID)
     */
    public RoundStartedEvent(@Nonnull String roundId,
                             @Nonnull String mode,
                             @Nonnull String presetName,
                             @Nonnull List<UUID> participants) {
        this.roundId = roundId;
        this.mode = mode;
        this.presetName = presetName;
        this.participants = List.copyOf(participants);
        this.partySize = this.participants.size();
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
    public String presetName() {
        return presetName;
    }

    @Nonnull
    public List<UUID> participants() {
        return participants;
    }

    public int partySize() {
        return partySize;
    }
}
