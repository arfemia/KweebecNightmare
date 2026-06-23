package com.ziggfreed.kweebec.atmosphere;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.world.ForcedMusicService;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Forces a dread MUSIC bed for the round, distinct from the SFX heartbeat. This is the
 * mod-SPECIFIC POLICY layer (the dread-container candidate ladders + per-tier selection +
 * the per-player one-shot apply logic); the forced-music ENGINE mechanism (resolving the
 * container index, setting the {@code ForcedMusicTracker}, and pushing the
 * {@code UpdateForcedMusic} packet) is delegated to ziggfreed-common's
 * {@link ForcedMusicService}. Kweebec owns WHICH container to force; common owns HOW.
 *
 * <p>The bed plays on the music channel while {@link com.ziggfreed.kweebec.feedback.HeartbeatService}
 * pulses on {@code SoundCategory.SFX}, so the two coexist. World-thread only; fully
 * try-guarded. Diagnostic at INFO so the next playtest log proves the path ran.
 */
public final class MusicBedService {

    /** Dread containers, first that resolves wins (validate before use). Pack asset first (not yet authored), then vanilla Zone4-dark / Void. */
    private static final String[] DREAD_MUSIC_CANDIDATES = {
            "KweebecNightmare_BlightTheme", "MC_Zone4_Dark", "Track_Portal_Void_Event", "MC_Zone4_Caves"
    };

    /**
     * Per-corruption-tier dread containers (a parallel ladder above the base
     * {@link #DREAD_MUSIC_CANDIDATES}): tier 0 = the calm bed, tier 1 = mid, tier 2 = the
     * heaviest dread bed. Each row is its own first-that-resolves candidate list (pack
     * asset first, then a vanilla heavier fallback), so an un-authored tier falls back to
     * a vanilla container; if a whole row resolves to nothing the swap keeps the previous
     * tier's bed (never silences the round). Indexed by {@code corruptionTier()} (0/1/2).
     */
    private static final String[][] TIER_MUSIC_CANDIDATES = {
            // tier 0: calm - the base dread bed.
            { "KweebecNightmare_BlightTheme", "MC_Zone4_Dark", "Track_Portal_Void_Event", "MC_Zone4_Caves" },
            // tier 1: mid - a darker variant.
            { "KweebecNightmare_BlightTheme_Tier2", "Track_Portal_Void_Event", "MC_Zone4_Caves", "MC_Zone4_Dark" },
            // tier 2: max - the heaviest dread bed.
            { "KweebecNightmare_BlightTheme_Tier3", "Track_Portal_Void_Event", "MC_Zone4_Caves", "MC_Zone4_Dark" },
    };

    /** Resolved base dread container id; null = not yet resolved (re-resolved next tick). */
    private static volatile String musicId = null;

    /** Resolved container id per tier; null = not yet resolved (re-resolved next swap). */
    private static final String[] TIER_ID = { null, null, null };

    /** Last tier whose bed was forced for the whole party; -1 = none yet. World-thread only. */
    private static volatile int appliedTier = -1;

    private MusicBedService() {
    }

    /**
     * Force the dread bed for ONE confirmed-present survivor. Call from the per-player
     * arrival path (ChaseMode.lazyPlayerSetup) on the instance world thread with the
     * player's instance-world ref. Returns true once handled (the bed was applied), or
     * false to RETRY next tick (the music asset map is not ready yet, or the ref / player
     * is momentarily unavailable) - never latches a half-applied state. The engine apply is
     * delegated to {@link ForcedMusicService#applyFor}.
     */
    public static boolean applyFor(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        String id = resolveMusic();
        if (id == null) {
            // Unresolved (asset map not ready or no dread container registered). Do NOT latch;
            // return false so the caller retries next tick. Once resolved it caches the id.
            return false;
        }
        boolean applied = ForcedMusicService.applyFor(store, ref, id);
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][music] applyFor id=" + id + " applied=" + applied);
        return applied;
    }

    /**
     * Swap the forced dread bed for the WHOLE party to the {@code tier}'s container, but
     * ONLY when the tier rose since the last swap (so this is cheap to call every tick).
     * Resolves the tier's own candidate ladder ({@link #TIER_MUSIC_CANDIDATES}); if the
     * tier resolves to nothing it keeps the previous bed (never silences the round). The
     * base per-player {@code applyFor} one-shot still seeds tier 0 on arrival; this layers
     * the rising-tier escalation on top. World-thread only (it self-hops via
     * {@code world.execute} like {@link #clear}); fully try-guarded.
     *
     * @param tier the round's current {@code ChaseState.corruptionTier()} (0/1/2)
     */
    public static void applyTierForSurvivors(@Nonnull RoundInstance round, int tier) {
        int clamped = Math.max(0, Math.min(TIER_MUSIC_CANDIDATES.length - 1, tier));
        if (clamped <= appliedTier) {
            return; // unchanged or a (non-escalating) drop - no churn; keep the heavier bed
        }
        String id = resolveTier(clamped);
        if (id == null) {
            // The tier's containers are not registered yet; do not latch, retry next rise.
            return;
        }
        World world = round.world();
        if (world == null) {
            return;
        }
        appliedTier = clamped;
        setForAll(round, world, id);
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][music] tier bed swap -> tier=" + clamped + " id=" + id);
    }

    /** Resolve (and cache) the container id for one tier's candidate ladder. */
    @Nullable
    private static String resolveTier(int tier) {
        String cached = TIER_ID[tier];
        if (cached != null) {
            return cached;
        }
        for (String id : TIER_MUSIC_CANDIDATES[tier]) {
            if (isRegistered(id)) {
                TIER_ID[tier] = id; // cache only a real, registered id
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][music] tier " + tier + " bed resolved '" + id + "'");
                return id;
            }
        }
        return null; // nothing registered yet; re-resolve next rise (do not cache)
    }

    /** Clear the override (index 0) for every player. Call on resolve/exit. */
    public static void clear(@Nonnull RoundInstance round) {
        appliedTier = -1; // reset so the next round's tier ladder starts from tier 0
        World world = round.world();
        if (world == null) {
            return;
        }
        clearForAll(round, world);
    }

    /** Force the resolved {@code containerId} bed on every active player (engine apply delegated to common). */
    private static void setForAll(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull String containerId) {
        try {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRoundState st : round.playerStates()) {
                    Ref<EntityStore> ref = refOf(st);
                    if (ref == null) {
                        continue;
                    }
                    ForcedMusicService.applyFor(store, ref, containerId);
                }
            });
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][music] set failed: " + t.getMessage());
        }
    }

    /** Clear the forced bed (engine clear delegated to common) for every player. */
    private static void clearForAll(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRoundState st : round.playerStates()) {
                    Ref<EntityStore> ref = refOf(st);
                    if (ref == null) {
                        continue;
                    }
                    ForcedMusicService.clearFor(store, ref);
                }
            });
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][music] clear failed: " + t.getMessage());
        }
    }

    /** Resolve a round-player state to its live, valid instance-world ref, or null. */
    @Nullable
    private static Ref<EntityStore> refOf(@Nonnull PlayerRoundState st) {
        PlayerRef pr = Universe.get().getPlayer(st.playerId());
        if (pr == null) {
            return null;
        }
        Ref<EntityStore> ref = pr.getReference();
        return (ref == null || !ref.isValid()) ? null : ref;
    }

    /** Resolve (and cache) the base dread container id; null until a candidate registers. */
    @Nullable
    private static String resolveMusic() {
        String cached = musicId;
        if (cached != null) {
            return cached;
        }
        for (String id : DREAD_MUSIC_CANDIDATES) {
            if (isRegistered(id)) {
                musicId = id; // cache only a real, registered id
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][music] dread bed resolved '" + id + "'");
                return id;
            }
        }
        return null; // nothing registered yet; re-resolve next tick (do not cache)
    }

    /** True if the music container id resolves to a registered asset (a positive index). */
    private static boolean isRegistered(@Nonnull String id) {
        try {
            int idx = MusicContainer.getAssetMap().getIndex(id);
            return idx != Integer.MIN_VALUE && idx > 0;
        } catch (Throwable t) {
            return false; // asset map not ready / id missing
        }
    }
}
