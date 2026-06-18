package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.instance.leaderboard.LeaderboardScreenMessages;
import com.ziggfreed.common.util.NumberFormatter;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed chrome for the generic leaderboard page (reuses Kweebec's existing LB_* keys). */
public final class KweebecLeaderboardScreenMessages implements LeaderboardScreenMessages {

    @Override @Nonnull public Message title() {
        return Lang.msg(Lang.LB_TITLE);
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

    @Override @Nonnull public Message colTime() {
        return Lang.msg(Lang.LB_COL_TIME);
    }

    @Override @Nonnull public Message colPlays() {
        return Lang.msg(Lang.LB_COL_PLAYS);
    }

    @Override @Nonnull public Message empty() {
        return Lang.msg(Lang.LB_EMPTY);
    }

    @Override @Nonnull public Message yourRank(int rank, long bestScore) {
        return Lang.msg(Lang.LB_YOUR_RANK).param("0", rank).param("1", NumberFormatter.grouped(bestScore));
    }

    @Override @Nonnull public Message yourRankNone() {
        return Lang.msg(Lang.LB_YOUR_RANK_NONE);
    }
}
