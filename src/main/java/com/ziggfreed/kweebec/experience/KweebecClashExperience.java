package com.ziggfreed.kweebec.experience;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.ziggfreed.common.instance.result.ColumnFormat;
import com.ziggfreed.common.instance.result.MatchResult;
import com.ziggfreed.common.instance.result.PlayerResultRow;
import com.ziggfreed.common.instance.result.ResultKind;
import com.ziggfreed.common.instance.result.ResultsPage;
import com.ziggfreed.common.instance.result.ResultsPageDeps;
import com.ziggfreed.common.instance.result.ScoreColumn;
import com.ziggfreed.common.instance.result.TeamResult;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.mode.clash.ClashState;
import com.ziggfreed.kweebec.mode.domination.DominationState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Kweebec's PvP (Clash + Domination) consumer wiring over the {@code ziggfreed-common}
 * instance-experience layer, the team-shaped twin of the co-op {@link KweebecExperience}: it owns the
 * (common) PvP {@link Leaderboard} ("leaderboard-clash") + its page deps, builds the TWO-team
 * {@link MatchResult} at round resolve, records each present player into the per-preset leaderboard
 * bucket, and opens the {@link ResultsPage} (button-less preview in the instance, full interactive page
 * on overworld return). It reuses the SERVER-GLOBAL {@link KweebecExperience#partyService()} +
 * {@link KweebecExperience#pendingRewards()} singletons rather than building a second copy.
 *
 * <p>PvP buckets key on the PRESET ID alone ({@code clash_1v1} / {@code clash_2v2} /
 * {@code domination_koth}) - team size is already encoded in the id, so the co-op
 * {@code "<difficulty>_<partySize>"} composition does NOT apply here.
 */
public final class KweebecClashExperience {

    private static final String LAYOUT_ID = "clash";
    private static final String BOARD_ID = "leaderboard-clash";

    /**
     * Fixed leaderboard stat keys (shared by the recorded deltas and the Stats-view columns).
     * The three counters available on {@link ClashState.PlayerClash}; Domination records none of them
     * (it has no per-player combat counters), recording win + score only.
     */
    private static final String STAT_HITS = "hits";
    private static final String STAT_KILLS = "kills";
    private static final String STAT_KOS = "kos";

    private static Path dataDir;
    private static Leaderboard board;
    private static LeaderboardPageDeps leaderboardDeps;
    private static ResultsPageDeps resultsDeps;
    private static ResultsPageDeps resultsDepsPreview;

    /**
     * Per-player deferred PvP results snapshot, stashed at resolve and drained on the player's return to
     * the overworld ({@link #onPlayerReady}) - identical lifecycle to {@link KweebecExperience}'s
     * pending-results map (a separate map so a Chase result and a PvP result never collide for one uuid;
     * the last-resolved round wins, which is correct since a player is only ever in one round at a time).
     */
    private static final Map<UUID, PendingResult> pendingResults = new ConcurrentHashMap<>();

    /** Everything needed to rebuild a player's two-team PvP results page once back in the overworld. */
    private record PendingResult(@Nonnull ResultKind kind, int duration, @Nonnull List<TeamResult> teams,
                                 int difficultyScore, @Nonnull String bucket) {
    }

    private KweebecClashExperience() {
    }

    /**
     * Build the config-independent deps (results, preview). Call once at plugin setup. The leaderboard
     * board + layout are built LAZILY by {@link #ensureLoaded()} (the common pack configs are only
     * populated by the LoadedAssetsEvent fold AFTER setup), exactly like {@link KweebecExperience#init}.
     */
    public static void init(@Nullable Path dir) {
        dataDir = dir;
        resultsDeps = new ResultsPageDeps(new KweebecClashResultsMessages(), new KweebecClashResultsActions());
        // Button-less (null actions) deps for the in-instance PREVIEW open; the overworld open uses resultsDeps.
        resultsDepsPreview = new ResultsPageDeps(new KweebecClashResultsMessages(), null);
        SafeLog.info("[Kweebec] PvP instance-experience layer ready (results eager; leaderboard lazy from common pack configs).");
    }

    /**
     * Build the PvP board + leaderboard layout once, from the ziggfreed-common asset configs. Idempotent
     * (guarded on {@code board != null}) and synchronized; called on first access + on first PlayerReady,
     * both AFTER the asset fold. If {@code ZiggfreedCommon/Leaderboard/Clash.json} is absent it degrades to
     * {@link #fallbackLayout()} so the board still works.
     */
    private static synchronized void ensureLoaded() {
        if (board != null) {
            return;
        }
        LeaderboardLayout layout = LeaderboardLayoutConfig.getInstance().resolve(LAYOUT_ID);
        if (layout == null) {
            layout = fallbackLayout();
        }
        Leaderboard b = new Leaderboard(layout.boardId());
        b.init(dataDir);
        leaderboardDeps = new LeaderboardPageDeps(b, KweebecExperience.enabledPrimaryTabs(layout), layout.secondaryTabs(),
                layout.statColumns(), new KweebecClashLeaderboardScreenMessages());
        board = b; // set LAST: it is the ensureLoaded guard.
        SafeLog.info("[Kweebec] PvP leaderboard built from common pack configs (board=" + layout.boardId() + ").");
    }

    /** The built-in PvP layout used when no pack authored {@code ZiggfreedCommon/Leaderboard/Clash.json}. */
    @Nonnull
    private static LeaderboardLayout fallbackLayout() {
        return new LeaderboardLayout(LAYOUT_ID, BOARD_ID,
                Lang.msg(Lang.LB_CLASH_AXIS_MODE), null,
                List.of(
                        new LeaderboardBucketTab("clash_1v1", Lang.msg(Lang.PRESET_CLASH_1V1)),
                        new LeaderboardBucketTab("clash_2v2", Lang.msg(Lang.PRESET_CLASH_2V2)),
                        new LeaderboardBucketTab("domination_koth", Lang.msg(Lang.PRESET_DOMINATION_KOTH))),
                List.of(),
                List.of(
                        StatColumnDef.grouped(STAT_HITS, Lang.msg(Lang.LB_CLASH_STAT_HITS)),
                        StatColumnDef.grouped(STAT_KILLS, Lang.msg(Lang.LB_CLASH_STAT_KILLS)),
                        StatColumnDef.grouped(STAT_KOS, Lang.msg(Lang.LB_CLASH_STAT_KOS))));
    }

    @Nonnull public static Leaderboard board() {
        ensureLoaded();
        return board;
    }

    @Nonnull public static LeaderboardPageDeps leaderboardDeps() {
        ensureLoaded();
        return leaderboardDeps;
    }

    /** The leaderboard bucket key for a PvP round: the preset id (team size is already in the id). */
    @Nonnull
    public static String bucketKey(@Nonnull String presetId) {
        return presetId;
    }

    /**
     * Record a finished PvP round into the leaderboard, stash a per-player results snapshot, and open the
     * button-less in-instance PREVIEW page (when a world/store is supplied). Builds TWO {@link TeamResult}s
     * (team 0 + team 1) from the mode state - Clash reads per-player hits/kills/deaths off {@link ClashState},
     * Domination reads per-team score off {@link DominationState}. Per-player win = (their team == the winning
     * team). Everything is try-guarded: a {@code null} world/store records the leaderboard only and skips the
     * preview. Called from {@code ClashRoundMode.onResolve} / {@code DominationRoundMode.onResolve} on the
     * instance world thread, before the round tears down.
     */
    public static void recordResult(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome,
                                    int duration, int difficultyScore,
                                    @Nullable World world, @Nullable Store<EntityStore> store) {
        try {
            ensureLoaded();
            String presetId = round.ruleSet().presetId();
            String bucket = bucketKey(presetId);
            int winningTeam = winningTeam(round);
            boolean draw = outcome == RoundCompletedEvent.Outcome.DRAW || winningTeam < 0;

            List<TeamResult> teams = new ArrayList<>(2);
            teams.add(buildTeam(round, 0, winningTeam, duration, bucket, draw));
            teams.add(buildTeam(round, 1, winningTeam, duration, bucket, draw));

            ResultKind kind = outcome == RoundCompletedEvent.Outcome.ABORTED ? ResultKind.ABORT : null;

            for (PlayerRoundState st : round.playerStates()) {
                if (st.hasLeftRound()) {
                    continue;
                }
                UUID uuid = st.playerId();
                int team = round.teamOf(uuid);
                ResultKind playerKind = kind != null ? kind
                        : (draw ? ResultKind.DRAW : (team == winningTeam ? ResultKind.WIN : ResultKind.LOSS));
                pendingResults.put(uuid, new PendingResult(playerKind, duration, teams, difficultyScore, bucket));
                if (world == null || store == null) {
                    continue;
                }
                openPreview(uuid, playerKind, duration, teams, difficultyScore, store);
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] PvP recordResult failed: " + t.getMessage());
        }
    }

    /** The 0-based winning team for the round's mode (Clash {@link ClashState} / Domination {@link DominationState}); -1 = draw/none. */
    private static int winningTeam(@Nonnull RoundInstance round) {
        Object ms = round.modeState();
        if (ms instanceof ClashState cs) {
            return cs.winningTeam();
        }
        if (ms instanceof DominationState ds) {
            return ds.winningTeam();
        }
        return -1;
    }

    /**
     * Build one team's {@link TeamResult}: each present member's row + the team total, recording each member
     * into the leaderboard bucket (win = team == winningTeam). Clash rows carry per-player hits/kills/KOs;
     * Domination rows carry the shared team score (no per-player combat counters) so the leaderboard still
     * ranks participation + wins.
     */
    @Nonnull
    private static TeamResult buildTeam(@Nonnull RoundInstance round, int team, int winningTeam,
                                        int duration, @Nonnull String bucket, boolean draw) {
        Object ms = round.modeState();
        ClashState clash = ms instanceof ClashState cs ? cs : null;
        DominationState dom = ms instanceof DominationState ds ? ds : null;
        boolean win = !draw && team == winningTeam;
        long teamScore = dom != null ? dom.teamScore(team) : 0L;

        List<PlayerResultRow> rows = new ArrayList<>();
        long mvpBest = Long.MIN_VALUE;
        UUID mvp = null;
        for (UUID uuid : round.membersOfTeam(team)) {
            PlayerRoundState st = round.playerState(uuid);
            if (st == null || st.hasLeftRound()) {
                continue;
            }
            long primary;
            List<ScoreColumn> cols;
            Map<String, Long> statDeltas;
            if (clash != null) {
                ClashState.PlayerClash pc = clash.get(uuid);
                primary = pc.kills * 100L + pc.hits; // headline orders by kills, then hits landed
                cols = List.of(
                        new ScoreColumn(Lang.msg(Lang.CLASH_HUD_TALLY_KILLS), pc.kills, ColumnFormat.GROUPED),
                        new ScoreColumn(Lang.msg(Lang.CLASH_HUD_TALLY_HITS), pc.hits, ColumnFormat.GROUPED),
                        new ScoreColumn(Lang.msg(Lang.RESULTS_CLASH_KOS), pc.deaths, ColumnFormat.GROUPED));
                statDeltas = Map.of(STAT_HITS, (long) pc.hits, STAT_KILLS, (long) pc.kills, STAT_KOS, (long) pc.deaths);
            } else {
                // Domination: the per-player headline IS the team's score (no per-player counters).
                primary = teamScore;
                cols = List.of(new ScoreColumn(Lang.msg(Lang.CLASH_HUD_SCORE), teamScore, ColumnFormat.GROUPED));
                statDeltas = Map.of();
            }
            if (primary > mvpBest) {
                mvpBest = primary;
                mvp = uuid;
            }
            rows.add(new PlayerResultRow(uuid, primary, cols, List.of(), false, false));

            PlayerRef pr = Universe.get().getPlayer(uuid);
            String name = pr != null ? pr.getUsername() : null;
            board.record(bucket, uuid, name, (int) Math.min(Integer.MAX_VALUE, primary), duration, win,
                    statDeltas.isEmpty() ? null : statDeltas);
        }
        // Clash team total = sum of member headlines; Domination = the team's own accrued score (each member's
        // headline IS that shared score, so summing them would multiply by member count - use teamScore directly).
        long total = clash != null ? sumPrimary(rows) : teamScore;
        List<PlayerResultRow> finalRows = new ArrayList<>(rows.size());
        for (PlayerResultRow r : rows) {
            finalRows.add(new PlayerResultRow(r.uuid(), r.primaryScore(), r.columns(), r.statColumns(),
                    false, r.uuid().equals(mvp)));
        }
        return new TeamResult(Integer.toString(team), teamLabel(team), total, win ? 1 : 2, finalRows);
    }

    private static long sumPrimary(@Nonnull List<PlayerResultRow> rows) {
        long sum = 0;
        for (PlayerResultRow r : rows) {
            sum += r.primaryScore();
        }
        return sum;
    }

    /** RED for team 0, BLUE for team 1 (the existing HUD team-name keys). */
    @Nonnull
    private static com.hypixel.hytale.server.core.Message teamLabel(int team) {
        return Lang.msg(team == 0 ? Lang.CLASH_TEAM_RED : Lang.CLASH_TEAM_BLUE);
    }

    /** Open the button-less in-instance preview (viewer-flagged rows, no rewards, no leaderboard CTA). */
    private static void openPreview(@Nonnull UUID uuid, @Nonnull ResultKind kind, int duration,
                                    @Nonnull List<TeamResult> teams, int difficultyScore,
                                    @Nonnull Store<EntityStore> store) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        MatchResult preview = new MatchResult(kind, duration, flagViewer(teams, uuid), difficultyScore,
                List.of(), null);
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new ResultsPage(pr, preview, resultsDepsPreview, Lang.msg(Lang.RESULTS_CLASH_PREVIEW)));
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] failed to open PvP results preview: " + t.getMessage());
        }
    }

    /**
     * On client-ready in a world ({@code PlayerReadyEvent} = the reliable post-teleport / post-login signal),
     * open any deferred PvP results page the player earned (mirrors {@link KweebecExperience#onPlayerReady}).
     * One-shot: the atomic {@code remove} makes a second call a no-op. The plugin owner registers this
     * alongside the co-op {@code KweebecExperience::onPlayerReady}.
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        ensureLoaded(); // first connect also warms the lazy PvP leaderboard from the common pack configs
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
                SafeLog.warn("[Kweebec] deferred PvP results open failed: " + t.getMessage());
            }
        });
    }

    /**
     * Open the full interactive PvP {@link ResultsPage} for a player now client-ready back in the overworld
     * (the deferred open from {@link #onPlayerReady}): the two-team breakdown, the viewer's row highlighted,
     * and the leaderboard CTA deep-linked to the just-played preset bucket. No snapshot = nothing to do.
     */
    public static void openResultsPage(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store) {
        PendingResult pend = pendingResults.remove(pr.getUuid());
        if (pend == null) {
            return;
        }
        MatchResult result = new MatchResult(pend.kind(), pend.duration(), flagViewer(pend.teams(), pr.getUuid()),
                pend.difficultyScore(), List.of(), pend.bucket());
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new ResultsPage(pr, result, resultsDeps));
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] failed to open PvP results page: " + t.getMessage());
        }
    }

    /** A copy of {@code teams} with the row matching {@code viewer} flagged isViewer (per-player open). */
    @Nonnull
    private static List<TeamResult> flagViewer(@Nonnull List<TeamResult> teams, @Nonnull UUID viewer) {
        List<TeamResult> out = new ArrayList<>(teams.size());
        for (TeamResult team : teams) {
            List<PlayerResultRow> rows = new ArrayList<>(team.rows().size());
            for (PlayerResultRow r : team.rows()) {
                rows.add(new PlayerResultRow(r.uuid(), r.primaryScore(), r.columns(), r.statColumns(),
                        r.uuid().equals(viewer), r.isMvp()));
            }
            out.add(new TeamResult(team.teamId(), team.teamLabel(), team.teamTotal(), team.rank(), rows));
        }
        return out;
    }
}
