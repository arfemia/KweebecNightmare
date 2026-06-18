package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

import com.ziggfreed.common.instance.queue.QueueScreenMessages;
import com.ziggfreed.kweebec.i18n.Lang;

/** Lang-backed chrome for the queue / ready screen. */
public final class KweebecQueueScreenMessages implements QueueScreenMessages {

    @Override @Nonnull public Message title() {
        return Lang.msg(Lang.QUEUE_SCREEN_TITLE);
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

    @Override @Nonnull public Message refreshButton() {
        return Lang.msg(Lang.QUEUE_SCREEN_REFRESH);
    }

    @Override @Nonnull public Message notInQueue() {
        return Lang.msg(Lang.QUEUE_SCREEN_NOT_QUEUED);
    }
}
