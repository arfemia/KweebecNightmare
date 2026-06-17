package com.ziggfreed.kweebec.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.util.AssetIndexCache;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Server-pulsed 3D heartbeat: a private one-shot played to each active survivor
 * at their own position, re-armed on a tightening interval driven by the round's
 * corruption tier (the 3-tier terror-radius cadence). There is no engine StopSound
 * for a loop, so the dread bed is repeated one-shots; stopping = stop scheduling.
 *
 * <p>Playback routes through the shared {@link Sound3D} seam (the ziggfreed-common
 * 3D-sound primitive: category + per-listener predicate + index cache) rather than a
 * private {@code SoundUtil}/index-cache copy. A shared {@link AssetIndexCache} over
 * the heartbeat candidate ids gates the pulse: when no candidate is registered we
 * stop pulsing (the round still plays); it caches ONLY a real index so a later round
 * re-resolves if the asset lands afterwards.
 */
public final class HeartbeatService {

    /** Pulse interval per corruption tier (ms): calm -> frantic. */
    private static final long[] TIER_INTERVAL_MS = { 1500L, 1000L, 600L };

    /** Candidate heartbeat sound ids: the pack asset first, then any vanilla fallback. */
    private static final String[] HEARTBEAT_CANDIDATES = {
            "KweebecNightmare_Heartbeat", "Heartbeat", "UI_Heartbeat"
    };

    /**
     * One shared cache that resolves the FIRST registered candidate id (pack first,
     * then vanilla). Used only as the pulse gate (whether ANY heartbeat asset exists);
     * the actual playback re-resolves the chosen id through {@link Sound3D}'s own cache.
     */
    private static final AssetIndexCache<SoundEvent> HEARTBEAT_INDEX =
            AssetIndexCache.ofCandidates(HEARTBEAT_CANDIDATES, id -> SoundEvent.getAssetMap().getIndex(id));

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kweebec-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static final Map<String, ScheduledFuture<?>> ACTIVE = new ConcurrentHashMap<>();

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
        // Gate: if no heartbeat candidate is registered yet, stop pulsing (the round
        // still plays). A round-scoped resolve re-runs next round if the asset lands.
        if (HEARTBEAT_INDEX.resolve() == AssetIndexCache.UNRESOLVED) {
            stop(round.roundId());
            return;
        }

        int tier = chase.corruptionTier();
        long interval = TIER_INTERVAL_MS[Math.max(0, Math.min(TIER_INTERVAL_MS.length - 1, tier))];

        try {
            world.execute(() -> playForSurvivors(round, world));
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] heartbeat dispatch failed: " + t.getMessage());
        }

        if (!round.isResolved()) {
            schedule(round, interval);
        }
    }

    private static void playForSurvivors(@Nonnull RoundInstance round, @Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        String heartbeatId = HEARTBEAT_INDEX.resolvedIdOrNull();
        if (heartbeatId == null) {
            return;
        }
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
            // Private to this survivor: only their own ref hears it (the self-only predicate).
            Sound3D.play(heartbeatId, SoundCategory.SFX, pos.x(), pos.y(), pos.z(),
                    Sound3D.onlyEntity(ref), store, "HEARTBEAT", false);
        }
    }
}
