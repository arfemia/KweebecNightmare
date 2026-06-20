package com.ziggfreed.kweebec.mode.chase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.UUID;

import org.joml.Vector3i;

import com.ziggfreed.kweebec.arena.Anchor;

/**
 * One grove shrine: its arena anchor plus its interactable furnace block. A shrine is
 * cleansed by submitting Moonbloom at its furnace ({@link #blockPos()}): each offer adds to
 * {@link #submitted()}, and at {@code RuleSet.cleanseCost()} the furnace lights ({@link #isLit()},
 * rendered as the block's green-fire "lit" state). Mutated only on the instance world thread.
 *
 * <p>{@code progress}/{@code channeller} are vestigial (the pre-0.4.0 channel-bar relight); the
 * furnace interaction supersedes them and they are kept only so {@code ChaseState.loudestChanneller()}
 * still compiles (it now always returns null, a safe no-channeller path for the hunter).
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

    /**
     * World-block position of this shrine's interactable furnace (the F-target), or null until
     * {@code ArenaBuilder} places it. The cleanse interaction resolves a shrine from the block the
     * player pressed F on via {@link #matchesBlock(int, int, int)}.
     */
    @Nullable
    private Vector3i blockPos;

    /** Moonbloom charges offered at this shrine's furnace so far (lights at {@code RuleSet.cleanseCost()}). */
    private int submitted;

    /**
     * Whether the furnace block has already been switched to its "lit" visual state. Transient render
     * bookkeeping (not the authoritative {@link #isLit()} flag): set when the lit block-state is asserted,
     * cleared when {@code ArenaBuilder} re-places the (default-state) block on a +4s/+9s re-paste so the
     * per-tick reconciler in {@code ChaseMode} re-asserts it.
     */
    private boolean litRendered;

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

    // --- interactable furnace (0.4.0 shrine rework) ---

    /** World-block position of this shrine's furnace, or null until {@code ArenaBuilder} places it. */
    @Nullable
    public Vector3i blockPos() {
        return blockPos;
    }

    public void setBlockPos(@Nonnull Vector3i blockPos) {
        this.blockPos = blockPos;
    }

    /** Whether this shrine's furnace is the block at (x,y,z). False until the block is placed. */
    public boolean matchesBlock(int x, int y, int z) {
        return blockPos != null && blockPos.x() == x && blockPos.y() == y && blockPos.z() == z;
    }

    /**
     * The stable world-map marker id for the shrine furnace at {@code pos}, derived from the block position
     * so the register / discover / lit-swap call sites never drift. Block-unique within a round.
     */
    @Nonnull
    public static String markerPoiId(@Nonnull Vector3i pos) {
        return "kweebec_shrine_" + pos.x() + "_" + pos.y() + "_" + pos.z();
    }

    /** Moonbloom charges offered at this shrine's furnace so far. */
    public int submitted() {
        return submitted;
    }

    /** Add {@code n} (clamped non-negative) offered charges. World-thread only. */
    public void addSubmitted(int n) {
        if (n > 0) {
            this.submitted += n;
        }
    }

    /** Whether the furnace block has already been switched to its lit visual state (render bookkeeping). */
    public boolean litRendered() {
        return litRendered;
    }

    public void setLitRendered(boolean litRendered) {
        this.litRendered = litRendered;
    }
}
