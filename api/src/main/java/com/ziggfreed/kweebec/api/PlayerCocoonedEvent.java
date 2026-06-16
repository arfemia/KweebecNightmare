package com.ziggfreed.kweebec.api;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;

/**
 * Native signal fired on the shared Hytale event bus when a survivor is caught
 * by the hunter and cocooned (held dead-in-place, rescuable by a teammate).
 *
 * <p>Sync {@code IEvent<Void>} POJO. See {@link RoundCompletedEvent} for the
 * fire/listen contract.
 */
public final class PlayerCocoonedEvent implements IEvent<Void> {

    private final String roundId;
    private final String mode;
    private final UUID player;

    /**
     * @param roundId unique id of the round instance
     * @param mode    gameplay mode id ({@code "chase"} / {@code "survival"})
     * @param player  the cocooned player's UUID
     */
    public PlayerCocoonedEvent(@Nonnull String roundId, @Nonnull String mode, @Nonnull UUID player) {
        this.roundId = roundId;
        this.mode = mode;
        this.player = player;
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
    public UUID player() {
        return player;
    }
}
