package com.ziggfreed.kweebec.hunter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Kweebec's gameplay moments that fire an encounter (extra-spawn) rule - the domain
 * trigger vocabulary the round flow drives and {@code AiHunterController} switches on.
 * Lifted out of the old {@code SpawnRuleAsset.Trigger} when the rule schema moved to
 * ziggfreed-common's {@code EncounterRuleAsset} (whose {@code Trigger} is a free STRING
 * so common stays domain-agnostic); this enum is the kweebec-side resolution of that
 * string, mapped via {@link #fromString} against the common asset's {@code trigger()}.
 */
public enum SpawnTrigger {
    /** Fires once when the hunt (ritual) begins. */
    ROUND_START,
    /** Fires each time a survivor cleanses (lights) a shrine. */
    SHRINE_LIT,
    /** Fires when the corruption tier crosses up to a new tier (0 -> 1, 1 -> 2). */
    CORRUPTION_TIER,
    /** Fires once each tick the round-elapsed time reaches the rule's {@code AtSeconds}. */
    TIME_ELAPSED,
    /** Fires when a survivor is near the gate corridor (the closing-in beat). */
    PLAYER_PROXIMITY;

    /** The trigger chosen when none is authored. */
    public static final SpawnTrigger DEFAULT = ROUND_START;

    /** Resolve a common {@code EncounterRuleAsset.trigger()} string to this enum (default on unknown/blank). */
    @Nonnull
    public static SpawnTrigger fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (SpawnTrigger t : values()) {
            if (t.name().equalsIgnoreCase(s.trim())) {
                return t;
            }
        }
        return DEFAULT;
    }
}
