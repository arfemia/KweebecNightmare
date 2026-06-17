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
    private Anchor anchor;

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

    /**
     * Re-point this shrine's anchor. Used to floor-snap a CAVE shrine's chamber stand
     * Y to the locally-probed surface once the worldgen terrain rolls (so the
     * underground channel Y-band in {@code ChaseMode} matches the carved chamber).
     * World-thread only.
     */
    public void setAnchor(@Nonnull Anchor anchor) {
        this.anchor = anchor;
    }

    /**
     * The surface top-solid Y a CAVE shaft was first carved from (so the +4s/+9s objective
     * re-pastes reuse the SAME Y instead of re-probing the already-carved surface, which would
     * stack a fresh shaft at a new depth each pass). {@link Integer#MIN_VALUE} = not yet resolved.
     */
    private int caveSurfaceTopY = Integer.MIN_VALUE;

    public int caveSurfaceTopY() {
        return caveSurfaceTopY;
    }

    public void setCaveSurfaceTopY(int caveSurfaceTopY) {
        this.caveSurfaceTopY = caveSurfaceTopY;
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
