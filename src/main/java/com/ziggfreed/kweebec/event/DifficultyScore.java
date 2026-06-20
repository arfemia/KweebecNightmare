package com.ziggfreed.kweebec.event;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.round.ClashConfig;
import com.ziggfreed.kweebec.round.RespawnPolicy;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * Computes a stable integer difficulty score from a resolved {@link RuleSet}. This
 * is the single number an installed MMO Skill Tree reads off the
 * {@code RoundCompletedEvent} to scale a round's rewards: a harder rule-set (more
 * hunters, a faster speed ceiling, deeper caves, a steeper corruption ramp, more
 * shrines) yields a higher score, so the reward seam can multiply payout by it
 * without re-deriving the knobs itself.
 *
 * <p>The score is a pure, deterministic function of the rule-set knobs - the SAME
 * knobs the {@code defaults < pack < owner < mutator < runtime-scale} fold produces,
 * so a mutator that bumps {@code hunterCount} or a runtime scale that speeds the
 * hunter both raise the score automatically. It is intentionally a flat weighted sum
 * (commutative across the knobs) so it is easy for a consumer to reason about and
 * stable across versions.
 *
 * <p><b>Weights</b> (each chosen so a one-step harder knob moves the score by a
 * meaningful, comparable amount):
 * <ul>
 *   <li>{@code hunterCount * 10} - each extra hunter is a large jump.</li>
 *   <li>{@code (hunterSpeedMax - 1.0) * 40} - the corruption-ramp ceiling; a +0.5
 *       ceiling (e.g. 1.5) adds 20.</li>
 *   <li>{@code caveShrineCount * 5} - each underground descend-and-return objective.</li>
 *   <li>{@code corruptionPerSecond * 1000} - the passive ramp; 0.002/s adds 2.</li>
 *   <li>{@code shrineBase * 3} - the surface-ring base shrine count (party-independent
 *       so the score is stable regardless of who is in the party).</li>
 * </ul>
 * The result is rounded to the nearest int and floored at 0, so a score is never
 * negative even under an aggressive negative-delta mutator stack.
 */
public final class DifficultyScore {

    private static final double HUNTER_COUNT_WEIGHT = 10.0;
    private static final double HUNTER_SPEED_WEIGHT = 40.0;
    private static final double CAVE_SHRINE_WEIGHT = 5.0;
    private static final double CORRUPTION_WEIGHT = 1000.0;
    private static final double SHRINE_BASE_WEIGHT = 3.0;

    private DifficultyScore() {
    }

    /**
     * Compute the difficulty score for a resolved {@link RuleSet}. Pure + stable; see
     * the class javadoc for the weights. Never negative.
     */
    public static int compute(@Nonnull RuleSet rs) {
        double score =
                rs.hunterCount() * HUNTER_COUNT_WEIGHT
                + (rs.hunterSpeedMax() - 1.0) * HUNTER_SPEED_WEIGHT
                + rs.caveShrineCount() * CAVE_SHRINE_WEIGHT
                + rs.corruptionPerSecond() * CORRUPTION_WEIGHT
                + shrineBase(rs) * SHRINE_BASE_WEIGHT;
        return Math.max(0, (int) Math.round(score));
    }

    // PvP (Clash / Domination) weights: the chase knobs are 0/default on a PvP preset, so a PvP round
    // needs its own weighting or its difficultyScore would be ~0 and an MMO would grant ~0 reward.
    private static final double TEAM_SIZE_WEIGHT = 15.0;
    private static final double RESPAWN_NONE_WEIGHT = 30.0;   // single-life is the most intense
    private static final double RESPAWN_LIMITED_WEIGHT = 15.0;
    private static final double RESPAWN_INFINITE_WEIGHT = 5.0;
    private static final double SHORT_ROUND_PIVOT_SECONDS = 300.0; // shorter than this adds intensity

    /**
     * Difficulty score for a PvP (Clash / Domination) rule-set, weighting team size, respawn policy,
     * score cap, and round brevity to a range comparable to {@link #compute(RuleSet)} so an MMO reward
     * multiplier reads both modes consistently. Floored at 1 (never the chase-zero a PvP preset would
     * otherwise produce). See {@link RoundMode#difficultyScore(RuleSet)} which routes PvP rounds here.
     */
    public static int computeClash(@Nonnull RuleSet rs) {
        ClashConfig c = rs.clash();
        double respawn = switch (c.respawnPolicy()) {
            case NONE -> RESPAWN_NONE_WEIGHT;
            case LIMITED -> RESPAWN_LIMITED_WEIGHT;
            case INFINITE -> RESPAWN_INFINITE_WEIGHT;
        };
        double score = rs.teamSize() * TEAM_SIZE_WEIGHT
                + respawn
                + (c.scoreToWin() > 0 ? c.scoreToWin() : 0)
                + Math.max(0.0, (SHORT_ROUND_PIVOT_SECONDS - rs.roundCapSeconds()) / 10.0);
        return Math.max(1, (int) Math.round(score));
    }

    /**
     * Recover the party-independent surface base-shrine count from a {@link RuleSet}.
     * It exposes {@code shrineCount(partySize) = base + perPlayer*max(1,partySize)} but
     * not the raw base; {@code base = shrineCount(1) - (shrineCount(2) - shrineCount(1))}.
     */
    private static int shrineBase(@Nonnull RuleSet rs) {
        int perPlayer = rs.shrineCount(2) - rs.shrineCount(1);
        return rs.shrineCount(1) - perPlayer;
    }
}
