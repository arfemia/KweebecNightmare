package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a cocooned (downed) player can return to the round. A descriptor for the
 * stakes a {@link RuleSet} encodes; the concrete numbers (max downs, bleed-out)
 * live on the rule-set so an installed MMO Skill Tree can scale them freely.
 */
public enum ReviveStyle {

    /** A teammate must free the cocoon; generous down count. The default co-op feel. */
    COOP_RESCUE,

    /** Forgiving solo-friendly play: many downs, long bleed-out. */
    FORGIVING,

    /** Brutal: a single catch can be permanent, short or no bleed-out. */
    HARDCORE;

    /** The style chosen when none is authored (the co-op baseline). */
    public static final ReviveStyle DEFAULT = COOP_RESCUE;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. */
    @Nonnull
    public static ReviveStyle fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (ReviveStyle v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) {
                return v;
            }
        }
        return DEFAULT;
    }
}
