package com.ziggfreed.kweebec.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Server-pulsed 3D heartbeat: a private one-shot played to each active survivor
 * at their own position, re-armed on a tightening interval driven by the round's
 * corruption tier (the 3-tier terror-radius cadence). There is no engine StopSound
 * for a loop, so the dread bed is repeated one-shots; stopping = stop scheduling.
 */
public final class HeartbeatService {

    /** Pulse interval per corruption tier (ms): calm -> frantic. */
    private static final long[] TIER_INTERVAL_MS = { 1500L, 1000L, 600L };

    /** Candidate heartbeat sound ids: the pack asset first, then any vanilla fallback. */
    private static final String[] HEARTBEAT_CANDIDATES = {
            "KweebecNightmare_Heartbeat", "Heartbeat", "UI_Heartbeat"
    };

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kweebec-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static final Map<String, ScheduledFuture<?>> ACTIVE = new ConcurrentHashMap<>();

    /** -1 = unresolved, Integer.MIN_VALUE = no asset found, else the sound index. */
    private static volatile int soundIndex = -1;

    private HeartbeatService() {
    }

    public static void start(@Nonnull RoundInstance round) {
        stop(round.roundId());
        schedule(round, 1000L);
    }

    public static void stop(@Nonnull String roundId) {
        ScheduledFuture<?> f = ACTIVE.remove(roundId);
        if (f != null) {
            f.cancel(false);
        }
    }

    public static void shutdown() {
        ACTIVE.values().forEach(f -> f.cancel(false));
        ACTIVE.clear();
        SCHEDULER.shutdownNow();
    }

    private static void schedule(@Nonnull RoundInstance round, long delayMs) {
        // compute() runs the resolved-check inside the map's per-key lock, so a
        // stop() (which removes the key once the round is resolved) cannot be
        // resurrected by a concurrent re-arm from an in-flight pulse().
        ACTIVE.compute(round.roundId(), (k, existing) -> {
            if (round.isResolved()) {
                return null;
            }
            return SCHEDULER.schedule(() -> pulse(round), delayMs, TimeUnit.MILLISECONDS);
        });
    }

    private static void pulse(@Nonnull RoundInstance round) {
        if (round.isResolved()) {
            stop(round.roundId());
            return;
        }
        World world = round.world();
        ChaseState chase = round.chaseState();
        if (world == null || chase == null) {
            // Not ready yet; re-check shortly.
            schedule(round, 1000L);
            return;
        }
        int idx = resolveSound();
        if (idx == Integer.MIN_VALUE) {
            // No heartbeat asset available; stop pulsing (the round still plays).
            stop(round.roundId());
            return;
        }

        int tier = chase.corruptionTier();
        long interval = TIER_INTERVAL_MS[Math.max(0, Math.min(TIER_INTERVAL_MS.length - 1, tier))];

        try {
            world.execute(() -> playForSurvivors(round, world, idx));
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] heartbeat dispatch failed: " + t.getMessage());
        }

        if (!round.isResolved()) {
            schedule(round, interval);
        }
    }

    private static void playForSurvivors(@Nonnull RoundInstance round, @Nonnull World world, int idx) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            UUID uuid = st.playerId();
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr == null) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d pos = tc.getPosition();
            Predicate<Ref<EntityStore>> onlyMe = candidate -> {
                PlayerRef cand = store.getComponent(candidate, PlayerRef.getComponentType());
                return cand != null && uuid.equals(cand.getUuid());
            };
            try {
                SoundUtil.playSoundEvent3d(idx, SoundCategory.SFX, pos.x, pos.y, pos.z, onlyMe, store);
            } catch (Throwable ignored) {
                // a single missed pulse is harmless
            }
        }
    }

    private static int resolveSound() {
        int cached = soundIndex;
        if (cached != -1) {
            return cached; // a real index was found and cached
        }
        for (String id : HEARTBEAT_CANDIDATES) {
            try {
                int i = SoundEvent.getAssetMap().getIndex(id);
                if (i != Integer.MIN_VALUE) {
                    soundIndex = i; // cache ONLY a real index
                    return i;
                }
            } catch (Throwable ignored) {
                return Integer.MIN_VALUE; // map not ready - skip this pulse, retry next round
            }
        }
        // No heartbeat asset registered. Do NOT cache MIN_VALUE permanently, so a
        // later round re-resolves in case the asset is registered afterwards.
        return Integer.MIN_VALUE;
    }
}
