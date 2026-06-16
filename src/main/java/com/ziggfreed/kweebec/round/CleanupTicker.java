package com.ziggfreed.kweebec.round;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.world.World;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Backstop eviction: once a round is EVICTING (players ejected) for a grace
 * period, force-remove its instance world off-thread and drop the registry entry.
 * The engine RemovalSystem (driven by the instance's authored RemovalConditions)
 * is the primary path; this catches the case where it does not fire.
 */
public final class CleanupTicker {

    private static final long POLL_INTERVAL_SEC = 20;
    private static final long EVICT_GRACE_MS = 15_000L;

    private final RoundService service;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;

    CleanupTicker(@Nonnull RoundService service) {
        this.service = service;
    }

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kweebec-cleanup-ticker");
            t.setDaemon(true);
            return t;
        });
        future = scheduler.scheduleAtFixedRate(this::tick,
                POLL_INTERVAL_SEC, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (RoundInstance round : service.registry().all()) {
            try {
                evaluate(round, now);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] cleanup threw for " + round.roundId() + ": " + t.getMessage());
            }
        }
    }

    private void evaluate(@Nonnull RoundInstance round, long now) {
        if (round.state() != InstanceState.EVICTING) {
            return;
        }
        if (now - round.stateChangedAtMs() < EVICT_GRACE_MS) {
            return;
        }
        World world = round.world();
        if (world != null) {
            InstanceLifecycle.removeWorldOffThread(world);
        }
        service.registry().remove(round.roundId());
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] cleanup evicted round " + round.roundId());
    }
}
