package com.ziggfreed.kweebec.score;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.api.PlayerScore;

/**
 * Computes a {@link PlayerScore} from a player's raw round inputs and a
 * {@link ScoringConfig}. Pure + deterministic (same inputs -> same score), so it is
 * trivial for a consumer to reason about and stable across versions.
 *
 * <p>Formula: {@code total = max(0, baseline + timeComponent + damageComponent + stunBonus +
 * shrineBonus + allShrinesBonus)} where {@code timeComponent = max(0, parTime - duration) *
 * timePointsPerSecond} (faster is better, never negative), {@code damageComponent = max(0,
 * damageBudget - damageTaken) * damagePointsPerHp} (less damage is better, capped at the budget),
 * {@code stunBonus = mobsStunned * stunBonusPer}, {@code shrineBonus = shrinesLit * shrineBonusPer}
 * (devotion per shrine personally lit), and {@code allShrinesBonus} (a flat completion bonus when
 * the round lit EVERY discovered shrine).
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    @Nonnull
    public static PlayerScore compute(int durationSeconds, float damageTaken, int mobsStunned,
                                      int moonbloomCollected, int shrinesLit, boolean allShrinesLit,
                                      boolean win, @Nonnull ScoringConfig cfg) {
        int safeDuration = Math.max(0, durationSeconds);
        float safeDamage = Math.max(0.0f, damageTaken);
        int safeStuns = Math.max(0, mobsStunned);
        int safeShrines = Math.max(0, shrinesLit);

        int timeComponent = (int) Math.round(
                Math.max(0, cfg.parTimeSeconds() - safeDuration) * cfg.timePointsPerSecond());
        double avoided = Math.max(0.0, cfg.damageBudget() - safeDamage);
        int damageComponent = (int) Math.round(avoided * cfg.damagePointsPerHp());
        int stunBonus = safeStuns * cfg.stunBonusPer();
        int shrineBonus = safeShrines * cfg.shrineBonusPer();
        int allShrinesBonus = allShrinesLit ? cfg.allShrinesBonus() : 0;

        int total = Math.max(0, cfg.baseline() + timeComponent + damageComponent
                + stunBonus + shrineBonus + allShrinesBonus);
        // moonbloomCollected is carried as a lifetime-stat input only (not weighted into total).
        return new PlayerScore(total, timeComponent, damageComponent, stunBonus, shrineBonus,
                allShrinesBonus, safeDuration, safeDamage, safeStuns, Math.max(0, moonbloomCollected),
                safeShrines, win);
    }
}
