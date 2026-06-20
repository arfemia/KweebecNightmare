package com.ziggfreed.kweebec.round;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;

/**
 * Drives every live round once per second. Polling happens off-thread; the mode
 * tick + every {@code Store}/{@code Ref} read hops onto the round's own world
 * thread via {@code world.execute}. When the mode returns an outcome, the round
 * is resolved.
 */
public final class RoundStateMachine {

    private static final long TICK_INTERVAL_SEC = 1;

    private final RoundService service;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> tickFuture;

    RoundStateMachine(@Nonnull RoundService service) {
        this.service = service;
    }

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kweebec-state-machine");
            t.setDaemon(true);
            return t;
        });
        tickFuture = scheduler.scheduleAtFixedRate(this::tickAll,
                TICK_INTERVAL_SEC, TICK_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        if (tickFuture != null) {
            tickFuture.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void tickAll() {
        for (RoundInstance round : service.registry().all()) {
            if (round.state() != InstanceState.ACTIVE) {
                continue;
            }
            World world = round.world();
            if (world == null) {
                continue;
            }
            try {
                world.execute(() -> tickInstance(round));
            } catch (Throwable t) {
                // world shutting down - skip this tick
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec] tick dispatch skipped for " + round.roundId() + ": " + t.getMessage());
            }
        }
    }

    private void tickInstance(@Nonnull RoundInstance round) {
        if (round.state() != InstanceState.ACTIVE || round.isResolved()) {
            return;
        }
        World world = round.world();
        if (world == null) {
            return;
        }
        RoundCompletedEvent.Outcome outcome;
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            RoundMode mode = ModeRegistry.get(round.mode());
            outcome = mode != null ? mode.tick(round, world, store) : null;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] tick threw for " + round.roundId() + ": " + t.getMessage());
            outcome = null;
        }
        if (outcome != null) {
            service.resolve(round, outcome);
        }
    }
}
