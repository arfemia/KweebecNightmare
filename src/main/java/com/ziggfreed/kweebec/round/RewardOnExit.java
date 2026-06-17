package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When a round grants its exit reward - a per-preset policy, paired with
 * {@link InventoryMode}.
 *
 * <p><b>DATA ONLY this pass (Phase 1B).</b> This is a {@link RoundPreset} schema
 * field + a {@link RuleSet} knob so presets can declare intent and an installed MMO
 * can override it, but NO reward-granting behavior is wired yet (the reward surface
 * is the outbound native events; the on-exit grant lands in Phase 2C). The default,
 * {@link #ON_WIN}, is the design's locked default.
 */
public enum RewardOnExit {

    /** No exit reward is granted by the minigame itself. */
    NONE,

    /** Grant the exit reward only on a winning exit (escape / survive). The default. */
    ON_WIN,

    /** Grant the exit reward on any exit, win or lose. */
    ALWAYS;

    /** The policy chosen when none is authored. */
    public static final RewardOnExit DEFAULT = ON_WIN;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. */
    @Nonnull
    public static RewardOnExit fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (RewardOnExit r : values()) {
            if (r.name().equalsIgnoreCase(s.trim())) {
                return r;
            }
        }
        return DEFAULT;
    }
}
