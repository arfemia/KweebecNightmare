package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a survivor's inventory is treated for the duration of a round - a per-preset
 * game-mode seam (the replayability pillar's inventory dial).
 *
 * <p><b>Wired via {@code RoundInventoryGuard} over ziggfreed-common's {@code
 * InventorySnapshotStore}.</b> On entry the round captures + persists + strips the
 * survivor's full inventory (slot-exact, durability + metadata preserved); on exit it
 * restores the exact entry state and drops any loot gained in-round. The snapshot is
 * persisted before the live inventory is touched and re-applied on the next login, so a
 * crash / disconnect / restart mid-round never eats gear. {@link #KEEP} skips all of this.
 * The default, {@link #PRESERVE_AND_STRIP}, is the design's locked default.
 */
public enum InventoryMode {

    /** Bring your overworld gear into the round and keep it (no strip, no restore). */
    KEEP,

    /** Enter empty; the round snapshots and restores your overworld gear on exit. The default. */
    PRESERVE_AND_STRIP,

    /**
     * Enter with a granted round kit; your overworld gear is preserved and restored on exit. The
     * preserve/strip half behaves exactly like {@link #PRESERVE_AND_STRIP}; the kit grant itself is
     * not authored yet, so until a kit is defined this mode strips like the default.
     */
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
