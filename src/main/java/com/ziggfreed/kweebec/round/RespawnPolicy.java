package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Whether a killed Clash player comes back, and how often - a {@link ClashConfig} stake orthogonal to
 * {@link WinCondition} (so e.g. INFINITE respawn composes with MOST_KILLS, NONE with LAST_TEAM_STANDING).
 * The respawn delay is {@link ClashConfig#respawnDelaySeconds()}.
 */
public enum RespawnPolicy {

    /** A death is permanent (elimination); the player spectates until the round resolves. */
    NONE,

    /** A death respawns the player after the delay, up to {@link ClashConfig#maxLives()} lives, then NONE. */
    LIMITED,

    /** A death always respawns the player after the delay (no elimination is ever reached this way). */
    INFINITE;

    /** The policy chosen when none is authored (single-life elimination, the default Clash feel). */
    public static final RespawnPolicy DEFAULT = NONE;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. */
    @Nonnull
    public static RespawnPolicy fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (RespawnPolicy v : values()) {
            if (v.name().equalsIgnoreCase(s.trim())) {
                return v;
            }
        }
        return DEFAULT;
    }

    /** Whether a player gets at least one respawn under this policy (LIMITED/INFINITE). */
    public boolean respawns() {
        return this != NONE;
    }
}
