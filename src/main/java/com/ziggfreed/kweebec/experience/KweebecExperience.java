package com.ziggfreed.kweebec.experience;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.leaderboard.Leaderboard;
import com.ziggfreed.common.instance.leaderboard.LeaderboardBucketTab;
import com.ziggfreed.common.instance.leaderboard.LeaderboardLayout;
import com.ziggfreed.common.instance.leaderboard.LeaderboardLayoutConfig;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPageDeps;
import com.ziggfreed.common.instance.leaderboard.StatColumnDef;
import com.ziggfreed.common.instance.play.PlayModePageDeps;
import com.ziggfreed.common.instance.play.PlayRewardClaim;
import com.ziggfreed.common.instance.preset.InstancePreset;
import com.ziggfreed.common.instance.preset.InstancePresetConfig;
import com.ziggfreed.common.instance.preset.QueueModeSet;
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
import com.ziggfreed.common.instance.reward.LootTable;
import com.ziggfreed.common.instance.reward.LootTableConfig;
import com.ziggfreed.common.instance.reward.PendingRewardStore;
import com.ziggfreed.common.party.PartyConfig;
import com.ziggfreed.common.party.PartyService;
import com.ziggfreed.common.party.PartySettingsConfig;
import com.ziggfreed.common.party.page.PartyPageDeps;
import com.ziggfreed.kweebec.api.PlayerScore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.asset.PresetConfig;
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
    private static final String STAT_WINS = "wins";

    private static Path dataDir;
    private static Leaderboard board;
    private static PartyService partyService;
    private static PendingRewardStore pendingRewards;
    private static LeaderboardPageDeps leaderboardDeps;
    private static ResultsPageDeps resultsDeps;
    private static ResultsPageDeps resultsDepsPreview;
    private static PartyPageDeps partyDeps;
    private static PlayModePageDeps playModeDeps;

    /**
     * Per-player deferred results snapshot: stashed in the instance at resolve, drained on the player's
     * return to the overworld ({@link #openDeferredResults}) to grant rewards + open the full page THERE
     * (never in the instance, where the reward grant would be dropped by the inventory restore and the
     * page closed by the world change). In-memory: a server restart during the ~6s hold loses it
     * (accepted; the leaderboard score is already recorded and rewards re-attempt on next login).
     */
    private static final Map<UUID, PendingResult> pendingResults = new ConcurrentHashMap<>();

    /**
     * Everything needed to rebuild + grant a player's results page once they are back in the overworld,
     * including the CONCRETE rolled spoils ({@code rolledRewards}) - the loot table is rolled once at
     * resolve (where the score is known), so the chip preview shows exactly what the durable claim store
     * will grant. The same list is queued to {@link #pendingRewards}; this copy only feeds the chips.
     */
    private record PendingResult(@Nonnull ResultKind kind, int duration, @Nonnull List<PlayerResultRow> rows,
                                 long teamTotal, int difficultyScore, @Nonnull String bucket,
                                 @Nonnull String presetId, boolean win,
                                 @Nonnull List<InstanceReward> rolledRewards) {
    }

    private KweebecExperience() {
    }

    /**
     * Build the config-independent experience deps (pending rewards, results, play/queue). Call once
     * at plugin setup (after KweebecLobby.init). The leaderboard board + party + leaderboard layout
     * are built LAZILY by {@link #ensureLoaded()} because they read the ziggfreed-common asset configs
     * (LeaderboardLayout / PartySettings), which are only populated by the LoadedAssetsEvent fold AFTER
     * setup() - the hyMMO first-player-connect deferral applied to a minigame.
     */
    public static void init(@Nullable Path dir) {
        dataDir = dir;
        pendingRewards = new PendingRewardStore("pending-rewards");
        pendingRewards.init(dir);
        resultsDeps = new ResultsPageDeps(new KweebecResultsMessages(), new KweebecResultsActions());
        // Button-less (null actions) deps for the in-instance PREVIEW open; the full overworld open uses resultsDeps.
        resultsDepsPreview = new ResultsPageDeps(new KweebecResultsMessages(), null);
        playModeDeps = new PlayModePageDeps(
                KweebecLobby.service(),
                id -> {
                    InstancePreset ip = InstancePresetConfig.getInstance()
                            .resolve(id == null || id.isBlank() ? PresetConfig.DEFAULT : id);
                    return ip != null ? ip.queueModes() : QueueModeSet.fallback();
                },
                id -> Lang.msg(PresetConfig.getInstance()
                        .nameKey(id == null || id.isBlank() ? PresetConfig.DEFAULT : id)),
                Lang::msg,
                new KweebecPlayMode(),
                new KweebecPlayScreenMessages(),
                new KweebecRewardClaim());
        SafeLog.info("[Kweebec] instance-experience layer ready (results + queue eager; leaderboard + party lazy from common pack configs).");
    }

    /**
     * Build the config-dependent deps (board + party + leaderboard layout) once, from the
     * ziggfreed-common asset configs. Idempotent (guarded on {@code board != null}) and synchronized.
     * Called on first access + on first PlayerReady, both of which are AFTER the asset fold, so the
     * configs are populated; if the pack JSON is absent it degrades to the built-in axes
     * ({@link #fallbackLayout()}) so the leaderboard still works.
     */
    private static synchronized void ensureLoaded() {
        if (board != null) {
            return;
        }
        LeaderboardLayout layout = LeaderboardLayoutConfig.getInstance().resolve("chase");
        if (layout == null) {
            layout = fallbackLayout();
        }
        Leaderboard b = new Leaderboard(layout.boardId());
        b.init(dataDir);
        partyService = new PartyService(GAME_ID,
                PartySettingsConfig.getInstance().resolveOrDefault(GAME_ID,
                        new PartyConfig(ARENA_MAX_PARTY, INVITE_TIMEOUT_SECONDS, false)),
                new KweebecPartyMessages());
        leaderboardDeps = new LeaderboardPageDeps(b, layout.primaryTabs(), layout.secondaryTabs(),
                layout.statColumns(), new KweebecLeaderboardScreenMessages());
        partyDeps = new PartyPageDeps(partyService, new KweebecPartyScreenMessages(), KweebecParty::queueParty);
        board = b; // set LAST: it is the ensureLoaded guard.
        SafeLog.info("[Kweebec] leaderboard + party built from common pack configs (board=" + layout.boardId() + ").");
    }

    /** The built-in leaderboard layout used when no pack authored {@code ZiggfreedCommon/Leaderboard/Chase.json}. */
    @Nonnull
    private static LeaderboardLayout fallbackLayout() {
        return new LeaderboardLayout("chase", "leaderboard-chase",
                Lang.msg(Lang.LB_AXIS_DIFFICULTY), Lang.msg(Lang.LB_AXIS_PLAYERS),
                List.of(
                        new LeaderboardBucketTab("amateur", Lang.msg(Lang.PRESET_AMATEUR)),
                        new LeaderboardBucketTab("nightmare", Lang.msg(Lang.PRESET_NIGHTMARE)),
                        new LeaderboardBucketTab("hardcore", Lang.msg(Lang.PRESET_HARDCORE)),
                        new LeaderboardBucketTab("endless", Lang.msg(Lang.PRESET_ENDLESS)),
                        new LeaderboardBucketTab("swarm", Lang.msg(Lang.PRESET_SWARM)),
                        new LeaderboardBucketTab("pitch", Lang.msg(Lang.PRESET_PITCH)),
                        new LeaderboardBucketTab("blitz", Lang.msg(Lang.PRESET_BLITZ))),
                List.of(
                        new LeaderboardBucketTab("1", Lang.msg(Lang.LB_TAB_SOLO)),
                        new LeaderboardBucketTab("2", Lang.msg(Lang.LB_TAB_DUO)),
                        new LeaderboardBucketTab("3", Lang.msg(Lang.LB_TAB_TRIO)),
                        new LeaderboardBucketTab("4", Lang.msg(Lang.LB_TAB_SQUAD))),
                List.of(
                        StatColumnDef.grouped(STAT_STUNNED, Lang.msg(Lang.LB_STAT_STUNNED)),
                        StatColumnDef.grouped(STAT_MOONBLOOM, Lang.msg(Lang.LB_STAT_MOONBLOOM)),
                        StatColumnDef.grouped(STAT_SHRINES, Lang.msg(Lang.LB_STAT_SHRINES)),
                        StatColumnDef.grouped(STAT_WINS, Lang.msg(Lang.LB_STAT_WINS))));
    }

    public static void shutdown() {
        if (partyService != null) {
            partyService.shutdown();
        }
    }

    @Nonnull public static Leaderboard board() {
        ensureLoaded();
        return board;
    }

    @Nonnull public static PartyService partyService() {
        ensureLoaded();
        return partyService;
    }

    @Nonnull public static PendingRewardStore pendingRewards() {
        return pendingRewards;
    }

    @Nonnull public static LeaderboardPageDeps leaderboardDeps() {
        ensureLoaded();
        return leaderboardDeps;
    }

    @Nonnull public static ResultsPageDeps resultsDeps() {
        return resultsDeps;
    }

    @Nonnull public static PartyPageDeps partyDeps() {
        ensureLoaded();
        return partyDeps;
    }

    @Nonnull public static PlayModePageDeps playModeDeps() {
        return playModeDeps;
    }

    /**
     * Record each present player into the (common) leaderboard's party-size bucket. The
     * migration of Kweebec's old {@code score/Leaderboard.record}.
     */
    public static void recordScores(int partySize, @Nonnull RoundInstance round,
                                    @Nonnull Map<UUID, PlayerScore> scores) {
        ensureLoaded();
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
                    STAT_SHRINES, (long) ps.shrinesLit(),
                    STAT_WINS, ps.win() ? 1L : 0L);
            board.record(bucket, st.playerId(), name, ps.total(), ps.durationSeconds(), ps.win(), statDeltas);
        }
    }

    /** The leaderboard bucket key for a round: {@code "<difficulty>_<partySize>"}. */
    @Nonnull
    public static String bucketKey(@Nonnull String presetId, int partySize) {
        return presetId + "_" + partySize;
    }

    /**
     * Build the shared team breakdown, persist the owed spoils to the claim store, stash a per-player
     * {@link PendingResult}, and open the BUTTON-LESS in-instance PREVIEW page for every present player.
     * Runs inside the resolve {@code world.execute} (instance world thread), before the round tears down.
     * NO reward grant here: the full interactive page is deferred to {@link #openResultsPage} on overworld
     * return, where the player CLAIMS the spoils (so nothing is dropped by the inventory restore and the
     * page is not closed by the world change).
     */
    public static void stashResults(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                                    boolean win, int duration, int difficultyScore,
                                    @Nonnull Map<UUID, PlayerScore> scores) {
        ResultKind kind = win ? ResultKind.WIN
                : (outcome == RoundCompletedEvent.Outcome.ABORTED ? ResultKind.ABORT : ResultKind.LOSS);
        // The leaderboard CTA deep-links to the just-played difficulty + party-size bucket.
        String bucket = bucketKey(round.ruleSet().presetId(), round.partySize());
        String presetId = round.ruleSet().presetId();
        String roundId = round.roundId();
        InstancePreset preset = InstancePresetConfig.getInstance().resolve(presetId);
        TeamResult team = buildTeam(round, scores);
        Message previewNote = Lang.msg(Lang.RESULTS_SPOILS_PENDING);

        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            UUID uuid = st.playerId();
            PlayerScore ps = scores.get(uuid);
            int score = ps != null ? ps.total() : 0;
            // Roll the score-tiered spoils ONCE now (the player's score is known here): persist the
            // CONCRETE rolled list to the claim store (NO grant) so it survives disconnect/restart, and
            // stash the same list for the chip preview. Delivered only when the player presses Claim
            // (results page or play-menu button).
            List<InstanceReward> rolled = rollRewards(preset, win, score, seedFor(roundId, uuid));
            if (!rolled.isEmpty()) {
                pendingRewards.queue(uuid, rolled);
            }
            pendingResults.put(uuid, new PendingResult(kind, duration, team.rows(), team.teamTotal(),
                    difficultyScore, bucket, presetId, win, rolled));
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr == null) {
                continue;
            }
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Store<EntityStore> store = ref.getStore();
            // In-instance preview: viewer-flagged rows, NO rewards, NO leaderboard bucket (button-less via
            // resultsDepsPreview's null actions), the "spoils on return" note in place of the reward strip.
            MatchResult preview = new MatchResult(kind, duration,
                    List.of(TeamResult.single(team.teamTotal(), rowsForViewer(team.rows(), uuid))),
                    difficultyScore, List.of(), null);
            try {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().openCustomPage(ref, store,
                            new ResultsPage(pr, preview, resultsDepsPreview, previewNote));
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] failed to open results preview: " + t.getMessage());
            }
        }
    }

    /**
     * Open the full interactive {@link ResultsPage} for a player who is now CLIENT-READY back in the
     * overworld (the deferred page-open, fired from {@link #onPlayerReady} on {@code PlayerReadyEvent}).
     * Shows the run's score breakdown + the run's UNCLAIMED spoils + a Claim button + the leaderboard
     * CTA. NOTHING is granted here - the player claims via the button ({@link #claimPending}). One-shot:
     * the atomic {@code remove} makes a second call a no-op. No snapshot = nothing to do (e.g. a
     * PlayerReadyEvent for the instance entry, or a player who never finished a run).
     */
    public static void openResultsPage(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store) {
        PendingResult pend = pendingResults.remove(pr.getUuid());
        if (pend == null) {
            return;
        }
        // Chips show the CONCRETE rolled spoils stashed at resolve (the same list the durable claim store
        // will grant), so the preview matches the payout exactly even though the table roll is random.
        List<RewardChip> chips = chipsFor(pend.rolledRewards());
        MatchResult result = new MatchResult(pend.kind(), pend.duration(),
                List.of(TeamResult.single(pend.teamTotal(), rowsForViewer(pend.rows(), pr.getUuid()))),
                pend.difficultyScore(), chips, pend.bucket());
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new ResultsPage(pr, result, resultsDeps));
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] failed to open results page: " + t.getMessage());
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
            // Point breakdown (sums to the headline total): Base + the four score components.
            int base = Math.max(0, ps.total() - (ps.timeComponent() + ps.damageComponent()
                    + ps.stunBonus() + ps.shrineBonus() + ps.allShrinesBonus()));
            List<ScoreColumn> pointCols = List.of(
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_BASE), base, ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_TIME), ps.timeComponent(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_DAMAGE), ps.damageComponent(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_STUN), ps.stunBonus(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_SHRINE),
                            ps.shrineBonus() + ps.allShrinesBonus(), ColumnFormat.GROUPED));
            // Run-stats (raw per-run activity): time survived, damage taken, hunters stunned, moonbloom gathered.
            List<ScoreColumn> statCols = List.of(
                    new ScoreColumn(Lang.msg(Lang.RESULTS_COL_DURATION), ps.durationSeconds(), ColumnFormat.TIME),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_STAT_DMG_TAKEN), Math.round(ps.damageTaken()), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_STAT_STUNNED), ps.mobsStunned(), ColumnFormat.GROUPED),
                    new ScoreColumn(Lang.msg(Lang.RESULTS_STAT_MOONBLOOM), ps.moonbloomCollected(), ColumnFormat.GROUPED));
            rows.add(new PlayerResultRow(st.playerId(), ps.total(), pointCols, statCols, false, false));
        }
        List<PlayerResultRow> finalRows = new ArrayList<>(rows.size());
        for (PlayerResultRow r : rows) {
            finalRows.add(new PlayerResultRow(r.uuid(), r.primaryScore(), r.columns(), r.statColumns(),
                    false, r.uuid().equals(mvp)));
        }
        return TeamResult.single(teamTotal, finalRows);
    }

    /** A copy of {@code rows} with the row matching {@code viewer} flagged isViewer (for the per-player open). */
    @Nonnull
    private static List<PlayerResultRow> rowsForViewer(@Nonnull List<PlayerResultRow> rows, @Nonnull UUID viewer) {
        List<PlayerResultRow> out = new ArrayList<>(rows.size());
        for (PlayerResultRow r : rows) {
            out.add(new PlayerResultRow(r.uuid(), r.primaryScore(), r.columns(), r.statColumns(),
                    r.uuid().equals(viewer), r.isMvp()));
        }
        return out;
    }

    /**
     * Roll a preset's owed spoils for a player with {@code score}: empty when the outcome does not grant
     * (win-gated by the preset's reward-on-exit policy), else the preset's flat
     * {@link InstancePreset#rewards()} (backward compat) plus the score-rolled entries of its
     * {@link InstancePreset#rewardTableId() loot table} when one is referenced. Higher score = more rolls
     * + premium entries unlocked (see {@code LootTable.roll}). Deterministic for a given {@code seed}.
     */
    @Nonnull
    private static List<InstanceReward> rollRewards(@Nullable InstancePreset preset, boolean win,
                                                    int score, long seed) {
        if (preset == null || !preset.rewardOnExit().grantsOn(win)) {
            return List.of();
        }
        List<InstanceReward> out = new ArrayList<>(preset.rewards());
        String tableId = preset.rewardTableId();
        if (tableId != null && !tableId.isBlank()) {
            LootTable table = LootTableConfig.getInstance().resolve(tableId);
            if (table != null) {
                out.addAll(table.roll(score, new Random(seed)));
            }
        }
        // Collapse the table's repeated picks (and any flat-reward overlap) into one entry per item,
        // so the claim store grants - and the results chips show - "x9 Moonbloom" once, not four chips.
        return InstanceReward.merge(out);
    }

    /**
     * A deterministic per-(round, player) seed for the loot roll: each player in a round gets their own
     * roll, reproducible for tests, with NO {@code Math.random} (so a resolve is replayable off the round
     * id + player uuid).
     */
    private static long seedFor(@Nonnull String roundId, @Nonnull UUID uuid) {
        return ((long) roundId.hashCode() << 32)
                ^ (uuid.getMostSignificantBits() * 31 + uuid.getLeastSignificantBits());
    }

    /** Build display chips for a concrete rolled spoils list WITHOUT granting (the page's unclaimed preview). */
    @Nonnull
    private static List<RewardChip> chipsFor(@Nonnull List<InstanceReward> rewards) {
        List<RewardChip> chips = new ArrayList<>();
        for (InstanceReward r : rewards) {
            chips.add(toChip(r, false));
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

    /**
     * On client-ready in a world ({@code PlayerReadyEvent} = the reliable post-teleport / post-login
     * signal), open any deferred results page the player earned. A finished run stashes a snapshot at
     * resolve; the FIRST ready event after that (the overworld arrival) opens the full page here. A
     * server-side open at teleport-complete was too early and got dropped by the still-loading client.
     * Rewards are NOT auto-granted - the player claims them from the page (or the play-menu button).
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        // First player connect also warms the lazy leaderboard + party from the common pack configs.
        ensureLoaded();
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
                    openResultsPage(pr, ref, ref.getStore());
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] deferred results open failed: " + t.getMessage());
            }
        });
    }

    /**
     * Claim a player's pending spoils NOW (drain the claim store + grant with the full-inventory guard,
     * re-queuing anything that still does not fit). Returns {@code true} when everything was delivered.
     * Shared by the results-page Claim button and the play-menu Claim button.
     */
    public static boolean claimPending(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store) {
        if (pendingRewards == null || !pendingRewards.has(pr.getUuid())) {
            return true; // nothing to claim
        }
        List<InstanceReward> due = pendingRewards.drain(pr.getUuid());
        GrantOutcome outcome = InstanceRewardGranter.grantAll(due, pr, ref, store, KweebecRewardSink.INSTANCE);
        if (!outcome.pending().isEmpty()) {
            pendingRewards.queue(pr.getUuid(), outcome.pending()); // still no space -> hold again
            return false;
        }
        return true;
    }

    /** Whether the player has unclaimed spoils (drives the play-screen Claim button). */
    public static boolean hasPendingRewards(@Nonnull UUID uuid) {
        return pendingRewards != null && pendingRewards.has(uuid);
    }

    /** Adapts the pending-reward claim to the common play-screen Claim button (hasPending + claim). */
    private static final class KweebecRewardClaim implements PlayRewardClaim {
        @Override public boolean hasPending(@Nonnull UUID uuid) {
            return hasPendingRewards(uuid);
        }

        @Override public boolean claim(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store) {
            return claimPending(player, ref, store);
        }
    }
}
