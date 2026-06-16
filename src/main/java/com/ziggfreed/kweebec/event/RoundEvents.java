package com.ziggfreed.kweebec.event;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.server.core.HytaleServer;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.api.PlayerCocoonedEvent;
import com.ziggfreed.kweebec.api.PlayerRescuedEvent;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.api.RoundStartedEvent;

/**
 * Fires the mod's native {@code IEvent<Void>} POJOs on the shared Hytale event
 * bus. Each call resolves the dispatcher, guards on {@code hasListener()} (so it
 * is a silent no-op with zero listeners), then dispatches synchronously on the
 * calling thread. Fire from a world-thread context so a listener (synchronous on
 * the firing thread) can resolve a player then hop via {@code world.execute}.
 *
 * <p>This is the entire integration surface: outbound events, no MMO dependency.
 * A consumer subscribes via the reflective {@code registerGlobal} adapter (it
 * loads these event classes from this mod's classloader so {@code Class} identity
 * matches across the jar boundary).
 */
public final class RoundEvents {

    private RoundEvents() {
    }

    public static void fireRoundStarted(@Nonnull String roundId, @Nonnull String mode,
                                        @Nonnull String presetName, @Nonnull List<UUID> participants) {
        try {
            IEventDispatcher<RoundStartedEvent, RoundStartedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(RoundStartedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new RoundStartedEvent(roundId, mode, presetName, participants));
            }
        } catch (Throwable t) {
            log("RoundStarted", t);
        }
    }

    public static void fireRoundCompleted(@Nonnull String roundId, @Nonnull String mode,
                                          @Nonnull RoundCompletedEvent.Outcome outcome,
                                          @Nonnull List<UUID> participants,
                                          int durationSeconds, int objectiveProgress) {
        try {
            IEventDispatcher<RoundCompletedEvent, RoundCompletedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(RoundCompletedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new RoundCompletedEvent(
                        roundId, mode, outcome, participants, durationSeconds, objectiveProgress));
            }
        } catch (Throwable t) {
            log("RoundCompleted", t);
        }
    }

    public static void firePlayerCocooned(@Nonnull String roundId, @Nonnull String mode, @Nonnull UUID player) {
        try {
            IEventDispatcher<PlayerCocoonedEvent, PlayerCocoonedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(PlayerCocoonedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new PlayerCocoonedEvent(roundId, mode, player));
            }
        } catch (Throwable t) {
            log("PlayerCocooned", t);
        }
    }

    public static void firePlayerRescued(@Nonnull String roundId, @Nonnull String mode,
                                         @Nonnull UUID player, @Nullable UUID rescuer) {
        try {
            IEventDispatcher<PlayerRescuedEvent, PlayerRescuedEvent> d =
                    HytaleServer.get().getEventBus().dispatchFor(PlayerRescuedEvent.class);
            if (d.hasListener()) {
                d.dispatch(new PlayerRescuedEvent(roundId, mode, player, rescuer));
            }
        } catch (Throwable t) {
            log("PlayerRescued", t);
        }
    }

    private static void log(@Nonnull String which, @Nonnull Throwable t) {
        KweebecNightmarePlugin.LOGGER.atWarning().log(
                "[Kweebec] failed to fire " + which + " event: " + t.getMessage());
    }
}
