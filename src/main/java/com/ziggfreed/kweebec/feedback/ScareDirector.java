package com.ziggfreed.kweebec.feedback;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.camera.CameraShakeService;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.util.AssetIndexCache;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.atmosphere.MusicBedService;
import com.ziggfreed.kweebec.asset.ScareBeatAsset;
import com.ziggfreed.kweebec.asset.ScareBeatConfig;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * The horror conductor: a 1 Hz fan-out (called from {@code ChaseMode.tick} on the
 * instance world thread) that turns the round's corruption tier and each survivor's
 * nearest-hunter distance into PRE-AUTHORED scare beats. The band/tier-to-effect
 * mapping is DATA ({@link ScareBeatConfig} / {@link ScareBeatAsset}), never hardcoded
 * here.
 *
 * <p>Three layers, all best-effort + validated, none of which can throw into the round
 * loop:
 * <ul>
 *   <li><b>Proximity vignette</b> ({@link #tick}): per active survivor, the minimum
 *       distance to any hunter ({@code round.hunterController().hunterPositions(store)})
 *       maps to a band (3 closest, 2 mid, 1 far, 0 none), raised to a floor by the
 *       corruption tier; the band's {@link ScareBeatAsset} {@code EntityEffect} is
 *       applied to the survivor via {@link EffectControllerComponent} WITH HYSTERESIS
 *       (the applied band + effect index are tracked per UUID, swapped only on a change,
 *       removed on band 0 / inactive) - mirroring {@code AiHunterController.applySpeed}
 *       but on survivors.</li>
 *   <li><b>Jumpscare</b>: when a survivor enters the closest band for the first time
 *       (or {@link #onAlert} fires), a one-shot {@link CameraShakeService} shake + a 3D
 *       {@code SFX_Hedera_Scream} stinger + the {@code KweebecNightmare_Jumpscare} hard
 *       tint + an {@link RoundFeedback} title fire once, throttled per survivor so it
 *       never spams every tick.</li>
 *   <li><b>Whisper / false-cue layer</b>: a private low-frequency 3D
 *       {@code SFX_Emit_Forgotten_Whispers} stinger per survivor whose cadence tightens
 *       with corruption tier - its own daemon scheduler, mirroring
 *       {@link HeartbeatService}.</li>
 * </ul>
 *
 * <p>The PARENT calls {@link #clear(RoundInstance, Store)} from
 * {@code RoundService.teardown} (next to {@code MusicBedService.clear(round)}) to remove
 * every applied vignette effect, drop the per-player state, and stop the whisper
 * schedule for the round.
 */
public final class ScareDirector {

    // ---------------------------------------------------------------------
    // proximity bands
    // ---------------------------------------------------------------------

    /**
     * Squared horizontal distance thresholds (blocks^2) for the proximity bands, from
     * closest to farthest. {@code <= [0]} -> band 3 (closest), {@code <= [1]} -> band 2,
     * {@code <= [2]} -> band 1, beyond -> band 0 (none). Squared so the per-survivor
     * test stays a cheap {@code dx*dx + dz*dz} with no sqrt. Hysteresis (below) keeps a
     * survivor straddling a boundary from flickering between bands.
     */
    private static final double[] BAND_DIST_SQ = {
            6.0 * 6.0,    // band 3: within 6 blocks - the hunter is on top of you
            14.0 * 14.0,  // band 2: within 14 blocks
            28.0 * 28.0,  // band 1: within 28 blocks
    };

    /** Extra distance (blocks) a survivor must move OUT before a band drops (anti-flicker). */
    private static final double HYSTERESIS_BLOCKS = 3.0;

    /** Closest proximity band (= {@link ScareBeatAsset#MAX_BAND}); entering it triggers the jumpscare. */
    private static final int CLOSEST_BAND = ScareBeatAsset.MAX_BAND;

    /** Minimum gap (ms) between jumpscares for one survivor, so a catch cannot spam every tick. */
    private static final long JUMPSCARE_COOLDOWN_MS = 12_000L;

    /** Camera-effect id for the jumpscare shake (validated by CameraShakeService; missing = no-op). */
    private static final String JUMPSCARE_SHAKE_ID = "KweebecNightmare_JumpscareShake";
    /** Shake intensity in the engine's 0..1 space. */
    private static final float JUMPSCARE_SHAKE_INTENSITY = 1.0f;

    /**
     * Full lang keys for the jumpscare title banner. Defined here (the same full-key form
     * {@code i18n/Lang} uses) because {@code Lang} is not this owner's file; the i18n owner
     * should add the matching unprefixed {@code .lang} lines ({@code title.jumpscare} +
     * {@code title.jumpscare.sub}) and may promote these to {@code Lang} constants. Until a
     * line exists the client renders the raw key, so these are referenced through the
     * try-guarded {@link RoundFeedback#title} only.
     */
    private static final String TITLE_JUMPSCARE = "kweebecnightmare.title.jumpscare";
    private static final String TITLE_JUMPSCARE_SUB = "kweebecnightmare.title.jumpscare.sub";

    // ---------------------------------------------------------------------
    // whisper layer
    // ---------------------------------------------------------------------

    /** Whisper interval per corruption tier (ms): rare when calm -> frequent at max. */
    private static final long[] WHISPER_INTERVAL_MS = { 18_000L, 11_000L, 6_000L };

    /** Candidate whisper sound ids: the design cue first, then a generic void fallback. */
    private static final String[] WHISPER_CANDIDATES = {
            "SFX_Emit_Forgotten_Whispers", "SFX_Emit_Whispers", "KweebecNightmare_Whisper"
    };

    /**
     * One shared cache that resolves the FIRST registered whisper candidate id. Used only
     * as the whisper gate (whether ANY whisper asset exists); the actual playback
     * re-resolves the chosen id through {@link Sound3D}'s own per-id cache.
     */
    private static final AssetIndexCache<com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent>
            WHISPER_INDEX = AssetIndexCache.ofCandidates(WHISPER_CANDIDATES,
                    id -> com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getIndex(id));

    private static final ScheduledExecutorService WHISPER_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kweebec-whisper");
                t.setDaemon(true);
                return t;
            });

    /** Live whisper schedule per round id. */
    private static final Map<String, ScheduledFuture<?>> WHISPER_ACTIVE = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------------
    // per-round, per-survivor vignette + jumpscare state (world-thread only)
    // ---------------------------------------------------------------------

    /** Applied vignette band per survivor UUID; absent / 0 = no vignette applied. */
    private static final Map<UUID, Integer> APPLIED_BAND = new ConcurrentHashMap<>();
    /** Asset-map index of the currently-applied vignette effect per survivor (for removal). */
    private static final Map<UUID, Integer> APPLIED_EFFECT_INDEX = new ConcurrentHashMap<>();
    /** Last jumpscare epoch ms per survivor (the per-player throttle). */
    private static final Map<UUID, Long> LAST_JUMPSCARE_MS = new ConcurrentHashMap<>();

    private ScareDirector() {
    }

    // =====================================================================
    // 1 Hz tick (called from ChaseMode.tick, instance world thread)
    // =====================================================================

    /**
     * One scare pass on the instance world thread: refresh each active survivor's
     * proximity vignette (with hysteresis), fire a throttled jumpscare on a survivor who
     * just entered the closest band, and lazily arm the whisper schedule for the round.
     * Best-effort throughout; a missing asset / bad ref degrades to a no-op, never a
     * throw.
     */
    public static void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        // Arm the whisper schedule once the round is running (idempotent: start() cancels any prior).
        ensureWhisper(round);

        int tier = chase.corruptionTier();
        // Escalate the forced dread bed when the corruption tier rises (cheap no-op when unchanged).
        MusicBedService.applyTierForSurvivors(round, tier);
        List<Vector3d> hunterPositions = hunterPositions(round, store);

        for (PlayerRoundState st : round.playerStates()) {
            UUID uuid = st.playerId();
            if (!st.isActive()) {
                // Inactive (cocooned/escaped/left): clear any held vignette so a freed
                // player starts clean and a left player leaks no effect.
                clearVignetteFor(uuid, store);
                continue;
            }
            Ref<EntityStore> ref = survivorRef(uuid);
            Vector3d pos = positionOf(store, ref);
            if (ref == null || pos == null) {
                continue;
            }
            // Capture the band BEFORE the swap so "first entry into the closest band" reads
            // the prior frame (applyVignette mutates APPLIED_BAND).
            Integer prevObj = APPLIED_BAND.get(uuid);
            int prevBand = prevObj == null ? 0 : prevObj;

            int rawBand = nearestBand(pos, hunterPositions);
            // The corruption tier raises the FLOOR band: deeper corruption keeps a baseline
            // dread vignette up even when no hunter is close (tier 2 -> floor band 1).
            int floor = Math.max(0, tier - 1);
            int targetBand = Math.max(rawBand, floor);
            targetBand = applyHysteresis(uuid, targetBand, pos, hunterPositions);

            applyVignette(uuid, ref, targetBand, tier, store);

            // Jumpscare on first entry into the closest band (throttled per survivor).
            if (targetBand >= CLOSEST_BAND && prevBand < CLOSEST_BAND) {
                fireJumpscare(uuid, ref, store);
            }
        }
    }

    /**
     * Public hook for a hard scare moment the round flow knows about (e.g. the gate
     * alert). Fires the throttled jumpscare for the named survivor; safe to call from
     * any world-thread context. A {@code null} / unknown survivor is a no-op.
     */
    public static void onAlert(@Nonnull RoundInstance round, @Nullable UUID survivorId,
                               @Nonnull Store<EntityStore> store) {
        if (survivorId == null) {
            return;
        }
        PlayerRoundState st = round.playerState(survivorId);
        if (st == null || !st.isActive()) {
            return;
        }
        Ref<EntityStore> ref = survivorRef(survivorId);
        if (ref == null || !ref.isValid()) {
            return;
        }
        fireJumpscare(survivorId, ref, store);
    }

    // =====================================================================
    // proximity vignette (per-survivor EntityEffect, hysteresis swap)
    // =====================================================================

    /**
     * Map a survivor position to a proximity band (3 closest .. 1 far, 0 none) from the
     * minimum horizontal distance to any hunter. An empty hunter list (no hunter yet)
     * always yields band 0.
     */
    private static int nearestBand(@Nonnull Vector3d pos, @Nonnull List<Vector3d> hunterPositions) {
        double bestSq = minDistanceSq(pos, hunterPositions);
        if (bestSq == Double.MAX_VALUE) {
            return 0;
        }
        if (bestSq <= BAND_DIST_SQ[0]) {
            return 3;
        }
        if (bestSq <= BAND_DIST_SQ[1]) {
            return 2;
        }
        if (bestSq <= BAND_DIST_SQ[2]) {
            return 1;
        }
        return 0;
    }

    /**
     * Hold a survivor at their CURRENTLY-applied band unless the new target is higher OR
     * they have moved clearly past the band's outer edge - the anti-flicker margin. Only
     * a DROP is dampened (a rise to a closer/deeper band is always immediate, so the
     * dread tightens responsively).
     */
    private static int applyHysteresis(@Nonnull UUID uuid, int targetBand,
                                       @Nonnull Vector3d pos, @Nonnull List<Vector3d> hunterPositions) {
        Integer appliedObj = APPLIED_BAND.get(uuid);
        int applied = appliedObj == null ? 0 : appliedObj;
        if (targetBand >= applied) {
            return targetBand; // rise (or unchanged) is immediate
        }
        // A drop: only allow it once the survivor is past the applied band's outer edge
        // plus the hysteresis margin (the applied band index is 1..3 -> threshold index applied-1).
        if (applied >= 1 && applied <= BAND_DIST_SQ.length) {
            double edge = Math.sqrt(BAND_DIST_SQ[applied - 1]) + HYSTERESIS_BLOCKS;
            double bestSq = minDistanceSq(pos, hunterPositions);
            if (bestSq != Double.MAX_VALUE && bestSq < edge * edge) {
                return applied; // still inside the sticky margin - hold
            }
        }
        return targetBand;
    }

    /** Minimum squared horizontal distance from {@code pos} to any hunter, or MAX_VALUE if none. */
    private static double minDistanceSq(@Nonnull Vector3d pos, @Nonnull List<Vector3d> hunterPositions) {
        double bestSq = Double.MAX_VALUE;
        for (Vector3d h : hunterPositions) {
            if (h == null) {
                continue;
            }
            double dx = pos.x() - h.x();
            double dz = pos.z() - h.z();
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
            }
        }
        return bestSq;
    }

    /**
     * Swap the survivor's vignette {@link EntityEffect} to the beat for {@code targetBand}
     * (gated by {@code tier}) only when the band CHANGES (hysteresis on the caller side).
     * Mirrors {@code AiHunterController.applySpeed}: remove the previously-applied effect
     * index, add the new one. Best-effort: a missing effect asset or a survivor without an
     * {@link EffectControllerComponent} leaves them un-tinted.
     */
    private static void applyVignette(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref,
                                      int targetBand, int tier, @Nonnull Store<EntityStore> store) {
        Integer appliedObj = APPLIED_BAND.get(uuid);
        int applied = appliedObj == null ? 0 : appliedObj;
        if (targetBand == applied) {
            return; // unchanged - the common case; no per-tick effect churn
        }

        ScareBeatAsset beat = targetBand <= 0 ? null
                : ScareBeatConfig.getInstance().bandBeat(targetBand, tier);

        var assetMap = EntityEffect.getAssetMap();
        String newId = beat == null ? null : beat.effectId();
        int newEffectIndex = (newId == null || newId.isBlank())
                ? Integer.MIN_VALUE : assetMap.getIndex(newId);
        EntityEffect newEffect = newEffectIndex == Integer.MIN_VALUE ? null : assetMap.getAsset(newEffectIndex);
        if (newId != null && !newId.isBlank() && newEffect == null) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] vignette effect '" + newId + "' not registered; survivor un-tinted.");
        }

        if (!ref.isValid()) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }
        try {
            Integer prevIndex = APPLIED_EFFECT_INDEX.get(uuid);
            if (prevIndex != null && prevIndex != Integer.MIN_VALUE) {
                effects.removeEffect(ref, prevIndex, store);
            }
            if (newEffect != null) {
                effects.addEffect(ref, newEffect, store);
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] vignette swap failed: " + t.getMessage());
            return;
        }
        if (targetBand <= 0 || newEffect == null) {
            APPLIED_BAND.remove(uuid);
            APPLIED_EFFECT_INDEX.remove(uuid);
        } else {
            APPLIED_BAND.put(uuid, targetBand);
            APPLIED_EFFECT_INDEX.put(uuid, newEffectIndex);
        }
    }

    /** Remove a survivor's currently-applied vignette effect + drop their band state. */
    private static void clearVignetteFor(@Nonnull UUID uuid, @Nonnull Store<EntityStore> store) {
        Integer prevIndex = APPLIED_EFFECT_INDEX.remove(uuid);
        APPLIED_BAND.remove(uuid);
        if (prevIndex == null || prevIndex == Integer.MIN_VALUE) {
            return;
        }
        Ref<EntityStore> ref = survivorRef(uuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }
        try {
            effects.removeEffect(ref, prevIndex, store);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] vignette clear failed: " + t.getMessage());
        }
    }

    // =====================================================================
    // jumpscare (one-shot, throttled per survivor)
    // =====================================================================

    /**
     * Fire the one-shot jumpscare for one survivor (subject to the per-player cooldown):
     * a camera shake + a 3D scream stinger + the {@code KweebecNightmare_Jumpscare} hard
     * tint + a title banner. Every channel is validated/try-guarded.
     */
    private static void fireJumpscare(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref,
                                      @Nonnull Store<EntityStore> store) {
        long now = System.currentTimeMillis();
        Long last = LAST_JUMPSCARE_MS.get(uuid);
        if (last != null && now - last < JUMPSCARE_COOLDOWN_MS) {
            return;
        }
        LAST_JUMPSCARE_MS.put(uuid, now);

        ScareBeatAsset beat = ScareBeatConfig.getInstance().jumpscareBeat();

        Vector3d pos = positionOf(store, ref);
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());

        // 1) Camera shake (validated inside CameraShakeService; missing asset = no-op).
        if (pr != null) {
            CameraShakeService.shake(pr, JUMPSCARE_SHAKE_ID, JUMPSCARE_SHAKE_INTENSITY);
        }

        // 2) 3D scream stinger, private to this survivor (Sound3D validates the id).
        if (beat != null && beat.soundId() != null && !beat.soundId().isBlank() && pos != null) {
            Sound3D.play(beat.soundId(), SoundCategory.SFX, pos.x(), pos.y(), pos.z(),
                    Sound3D.onlyEntity(ref), store, "JUMPSCARE", false);
        }

        // 3) Hard-tint EntityEffect one-shot (best-effort; never disturbs the held vignette state).
        if (beat != null && beat.effectId() != null && !beat.effectId().isBlank() && ref.isValid()) {
            try {
                var assetMap = EntityEffect.getAssetMap();
                int idx = assetMap.getIndex(beat.effectId());
                EntityEffect eff = idx == Integer.MIN_VALUE ? null : assetMap.getAsset(idx);
                EffectControllerComponent effects = eff == null ? null
                        : store.getComponent(ref, EffectControllerComponent.getComponentType());
                if (eff != null && effects != null) {
                    effects.addEffect(ref, eff, store);
                } else if (eff == null) {
                    KweebecNightmarePlugin.LOGGER.atFine().log(
                            "[Kweebec][scare] jumpscare effect '" + beat.effectId() + "' not registered.");
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec][scare] jumpscare tint failed: " + t.getMessage());
            }
        }

        // 4) Title banner.
        if (pr != null) {
            RoundFeedback.title(pr, TITLE_JUMPSCARE, TITLE_JUMPSCARE_SUB, true);
        }
    }

    // =====================================================================
    // whisper / false-cue layer (daemon scheduler, mirrors HeartbeatService)
    // =====================================================================

    /** Arm the whisper schedule for the round if not already running (idempotent). */
    private static void ensureWhisper(@Nonnull RoundInstance round) {
        if (WHISPER_ACTIVE.containsKey(round.roundId())) {
            return;
        }
        scheduleWhisper(round, WHISPER_INTERVAL_MS[0]);
    }

    private static void scheduleWhisper(@Nonnull RoundInstance round, long delayMs) {
        // compute() runs the resolved-check inside the map's per-key lock, so a clear()
        // (which removes the key once the round is resolved) cannot be resurrected by a
        // concurrent re-arm from an in-flight whisper.
        WHISPER_ACTIVE.compute(round.roundId(), (k, existing) -> {
            if (round.isResolved()) {
                return null;
            }
            return WHISPER_SCHEDULER.schedule(() -> whisperPulse(round), delayMs, TimeUnit.MILLISECONDS);
        });
    }

    private static void stopWhisper(@Nonnull String roundId) {
        ScheduledFuture<?> f = WHISPER_ACTIVE.remove(roundId);
        if (f != null) {
            f.cancel(false);
        }
    }

    private static void whisperPulse(@Nonnull RoundInstance round) {
        if (round.isResolved()) {
            stopWhisper(round.roundId());
            return;
        }
        World world = round.world();
        ChaseState chase = round.chaseState();
        if (world == null || chase == null) {
            scheduleWhisper(round, WHISPER_INTERVAL_MS[0]);
            return;
        }
        // Gate: if no whisper candidate is registered yet, stop pulsing (the round still plays).
        if (WHISPER_INDEX.resolve() == AssetIndexCache.UNRESOLVED) {
            stopWhisper(round.roundId());
            return;
        }

        int tier = chase.corruptionTier();
        long interval = WHISPER_INTERVAL_MS[Math.max(0, Math.min(WHISPER_INTERVAL_MS.length - 1, tier))];

        try {
            world.execute(() -> playWhispersForSurvivors(round, world));
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] whisper dispatch failed: " + t.getMessage());
        }

        if (!round.isResolved()) {
            scheduleWhisper(round, interval);
        }
    }

    private static void playWhispersForSurvivors(@Nonnull RoundInstance round, @Nonnull World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        String whisperId = WHISPER_INDEX.resolvedIdOrNull();
        if (whisperId == null) {
            return;
        }
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = survivorRef(st.playerId());
            Vector3d pos = positionOf(store, ref);
            if (ref == null || pos == null) {
                continue;
            }
            // A FALSE cue: mislocate the whisper a few blocks off the survivor so it reads
            // as something just out of sight, not a sound stuck to them. Deterministic
            // per (round, player) so it does not jitter wildly each pulse.
            double[] off = falseOffset(round.roundId(), st.playerId());
            Sound3D.play(whisperId, SoundCategory.SFX,
                    pos.x() + off[0], pos.y(), pos.z() + off[1],
                    Sound3D.onlyEntity(ref), store, "WHISPER", false);
        }
    }

    /** A small deterministic XZ offset (blocks) for the mislocated false-cue whisper. */
    @Nonnull
    private static double[] falseOffset(@Nonnull String roundId, @Nonnull UUID playerId) {
        int h = (roundId.hashCode() * 31) ^ playerId.hashCode();
        double angle = (h & 0xFFFF) / 65535.0 * Math.PI * 2.0;
        double radius = 5.0 + ((h >>> 16) & 0x7) * 0.75; // 5.0 .. ~10.25 blocks
        return new double[]{ Math.cos(angle) * radius, Math.sin(angle) * radius };
    }

    // =====================================================================
    // teardown (PARENT calls this from RoundService.teardown)
    // =====================================================================

    /**
     * Remove every applied vignette effect for the round's survivors, drop all per-player
     * scare state (vignette band, effect index, jumpscare throttle), and stop the round's
     * whisper schedule. Mirrors {@code MusicBedService.clear(round)} - the parent wires
     * this into {@code RoundService.teardown} on the instance world thread.
     */
    public static void clear(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        stopWhisper(round.roundId());
        Set<UUID> roundPlayers = new HashSet<>();
        try {
            for (PlayerRoundState st : round.playerStates()) {
                UUID uuid = st.playerId();
                roundPlayers.add(uuid);
                clearVignetteFor(uuid, store);
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] clear failed: " + t.getMessage());
        }
        // Drop the jumpscare throttle for everyone in this round (the vignette maps are
        // already pruned by clearVignetteFor).
        for (UUID uuid : roundPlayers) {
            LAST_JUMPSCARE_MS.remove(uuid);
        }
    }

    /** Cancel every whisper schedule (plugin shutdown). */
    public static void shutdown() {
        WHISPER_ACTIVE.values().forEach(f -> f.cancel(false));
        WHISPER_ACTIVE.clear();
        WHISPER_SCHEDULER.shutdownNow();
    }

    // =====================================================================
    // ref / position helpers
    // =====================================================================

    /**
     * Live hunter world positions for the round (empty if none). Consumes the
     * cross-owner {@code HunterController.hunterPositions(store)} contract EXACTLY as
     * specified; degrades to an empty list (and thus band 0 for everyone) if the
     * controller is absent or throws.
     */
    @Nonnull
    private static List<Vector3d> hunterPositions(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        var hunter = round.hunterController();
        if (hunter == null) {
            return List.of();
        }
        try {
            List<Vector3d> positions = hunter.hunterPositions(store);
            return positions == null ? List.of() : positions;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][scare] hunterPositions failed: " + t.getMessage());
            return List.of();
        }
    }

    @Nullable
    private static Ref<EntityStore> survivorRef(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        return pr == null ? null : pr.getReference();
    }

    @Nullable
    private static Vector3d positionOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        return tc == null ? null : tc.getPosition();
    }
}
