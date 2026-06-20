package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Who must be standing on the extraction pad for the co-op escape to complete - a
 * {@link RuleSet} stake so an installed MMO Skill Tree (and each difficulty preset)
 * can pick the feel. The Chase escape is no longer a single survivor reaching the
 * exit; the whole required group must hold the Heartwood platform together for
 * {@link RuleSet#extractionHoldSeconds()} (see {@code mode/chase/ChaseMode}).
 */
public enum ExtractionMode {

    /**
     * Every MOBILE survivor (active: not cocooned, not escaped, not left) must be on the pad.
     * A cocooned teammate does NOT block - the team can extract and a downed player is pulled
     * out with them (a personal loss for that player). No soft-lock is possible.
     */
    ALL_MOBILE,

    /**
     * Leave no one behind: every survivor still able to take part must be on the pad. A cocooned
     * but still-rescuable teammate counts toward the requirement (so they BLOCK extraction until a
     * teammate revives them and they reach the pad); a PERMANENTLY cocooned player (downs exhausted,
     * {@link PlayerRoundState#isReviveAllowed(RuleSet)} false) is excused so it can never soft-lock.
     */
    EVERYONE;

    /** The mode chosen when none is authored (the no-soft-lock co-op baseline). */
    public static final ExtractionMode DEFAULT = ALL_MOBILE;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. */
    @Nonnull
    public static ExtractionMode fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (ExtractionMode v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) {
                return v;
            }
        }
        return DEFAULT;
    }
}
