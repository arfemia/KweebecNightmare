package com.ziggfreed.kweebec.experience;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.asset.AssetMergeAdapter;
import com.ziggfreed.common.instance.leaderboard.Leaderboard;
import com.ziggfreed.common.instance.leaderboard.LeaderboardBucketTab;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPageDeps;
import com.ziggfreed.common.instance.leaderboard.StatColumnDef;
import com.ziggfreed.common.instance.preset.InstancePreset;
import com.ziggfreed.common.instance.preset.InstancePresetAsset;
import com.ziggfreed.common.instance.preset.InstancePresetConfig;
import com.ziggfreed.common.instance.queue.QueuePageDeps;
import com.ziggfreed.common.instance.result.ColumnFormat;
import com.ziggfreed.common.instance.result.MatchResult;
import com.ziggfreed.common.instance.result.PlayerResultRow;
import com.ziggfreed.common.instance.result.ResultKind;
import com.ziggfreed.common.instance.result.ResultsPage;
import com.ziggfreed.common.instance.result.ResultsPageDeps;
import com.ziggfreed.common.instance.result.RewardChip;
import com.ziggfreed.common.instance.result.RewardChipRenderer;
import com.ziggfreed.common.instance.result.ScoreColumn;
import com.ziggfreed.common.instance.result.TeamResult;
import com.ziggfreed.common.instance.reward.GrantOutcome;
import com.ziggfreed.common.instance.reward.InstanceReward;
import com.ziggfreed.common.instance.reward.InstanceRewardGranter;
import com.ziggfreed.common.instance.reward.PendingRewardStore;
import com.ziggfreed.common.party.PartyConfig;
import com.ziggfreed.common.party.PartyService;
import com.ziggfreed.common.party.page.PartyPageDeps;
import com.ziggfreed.kweebec.api.PlayerScore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Kweebec's consumer wiring over the {@code ziggfreed-common} instance-experience layer:
 * it owns the (common) {@link Leaderboard}, {@link PartyService}, and
 * {@link PendingRewardStore}, builds the page deps once, supplies the locale-free
 * {@code *Messages} from {@link Lang}, and builds + opens the {@link ResultsPage} (with
 * the asset-driven reward grant + the no-claim-with-full-inventory guard) at round
 * resolve. Everything kweebec-specific lives here; nothing leaks into common.
 */
public final class KweebecExperience {

    public static final String GAME_ID = "kweebec";
    private static final int ARENA_MAX_PARTY = 4;
    private static final int INVITE_TIMEOUT_SECONDS = 60;

    /** Lifetime-stat bucket keys (shared by the recorded deltas and the Stats-view columns). */
    private static final String STAT_STUNNED = "stunned";
    private static final String STAT_MOONBLOOM = "moonbloom";
    private static final String STAT_SHRINES = "shrines";

    private static Leaderboard board;
    private static PartyService partyService;
    private static PendingRewardStore pendingRewards;
    private static LeaderboardPageDeps leaderboardDeps;
    private static ResultsPageDeps resultsDeps;
    private static PartyPageDeps partyDeps;
    private static QueuePageDeps queueDeps;

    private KweebecExperience() {
    }

    /** Build + load the experience layer. Call once at plugin setup (after KweebecLobby.init). */
    public static void init(@Nullable Path dataDir) {
        // One board PER game-mode (Survival later adds "leaderboard-survival"); buckets within it
        // are "<difficulty>_<partySize>". The old party-size-only "leaderboard.json" is left orphaned
        // (pre-release; the bucket scheme changed).
        board = new Leaderboard("leaderboard-chase");
        board.init(dataDir);
        pendingRewards = new PendingRewardStore("pending-rewards");
        pendingRewards.init(dataDir);
        partyService = new PartyService(GAME_ID, new PartyConfig(ARENA_MAX_PARTY, INVITE_TIMEOUT_SECONDS, false),
                new KweebecPartyMessages());

        // PRIMARY axis = difficulty (preset ids; labels reuse the preset names), SECONDARY = party size.
        leaderboardDeps = new LeaderboardPageDeps(board,
                List.of(
                        new LeaderboardBucketTab("amateur", Lang.msg(Lang.PRESET_AMATEUR)),
                        new LeaderboardBucketTab("nightmare", Lang.msg(Lang.PRESET_NIGHTMARE)),
                        new LeaderboardBucketTab("hardcore", Lang.msg(Lang.PRESET_HARDCORE))),
                List.of(
                        new LeaderboardBucketTab("1", Lang.msg(Lang.LB_TAB_SOLO)),
                        new LeaderboardBucketTab("2", Lang.msg(Lang.LB_TAB_DUO)),
                        new LeaderboardBucketTab("3", Lang.msg(Lang.LB_TAB_TRIO)),
                        new LeaderboardBucketTab("4", Lang.msg(Lang.LB_TAB_SQUAD))),
                List.of(
                        StatColumnDef.grouped(STAT_STUNNED, Lang.msg(Lang.LB_STAT_STUNNED)),
                        StatColumnDef.grouped(STAT_MOONBLOOM, Lang.msg(Lang.LB_STAT_MOONBLOOM)),
                        StatColumnDef.grouped(STAT_SHRINES, Lang.msg(Lang.LB_STAT_SHRINES))),
                new KweebecLeaderboardScreenMessages());
        resultsDeps = new ResultsPageDeps(new KweebecResultsMessages(), new KweebecResultsActions());
        partyDeps = new PartyPageDeps(partyService, new KweebecPartyScreenMessages(), KweebecParty::queueParty);
        queueDeps = new QueuePageDeps(KweebecLobby.service(), new KweebecQueueScreenMessages());
        SafeLog.info("[Kweebec] instance-experience layer ready (results / party / queue / leaderboard).");
    }

    public static void shutdown() {
        if (partyService != null) {
            partyService.shutdown();
        }
    }

    @Nonnull public static Leaderboard board() {
        return board;
    }

    @Nonnull public static PartyService partyService() {
        return partyService;
    }

    @Nonnull public static PendingRewardStore pendingRewards() {
        return pendingRewards;
    }

    @Nonnull public static LeaderboardPageDeps leaderboardDeps() {
        return leaderboardDeps;
    }

    @Nonnull public static ResultsPageDeps resultsDeps() {
        return resultsDeps;
    }

    @Nonnull public static PartyPageDeps partyDeps() {
        return partyDeps;
    }

    @Nonnull public static QueuePageDeps queueDeps() {
        return queueDeps;
    }

    /** Fold pack-authored {@link InstancePresetAsset}s into the cross-cutting config. */
    public static void onInstanceAssetsLoaded(
            @Nonnull LoadedAssetsEvent<String, InstancePresetAsset, DefaultAssetMap<String, InstancePresetAsset>> ev) {
        InstancePresetConfig.getInstance().mergePackLayer(
                AssetMergeAdapter.layer(ev.getAssetMap(), (id, asset) -> asset.toPreset(id)));
    }

    /**
     * Record each present player into the (common) leaderboard's party-size bucket. The
     * migration of Kweebec's old {@code score/Leaderboard.record}.
     */
    public static void recordScores(int partySize, @Nonnull RoundInstance round,
                                    @Nonnull Map<UUID, PlayerScore> scores) {
        String bucket = bucketKey(round.ruleSet().presetId(), partySize);
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerScore ps = scores.get(st.playerId());
            if (ps == null) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            String name = pr != null ? pr.getUsername() : null;
            Map<String, Long> statDeltas = Map.of(
                    STAT_STUNNED, (long) ps.mobsStunned(),
                    STAT_MOONBLOOM, (long) ps.moonbloomCollected(),
                    STAT_SHRINES, (long) ps.shrinesLit());
            board.record(bucket, st.playerId(), name, ps.total(), ps.durationSeconds(), ps.win(), statDeltas);
        }
    }

    /** The leaderboard bucket key for a round: {@code "<difficulty>_<partySize>"}. */
    @Nonnull
    public static String bucketKey(@Nonnull String presetId, int partySize) {
        return presetId + "_" + partySize;
    }

    /**
     * Build the shared team breakdown, grant the asset-driven rewards (with the
     * full-inventory guard), and open the {@link ResultsPage} for every present player.
     * Runs inside the resolve {@code world.execute} (on the instance world thread), before
     * the round tears down.
     */
    public static void openResults(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                                   boolean win, int duration, int difficultyScore,
                                   @Nonnull Map<UUID, PlayerScore> scores) {
        ResultKind kind = win ? ResultKind.WIN
                : (outcome == RoundCompletedEvent.Outcome.ABORTED ? ResultKind.ABORT : ResultKind.LOSS);
        // The leaderboard CTA deep-links to the just-played difficulty + party-size bucket.
        String bucket = bucketKey(round.ruleSet().presetId(), round.partySize());
        TeamResult team = buildTeam(round, scores);
        InstancePreset preset = InstancePresetConfig.getInstance().resolve(round.ruleSet().presetId());

        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr == null) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            List<RewardChip> chips = grantRewards(preset, win, pr, ref, store);
            MatchResult result = new MatchResult(kind, duration, List.of(team), difficultyScore, chips, bucket);
            try {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store, new ResultsPage(pr, result, resultsDeps));
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] failed to open results page: " + t.getMessage());
            }
        }
    }

    @Nonnull
    private static TeamResult buildTeam(@Nonnull RoundInstance round, @Nonnull Map<UUID, PlayerScore> scores) {
        List<PlayerResultRow> rows = new ArrayList<>();
        long teamTotal = 0;
        int mvpBest = Integer.MIN_VALUE;
        UUID mvp = null;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerScore ps = scores.get(st.playerId());
            if (ps == null) {
                continue;
            }
            teamTotal += ps.total();
            if (ps.total() > mvpBest) {
                mvpBest = ps.total();
                mvp = st.playerId();
            }
            List<ScoreColumn> cols = List.of(
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_TIME), ps.timeComponent(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_DAMAGE), ps.damageComponent(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_STUN), ps.stunBonus(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_DURATION), ps.durationSeconds(), ColumnFormat.TIME));
            rows.add(new PlayerResultRow(st.playerId(), ps.total(), cols, false, false));
        }
        List<PlayerResultRow> finalRows = new ArrayList<>(rows.size());
        for (PlayerResultRow r : rows) {
            finalRows.add(new PlayerResultRow(r.uuid(), r.primaryScore(), r.columns(), false, r.uuid().equals(mvp)));
        }
        return TeamResult.single(teamTotal, finalRows);
    }

    /** Grant the preset's reward list to one player (item-fit guarded), returning the result chips. */
    @Nonnull
    private static List<RewardChip> grantRewards(@Nullable InstancePreset preset, boolean win,
                                                 @Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                                 @Nonnull Store<EntityStore> store) {
        if (preset == null || !preset.rewardOnExit().grantsOn(win) || preset.rewards().isEmpty()) {
            return List.of();
        }
        GrantOutcome outcome = InstanceRewardGranter.grantAll(preset.rewards(), pr, ref, store, KweebecRewardSink.INSTANCE);
        if (!outcome.pending().isEmpty()) {
            pendingRewards.queue(pr.getUuid(), outcome.pending());
        }
        List<RewardChip> chips = new ArrayList<>();
        for (InstanceReward r : preset.rewards()) {
            chips.add(toChip(r, outcome.pending().contains(r)));
        }
        return chips;
    }

    @Nonnull
    private static RewardChip toChip(@Nonnull InstanceReward r, boolean pending) {
        // Common auto-generates the label from the item's own engine display name ("Moonbloom x2"),
        // so a pack author needs no per-reward displayKey; an authored displayKey (optional override)
        // resolves through Lang here.
        return RewardChipRenderer.toChip(r, pending, (key, qty) -> Lang.msg(key).param("0", qty));
    }

    /** On player-ready, re-attempt any rewards that were blocked by a full inventory last time. */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                PlayerRef pr = Universe.get().getPlayer(player.getUuid());
                if (pr != null) {
                    deliverPendingRewards(pr, ref, ref.getStore());
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] pending-reward drain failed: " + t.getMessage());
            }
        });
    }

    /** Re-deliver any rewards that were blocked by a full inventory, on next login (with the same guard). */
    public static void deliverPendingRewards(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                             @Nonnull Store<EntityStore> store) {
        if (pendingRewards == null || !pendingRewards.has(pr.getUuid())) {
            return;
        }
        List<InstanceReward> due = pendingRewards.drain(pr.getUuid());
        GrantOutcome outcome = InstanceRewardGranter.grantAll(due, pr, ref, store, KweebecRewardSink.INSTANCE);
        if (!outcome.pending().isEmpty()) {
            pendingRewards.queue(pr.getUuid(), outcome.pending()); // still no space -> hold again
        }
    }
}
