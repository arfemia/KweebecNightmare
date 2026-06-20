package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.instance.play.PlayScreenMessages;
import com.ziggfreed.common.instance.preset.QueueModeId;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed chrome for the Play screen (mode chooser + live roster). */
public final class KweebecPlayScreenMessages implements PlayScreenMessages {

    @Override @Nonnull public Message title() {
        return Lang.msg(Lang.PLAY_TITLE);
    }

    @Override @Nonnull public Message difficulty(@Nonnull Message presetName) {
        // presetName is a NESTED Message so the difficulty name resolves per-locale too.
        return Lang.msg(Lang.PLAY_DIFFICULTY).param("0", presetName);
    }

    @Override @Nonnull public Message modeLabel(@Nonnull QueueModeId mode) {
        return Lang.msg(switch (mode) {
            case PUBLIC -> Lang.MODE_PUBLIC;
            case PARTY -> Lang.MODE_PARTY;
            case SOLO -> Lang.MODE_SOLO;
        });
    }

    @Override @Nonnull public Message modeDesc(@Nonnull QueueModeId mode) {
        return Lang.msg(switch (mode) {
            case PUBLIC -> Lang.MODE_PUBLIC_DESC;
            case PARTY -> Lang.MODE_PARTY_DESC;
            case SOLO -> Lang.MODE_SOLO_DESC;
        });
    }

    @Override @Nonnull public Message playerCount(int size, int maxSize) {
        return Lang.msg(Lang.QUEUE_SCREEN_COUNT).param("0", size).param("1", maxSize);
    }

    @Override @Nonnull public Message statusWaiting() {
        return Lang.msg(Lang.QUEUE_SCREEN_WAITING);
    }

    @Override @Nonnull public Message statusCountdown(int seconds) {
        return Lang.msg(Lang.QUEUE_SCREEN_COUNTDOWN).param("0", seconds);
    }

    @Override @Nonnull public Message statusLaunching() {
        return Lang.msg(Lang.QUEUE_SCREEN_LAUNCHING);
    }

    @Override @Nonnull public Message waitEstimate(int seconds) {
        return Lang.msg(Lang.QUEUE_SCREEN_WAIT).param("0", seconds);
    }

    @Override @Nonnull public Message leaveButton() {
        return Lang.msg(Lang.QUEUE_SCREEN_LEAVE);
    }

    @Override @Nonnull public Message claimButton() {
        return Lang.msg(Lang.PLAY_BTN_CLAIM);
    }

    @Override @Nonnull public Message toastClaimed() {
        return Lang.msg(Lang.PLAY_TOAST_CLAIMED);
    }

    @Override @Nonnull public Message notInQueue() {
        return Lang.msg(Lang.QUEUE_SCREEN_NOT_QUEUED);
    }

    @Override @Nonnull public Message toastQueued(int size, int maxSize) {
        return Lang.msg(Lang.QUEUE_SCREEN_TOAST_QUEUED).param("0", size).param("1", maxSize);
    }
}
