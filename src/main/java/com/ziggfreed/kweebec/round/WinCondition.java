package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a PvP (Clash) round is decided - a {@link ClashConfig} stake so each preset (and an installed
 * MMO Skill Tree) picks the feel. Orthogonal to {@link RespawnPolicy}: a win condition says WHAT wins,
 * the respawn policy says WHETHER a death is permanent, so e.g. MOST_KILLS composes with INFINITE
 * respawn and LAST_TEAM_STANDING composes with NONE.
 *
 * <p>The condition maps onto the generic, engine-free
 * {@link com.ziggfreed.common.instance.match.WinConditionResolver} via a {@code MatchRules}: it decides
 * which per-team counter fills the score array, whether elimination is armed, and the score cap. The
 * resolver owns the precedence (team-wipe gate, score cap, timer compare, sudden-death overtime, draw).
 */
public enum WinCondition {

    /** A team is out when all its players are eliminated; the last team standing wins. At the timer with
     *  both teams alive, MOST HITS LANDED is the tiebreak (the default two-stage Clash rule). */
    LAST_TEAM_STANDING,

    /** No elimination win; at the timer the team with the most hits landed wins (tie -> sudden death / draw). */
    MOST_HITS_LANDED,

    /** At the timer the team with the most kills wins; with a score cap, first team to the cap wins early. */
    MOST_KILLS,

    /** Team deathmatch: first team to {@link ClashConfig#scoreToWin()} kills wins; else highest at the timer. */
    TDM_SCORE_TO_KILLS;

    /** The condition chosen when none is authored (the brief's default two-stage Clash rule). */
    public static final WinCondition DEFAULT = LAST_TEAM_STANDING;

    /** Case-insensitive parse; {@code null} / unknown falls back to {@link #DEFAULT}. Accepts the
     *  {@code TDM} alias for {@link #TDM_SCORE_TO_KILLS}. */
    @Nonnull
    public static WinCondition fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        String t = s.trim();
        if (t.equalsIgnoreCase("TDM")) {
            return TDM_SCORE_TO_KILLS;
        }
        for (WinCondition v : values()) {
            if (v.name().equalsIgnoreCase(t)) {
                return v;
            }
        }
        return DEFAULT;
    }

    /** Whether this condition arms the team-wipe elimination gate (a team at 0 eligible loses now). */
    public boolean isElimination() {
        return this == LAST_TEAM_STANDING;
    }
}
