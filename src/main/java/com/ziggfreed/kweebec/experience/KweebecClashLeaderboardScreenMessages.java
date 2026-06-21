package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.instance.leaderboard.LeaderboardScreenMessages;
import com.ziggfreed.common.util.NumberFormatter;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * Lang-backed chrome for the generic leaderboard page when it shows the PvP (Clash + Domination)
 * board. Mirrors {@link KweebecLeaderboardScreenMessages} but names the PvP axes (Mode / players)
 * and reuses Kweebec's shared LB_* column / sort / view / footer keys (those are mode-agnostic).
 */
public final class KweebecClashLeaderboardScreenMessages implements LeaderboardScreenMessages {

    @Override @Nonnull public Message title() {
        return Lang.msg(Lang.LB_CLASH_TITLE);
    }

    @Override @Nonnull public Message primaryAxisLabel() {
        return Lang.msg(Lang.LB_CLASH_AXIS_MODE);
    }

    @Override @Nonnull public Message sortLabel() {
        return Lang.msg(Lang.LB_AXIS_SORT);
    }

    @Override @Nonnull public Message viewLabel() {
        return Lang.msg(Lang.LB_AXIS_VIEW);
    }

    @Override @Nonnull public Message colRank() {
        return Lang.msg(Lang.LB_COL_RANK);
    }

    @Override @Nonnull public Message colPlayer() {
        return Lang.msg(Lang.LB_COL_PLAYER);
    }

    @Override @Nonnull public Message colScore() {
        return Lang.msg(Lang.LB_COL_SCORE);
    }

    @Override @Nonnull public Message colTotal() {
        return Lang.msg(Lang.LB_COL_TOTAL);
    }

    @Override @Nonnull public Message colTime() {
        return Lang.msg(Lang.LB_COL_TIME);
    }

    @Override @Nonnull public Message colPlays() {
        return Lang.msg(Lang.LB_COL_PLAYS);
    }

    @Override @Nonnull public Message empty() {
        return Lang.msg(Lang.LB_EMPTY);
    }

    @Override @Nonnull public Message sortScore() {
        return Lang.msg(Lang.LB_SORT_SCORE);
    }

    @Override @Nonnull public Message sortTotal() {
        return Lang.msg(Lang.LB_SORT_TOTAL);
    }

    @Override @Nonnull public Message sortTime() {
        return Lang.msg(Lang.LB_SORT_TIME);
    }

    @Override @Nonnull public Message viewRankings() {
        return Lang.msg(Lang.LB_VIEW_RANKINGS);
    }

    @Override @Nonnull public Message viewStats() {
        return Lang.msg(Lang.LB_VIEW_STATS);
    }

    @Override @Nonnull public Message filterAll() {
        return Lang.msg(Lang.LB_FILTER_ALL);
    }

    @Override @Nonnull public Message yourRank(int rank, long bestScore) {
        return Lang.msg(Lang.LB_YOUR_RANK).param("0", rank).param("1", NumberFormatter.grouped(bestScore));
    }

    @Override @Nonnull public Message yourRankNone() {
        return Lang.msg(Lang.LB_YOUR_RANK_NONE);
    }
}
