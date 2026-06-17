package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a survivor's inventory is treated for the duration of a round - a per-preset
 * game-mode seam (the replayability pillar's inventory dial).
 *
 * <p><b>DATA ONLY this pass (Phase 1B).</b> This enum is a {@link RoundPreset}
 * schema field + a {@link RuleSet} knob so presets can declare intent and an
 * installed MMO can override it, but NO inventory behavior is wired yet. The actual
 * snapshot / strip / restore mechanism (native gameplay-config vs a Java section
 * snapshot) is still under investigation and lands in Phase 2C. The default,
 * {@link #PRESERVE_AND_STRIP}, is the design's locked default.
 */
public enum InventoryMode {

    /** Bring your overworld gear into the round and keep it (no strip, no restore). */
    KEEP,

    /** Enter empty; the round snapshots and restores your overworld gear on exit. The default. */
    PRESERVE_AND_STRIP,

    /** Enter with a granted round kit; your overworld gear is preserved and restored on exit. */
    KIT;

    /** The mode chosen when none is authored. */
    public static final InventoryMode DEFAULT = PRESERVE_AND_STRIP;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. */
    @Nonnull
    public static InventoryMode fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (InventoryMode m : values()) {
            if (m.name().equalsIgnoreCase(s.trim())) {
                return m;
            }
        }
        return DEFAULT;
    }
}
