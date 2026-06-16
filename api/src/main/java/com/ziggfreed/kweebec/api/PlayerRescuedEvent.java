package com.ziggfreed.kweebec.api;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.event.IEvent;

/**
 * Native signal fired on the shared Hytale event bus when a cocooned player is
 * freed and revived in-place by a teammate.
 *
 * <p>Sync {@code IEvent<Void>} POJO. See {@link RoundCompletedEvent} for the
 * fire/listen contract.
 */
public final class PlayerRescuedEvent implements IEvent<Void> {

    private final String roundId;
    private final String mode;
    private final UUID player;
    @Nullable
    private final UUID rescuer;

    /**
     * @param roundId unique id of the round instance
     * @param mode    gameplay mode id ({@code "chase"} / {@code "survival"})
     * @param player  the revived player's UUID
     * @param rescuer the teammate who freed them (null if an automatic / timed revive)
     */
    public PlayerRescuedEvent(@Nonnull String roundId, @Nonnull String mode,
                              @Nonnull UUID player, @Nullable UUID rescuer) {
        this.roundId = roundId;
        this.mode = mode;
        this.player = player;
        this.rescuer = rescuer;
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

    @Nullable
    public UUID rescuer() {
        return rescuer;
    }
}
