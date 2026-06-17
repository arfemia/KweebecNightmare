package com.ziggfreed.kweebec.atmosphere;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.world.UpdateForcedMusic;
import com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Forces a dread MUSIC bed for the round, distinct from the SFX heartbeat.
 *
 * <p><b>Mechanism (verified against the 0.5.3 decompile + 0.5.5 javap):</b> the engine
 * music override is the {@code UpdateForcedMusic} packet (id 151), a single int
 * container index. {@code AudioPlugin} ensures a {@code ForcedMusicTracker} on every
 * player and registers {@code ForcedMusicSystems.Tick}, which sends that packet ONLY
 * when {@code currentContainerIndex} differs from {@code lastSentContainerIndex}. The
 * earlier attempts here just set {@code tracker.setCurrentContainerIndex(idx)} and
 * relied entirely on that Tick system running inside the plugin-spawned INSTANCE world
 * - and were silent. So we no longer depend on it: we set the tracker index (so the
 * engine stays consistent and the exit-clear works) AND push the {@code UpdateForcedMusic}
 * packet DIRECTLY to the player's {@link PlayerRef#getPacketHandler()}, exactly the
 * packet that Tick / {@code /audio music force} send. The direct send works whether or
 * not the Tick system ticks instance worlds. Index 0 clears the override.
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

    /** -1 = unresolved, else the container index (>0). Never caches 0 (a 0 means "retry"). */
    private static volatile int musicIndex = -1;

    /** Resolved container index per tier; -1 = not yet resolved (re-resolved next swap). */
    private static final int[] TIER_INDEX = { -1, -1, -1 };

    /** Last tier whose bed was forced for the whole party; -1 = none yet. World-thread only. */
    private static volatile int appliedTier = -1;

    private MusicBedService() {
    }

    /**
     * Force the dread bed for ONE confirmed-present survivor. Call from the per-player
     * arrival path (ChaseMode.lazyPlayerSetup) on the instance world thread with the
     * player's instance-world ref. Returns true once handled (the packet was sent), or
     * false to RETRY next tick (the music asset map is not ready yet, or the ref / player
     * is momentarily unavailable) - never latches a half-applied state.
     */
    public static boolean applyFor(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        int idx = resolveMusic();
        if (idx <= 0) {
            // Unresolved (asset map not ready or no dread container registered). Do NOT latch;
            // return false so the caller retries next tick. Once resolved it caches the real index.
            return false;
        }
        if (!ref.isValid()) {
            return false;
        }
        try {
            // 1) Set the engine tracker so ForcedMusicSystems.Tick keeps it forced IF it runs
            //    in this instance world, and so clear()-on-exit has a tracker to zero out.
            ForcedMusicTracker tracker = store.ensureAndGetComponent(ref, ForcedMusicTracker.getComponentType());
            boolean haveTracker = tracker != null;
            if (haveTracker) {
                tracker.setCurrentContainerIndex(idx);
            }
            // 2) Push the packet DIRECTLY - the mechanism-independent send (see class javadoc).
            boolean sent = sendForcedMusic(store, ref, idx);
            // If our direct send landed, mark the tracker as already-sent so the engine's
            // ForcedMusicSystems.Tick (if it runs in this instance world) does not emit a duplicate
            // identical packet next tick. If the direct send failed, leave lastSent untouched so that
            // Tick still delivers it (have 0 != desired idx).
            if (sent && haveTracker) {
                tracker.setLastSentContainerIndex(idx);
            }
            KweebecNightmarePlugin.LOGGER.atInfo().log(
                    "[Kweebec][music] applyFor idx=" + idx + " tracker=" + haveTracker + " packetSent=" + sent);
            // Retry next tick if neither lever took (no tracker AND no packet) - otherwise we are done.
            return haveTracker || sent;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec][music] applyFor failed: " + t.getMessage());
            return false;
        }
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
        int idx = resolveTier(clamped);
        if (idx <= 0) {
            // The tier's containers are not registered yet; do not latch, retry next rise.
            return;
        }
        World world = round.world();
        if (world == null) {
            return;
        }
        appliedTier = clamped;
        setForAll(round, world, idx);
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][music] tier bed swap -> tier=" + clamped + " idx=" + idx);
    }

    /** Resolve (and cache) the container index for one tier's candidate ladder. */
    private static int resolveTier(int tier) {
        int cached = TIER_INDEX[tier];
        if (cached != -1) {
            return cached;
        }
        for (String id : TIER_MUSIC_CANDIDATES[tier]) {
            Integer i = tryIndex(id);
            if (i != null && i != Integer.MIN_VALUE && i > 0) {
                TIER_INDEX[tier] = i; // cache only a real index
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][music] tier " + tier + " bed resolved '" + id + "' -> idx " + i);
                return i;
            }
        }
        return 0; // nothing registered yet; re-resolve next rise (do not cache)
    }

    /** Clear the override (index 0) for every player. Call on resolve/exit. */
    public static void clear(@Nonnull RoundInstance round) {
        appliedTier = -1; // reset so the next round's tier ladder starts from tier 0
        World world = round.world();
        if (world == null) {
            return;
        }
        setForAll(round, world, 0);
    }

    private static void setForAll(@Nonnull RoundInstance round, @Nonnull World world, int index) {
        try {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRoundState st : round.playerStates()) {
                    UUID uuid = st.playerId();
                    PlayerRef pr = Universe.get().getPlayer(uuid);
                    if (pr == null) {
                        continue;
                    }
                    Ref<EntityStore> ref = pr.getReference();
                    if (ref == null || !ref.isValid()) {
                        continue;
                    }
                    ForcedMusicTracker tracker = store.getComponent(ref, ForcedMusicTracker.getComponentType());
                    if (tracker != null) {
                        tracker.setCurrentContainerIndex(index);
                    }
                    sendForcedMusic(store, ref, index);
                }
            });
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec][music] set failed: " + t.getMessage());
        }
    }

    /** Write the UpdateForcedMusic packet to the player's handler. Returns true if sent. */
    private static boolean sendForcedMusic(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, int index) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return false;
        }
        pr.getPacketHandler().write(new UpdateForcedMusic(index));
        return true;
    }

    private static int resolveMusic() {
        int cached = musicIndex;
        if (cached != -1) {
            return cached;
        }
        for (String id : DREAD_MUSIC_CANDIDATES) {
            Integer i = tryIndex(id);
            if (i != null && i != Integer.MIN_VALUE && i > 0) {
                musicIndex = i; // cache only a real index
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][music] dread bed resolved '" + id + "' -> idx " + i);
                return i;
            }
        }
        return 0; // nothing registered yet; re-resolve next tick (do not cache)
    }

    /** Asset-map lookup that returns null if the map is not ready (vs MIN_VALUE for a missing id). */
    @Nullable
    private static Integer tryIndex(@Nonnull String id) {
        try {
            return MusicContainer.getAssetMap().getIndex(id);
        } catch (Throwable t) {
            return null;
        }
    }
}
