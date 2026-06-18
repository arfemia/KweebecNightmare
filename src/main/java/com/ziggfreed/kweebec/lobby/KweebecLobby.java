package com.ziggfreed.kweebec.lobby;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.instance.preset.InstancePreset;
import com.ziggfreed.common.instance.preset.InstancePresetConfig;
import com.ziggfreed.common.lobby.GroupJoinResult;
import com.ziggfreed.common.lobby.JoinResult;
import com.ziggfreed.common.lobby.LobbyConfig;
import com.ziggfreed.common.lobby.LobbyService;
import com.ziggfreed.common.lobby.MatchmakingQueue;
import com.ziggfreed.common.lobby.QueueKey;
import com.ziggfreed.common.lobby.QueueMessages;
import com.ziggfreed.common.lobby.RoundLauncher;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * Kweebec's policy wiring over the generic {@code ziggfreed-common} matchmaking
 * queue. It owns ONE {@link LobbyService} and, per preset, builds the
 * {@link LobbyConfig} (min/max party from the resolved {@link RuleSet}, plus
 * kweebec's wait/countdown/solo policy), the {@link RoundLauncher} (a closure over
 * {@code startChase} capturing the preset), the {@code alreadyEngaged} guard
 * ({@code registry::isInRound}), and the {@link QueueMessages} (localized Lang
 * Messages). The queue stays preset-id-string-typed; everything kweebec-specific
 * lives here, nothing leaks into common.
 *
 * <p>One global queue per preset id ({@code QueueKey("kweebec", presetId)}); kweebec
 * runs a single overworld so a per-world scope is unnecessary in v1.
 */
public final class KweebecLobby {

    /** The lobby game id (the {@link QueueKey} namespace). */
    public static final String GAME_ID = "kweebec";

    // Queue policy (preset-agnostic): how long to gather, the launch countdown, and that
    // a lone player launches when the fill window expires (the user's allowSolo choice).
    private static final int FILL_TIMEOUT_SECONDS = 20;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final boolean ALLOW_SOLO = true;
    private static final boolean LEADER_FORCE_START = false;

    private static final LobbyService SERVICE = new LobbyService();

    /** Membership-truth guard the queue defers to (never duplicated): a live-round check. */
    private static final Predicate<UUID> ALREADY_ENGAGED =
            uuid -> RoundService.getInstance().registry().isInRound(uuid);

    private KweebecLobby() {
    }

    /** Touch the service so its daemon scheduler exists; called from plugin setup. */
    public static void init() {
        KweebecNightmarePlugin.LOGGER.atInfo().log("[Kweebec] matchmaking lobby ready.");
    }

    /** Stop the lobby's daemon scheduler (plugin shutdown). */
    public static void shutdown() {
        SERVICE.shutdown();
    }

    /** The underlying generic lobby service (read access for a UI queue screen). */
    @Nonnull
    public static LobbyService service() {
        return SERVICE;
    }

    /** Queue {@code uuid} for {@code presetId} (defaulted + normalized). */
    @Nonnull
    public static JoinResult join(@Nonnull UUID uuid, @Nullable String presetId) {
        return queue(keyFor(presetId)).join(uuid);
    }

    /** Queue a whole party as a unit for {@code presetId} (public queue; backfills with strangers). */
    @Nonnull
    public static GroupJoinResult queueParty(@Nonnull List<UUID> members, @Nonnull UUID owner,
                                             @Nullable String presetId) {
        QueueKey key = keyFor(presetId);
        String preset = key.presetId();
        return SERVICE.queueParty(key, members, owner, configFor(preset), launcherFor(preset),
                ALREADY_ENGAGED, messagesFor(preset));
    }

    /** The (get-or-create) queue for {@code key}. */
    @Nonnull
    public static MatchmakingQueue queue(@Nonnull QueueKey key) {
        String presetId = key.presetId();
        return SERVICE.queue(key, configFor(presetId), launcherFor(presetId), ALREADY_ENGAGED, messagesFor(presetId));
    }

    /** The queue key for a preset id (blank/unknown -> the default preset). */
    @Nonnull
    public static QueueKey keyFor(@Nullable String presetId) {
        String preset = (presetId == null || presetId.isBlank()) ? PresetConfig.DEFAULT : presetId;
        return new QueueKey(GAME_ID, preset);
    }

    /** True if {@code uuid} is sitting in any kweebec queue. */
    public static boolean isQueued(@Nonnull UUID uuid) {
        return SERVICE.isQueued(uuid);
    }

    /** Remove {@code uuid} from whatever queue it is in. False if it was not queued. */
    public static boolean leave(@Nonnull UUID uuid) {
        return SERVICE.leave(uuid);
    }

    // ==================== per-preset seam builders ====================

    @Nonnull
    private static LobbyConfig configFor(@Nonnull String presetId) {
        RuleSet rs = PresetConfig.getInstance().resolve(presetId);
        // Queue policy is asset-driven when an InstancePreset is authored for this id;
        // min/max party stay on the gameplay RuleSet (the arena-budget clamp authority).
        InstancePreset ip = InstancePresetConfig.getInstance().resolve(presetId);
        if (ip != null) {
            return ip.toLobbyConfig(rs.minParty(), rs.maxParty());
        }
        return new LobbyConfig(rs.minParty(), rs.maxParty(),
                FILL_TIMEOUT_SECONDS, COUNTDOWN_SECONDS, ALLOW_SOLO, LEADER_FORCE_START);
    }

    /** A launcher that hands the snapshotted party to {@code startChase} for THIS preset. */
    @Nonnull
    private static RoundLauncher launcherFor(@Nonnull String presetId) {
        return (initiator, party) -> RoundService.getInstance().startChase(initiator, party, presetId);
    }

    /** Localized queue feedback (common stays locale-free; these are pre-built Messages). */
    @Nonnull
    private static QueueMessages messagesFor(@Nonnull String presetId) {
        return new QueueMessages() {
            @Override
            public Message joined(int size, int minParty, int maxParty) {
                return Lang.msg(Lang.QUEUE_JOINED).param("0", size).param("1", maxParty);
            }

            @Override
            public Message left(int size, int minParty, int maxParty) {
                return Lang.msg(Lang.QUEUE_LEFT);
            }

            @Override
            public Message countdownPrimary(int secondsRemaining) {
                return Lang.msg(Lang.QUEUE_COUNTDOWN).param("0", secondsRemaining);
            }

            @Override
            public Message countdownSecondary(int secondsRemaining) {
                return Lang.msg(Lang.QUEUE_COUNTDOWN_SUB);
            }

            @Override
            public Message launching() {
                return Lang.msg(Lang.QUEUE_LAUNCHING);
            }

            @Override
            public Message cancelled() {
                return Lang.msg(Lang.QUEUE_CANCELLED);
            }

            @Override
            public Message launchFailed() {
                return Lang.msg(Lang.QUEUE_FAILED);
            }
        };
    }
}
