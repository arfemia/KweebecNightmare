package com.ziggfreed.kweebec.mode.chase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.UUID;

import com.ziggfreed.kweebec.arena.Anchor;

/**
 * One grove shrine: its arena anchor plus relight progress. A shrine is relit by
 * a survivor channelling in place within {@link com.ziggfreed.kweebec.arena.ArenaLayout#INTERACT_RADIUS};
 * channelling pins the player and emits noise the hunter hears. Progress decays
 * when nobody is channelling. Mutated only on the instance world thread.
 */
public final class ShrineState {

    private final int index;
    private final Anchor anchor;

    private boolean lit;
    /** 0..1 relight progress. */
    private double progress;
    @Nullable
    private UUID channeller;
    /** Ritual feedback stage already announced this channel attempt: 0 idle, 1 started, 2 flared. */
    private int feedbackStage;

    public ShrineState(int index, @Nonnull Anchor anchor) {
        this.index = index;
        this.anchor = anchor;
    }

    public int index() {
        return index;
    }

    @Nonnull
    public Anchor anchor() {
        return anchor;
    }

    public boolean isLit() {
        return lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
        if (lit) {
            this.progress = 1.0;
            this.channeller = null;
        }
    }

    /** Ritual feedback stage already announced this channel attempt (0 idle, 1 started, 2 flared). */
    public int feedbackStage() {
        return feedbackStage;
    }

    public void setFeedbackStage(int feedbackStage) {
        this.feedbackStage = feedbackStage;
    }

    public double progress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
    }

    @Nullable
    public UUID channeller() {
        return channeller;
    }

    public void setChanneller(@Nullable UUID channeller) {
        this.channeller = channeller;
    }
}
