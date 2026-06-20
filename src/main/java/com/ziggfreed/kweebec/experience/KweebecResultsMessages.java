package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.util.NumberFormatter;
import com.ziggfreed.common.instance.result.ResultKind;
import com.ziggfreed.common.instance.result.ResultsMessages;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed chrome for the results screen. */
public final class KweebecResultsMessages implements ResultsMessages {

    @Override @Nonnull public Message outcomeTitle(@Nonnull ResultKind kind) {
        return Lang.msg(switch (kind) {
            case WIN -> Lang.RESULTS_WIN;
            case LOSS -> Lang.RESULTS_LOSS;
            case DRAW -> Lang.RESULTS_DRAW;
            case ABORT -> Lang.RESULTS_ABORT;
        });
    }

    @Override @Nonnull public Message duration(int seconds) {
        int m = Math.max(0, seconds) / 60;
        int s = Math.max(0, seconds) % 60;
        String t = m + ":" + (s < 10 ? "0" + s : Integer.toString(s));
        return Lang.msg(Lang.RESULTS_DURATION).param("0", t);
    }

    @Override @Nonnull public Message breakdownTitle() {
        return Lang.msg(Lang.RESULTS_BREAKDOWN);
    }

    @Override @Nonnull public Message statsTitle() {
        return Lang.msg(Lang.RESULTS_STATS);
    }

    @Override @Nonnull public Message rewardsTitle() {
        return Lang.msg(Lang.RESULTS_REWARDS);
    }

    @Override @Nonnull public Message noRewards() {
        return Lang.msg(Lang.RESULTS_NO_REWARDS);
    }

    @Override @Nonnull public Message pendingNote() {
        return Lang.msg(Lang.RESULTS_PENDING);
    }

    @Override @Nonnull public Message claimButton() {
        return Lang.msg(Lang.RESULTS_BTN_CLAIM);
    }

    @Override @Nonnull public Message claimedNote() {
        return Lang.msg(Lang.RESULTS_CLAIMED);
    }

    @Override @Nonnull public Message viewLeaderboardButton() {
        return Lang.msg(Lang.RESULTS_BTN_LB);
    }

    @Override @Nonnull public Message playAgainButton() {
        return Lang.msg(Lang.RESULTS_BTN_AGAIN);
    }

    @Override @Nonnull public Message closeButton() {
        return Lang.msg(Lang.RESULTS_BTN_CLOSE);
    }

    @Override @Nonnull public Message teamTotal(long total) {
        return Lang.msg(Lang.RESULTS_TEAM_TOTAL).param("0", NumberFormatter.grouped(total));
    }
}
