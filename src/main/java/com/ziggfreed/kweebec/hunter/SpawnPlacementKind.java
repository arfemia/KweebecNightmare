package com.ziggfreed.kweebec.hunter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where an encounter (extra-spawn) rule's hunters appear relative to the survivors - the
 * kweebec-side placement vocabulary {@code AiHunterController} maps onto the shared
 * {@code ziggfreed-common} {@code SpawnPlacement} geometry helpers. Lifted out of the old
 * {@code SpawnRuleAsset.Placement} when the rule schema moved to common's
 * {@code EncounterRuleAsset} (whose {@code Placement} is a free STRING); this enum is the
 * kweebec-side resolution of that string, mapped via {@link #fromString} against the
 * common asset's {@code placement()}.
 */
public enum SpawnPlacementKind {
    /** At the fixed hunter den (the historical spawn point). */
    DEN,
    /** In a seeded ring band around ONE randomly chosen active survivor. */
    NEAR_RANDOM_PLAYER,
    /** On a ring around the survivors' centroid (a surrounding wave). */
    RING_AROUND_PLAYERS,
    /** Scattered around the survivors' centroid at varied distances. */
    SCATTER;

    /** The placement chosen when none is authored. */
    public static final SpawnPlacementKind DEFAULT = NEAR_RANDOM_PLAYER;

    /** Resolve a common {@code EncounterRuleAsset.placement()} string to this enum (default on unknown/blank). */
    @Nonnull
    public static SpawnPlacementKind fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (SpawnPlacementKind p : values()) {
            if (p.name().equalsIgnoreCase(s.trim())) {
                return p;
            }
        }
        return DEFAULT;
    }
}
