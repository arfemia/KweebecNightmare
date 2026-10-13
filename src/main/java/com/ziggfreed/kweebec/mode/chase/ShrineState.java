package com.ziggfreed.kweebec.mode.chase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.ziggfreed.kweebec.arena.Anchor;

/**
 * One grove shrine: its arena anchor plus its interactable furnace block. A shrine is
 * cleansed by submitting Moonbloom at its furnace ({@link #blockPos()}): each offer adds to
 * {@link #submitted()}, and at {@code RuleSet.cleanseCost()} the furnace lights ({@link #isLit()},
 * rendered as the block's green-fire "lit" state). Mutated only on the instance world thread.
 */
public final class ShrineState {

    private final int index;
    private final Anchor anchor;

    private boolean lit;

    /**
     * World-block position of this shrine's interactable furnace (the F-target), or null until
     * {@code ArenaBuilder} places it. The cleanse interaction resolves a shrine from the block the
     * player pressed F on through {@code ChaseState.shrineForBlock}.
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

    public boolean isLit() {
        return lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
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
