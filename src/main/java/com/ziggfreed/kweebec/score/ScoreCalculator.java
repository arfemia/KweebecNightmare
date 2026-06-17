package com.ziggfreed.kweebec.score;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.api.PlayerScore;

/**
 * Computes a {@link PlayerScore} from a player's raw round inputs and a
 * {@link ScoringConfig}. Pure + deterministic (same inputs -> same score), so it is
 * trivial for a consumer to reason about and stable across versions.
 *
 * <p>Formula: {@code total = max(0, baseline + timeComponent + damageComponent + stunBonus)} where
 * {@code timeComponent = max(0, parTime - duration) * timePointsPerSecond} (faster is better, never
 * negative), {@code damageComponent = max(0, damageBudget - damageTaken) * damagePointsPerHp} (less
 * damage is better, capped at the budget), and {@code stunBonus = mobsStunned * stunBonusPer}.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    @Nonnull
    public static PlayerScore compute(int durationSeconds, float damageTaken, int mobsStunned,
                                      boolean win, @Nonnull ScoringConfig cfg) {
        int safeDuration = Math.max(0, durationSeconds);
        float safeDamage = Math.max(0.0f, damageTaken);
        int safeStuns = Math.max(0, mobsStunned);

        int timeComponent = (int) Math.round(
                Math.max(0, cfg.parTimeSeconds() - safeDuration) * cfg.timePointsPerSecond());
        double avoided = Math.max(0.0, cfg.damageBudget() - safeDamage);
        int damageComponent = (int) Math.round(avoided * cfg.damagePointsPerHp());
        int stunBonus = safeStuns * cfg.stunBonusPer();

        int total = Math.max(0, cfg.baseline() + timeComponent + damageComponent + stunBonus);
        return new PlayerScore(total, timeComponent, damageComponent, stunBonus,
                safeDuration, safeDamage, safeStuns, win);
    }
}
