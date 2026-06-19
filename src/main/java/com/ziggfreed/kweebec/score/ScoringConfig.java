package com.ziggfreed.kweebec.score;

import javax.annotation.Nonnull;

/**
 * The configurable weights of the round score - immutable, with the design defaults
 * pre-seeded. A round's per-player score (see {@link ScoreCalculator}) rewards three
 * things the design calls out:
 * <ul>
 *   <li><b>speed</b> - points for every second UNDER {@link #parTimeSeconds()};</li>
 *   <li><b>caution</b> - points for every HP of {@link #damageBudget()} NOT taken;</li>
 *   <li><b>aggression</b> - a small flat {@link #stunBonusPer()} bonus per hunter stunned;</li>
 *   <li><b>devotion</b> - a {@link #shrineBonusPer()} bonus per shrine the player personally lit,
 *       plus a flat {@link #allShrinesBonus()} when the round lit EVERY discovered shrine.</li>
 * </ul>
 *
 * <p>It is the single configurable scoring surface. The runtime tier
 * ({@code KweebecNightmareAPI.overrideScoring} / {@code resolveScoring}) lets an
 * installed MMO Skill Tree (or any external driver) tune the weights without an asset
 * edit; the defaults are the standalone baseline. Built via {@link #builder()} or
 * {@link #DEFAULT}.
 */
public final class ScoringConfig {

    private final int baseline;
    private final int parTimeSeconds;
    private final double timePointsPerSecond;
    private final double damagePointsPerHp;
    private final double damageBudget;
    private final int stunBonusPer;
    private final int shrineBonusPer;
    private final int allShrinesBonus;

    private ScoringConfig(Builder b) {
        this.baseline = b.baseline;
        this.parTimeSeconds = b.parTimeSeconds;
        this.timePointsPerSecond = b.timePointsPerSecond;
        this.damagePointsPerHp = b.damagePointsPerHp;
        this.damageBudget = b.damageBudget;
        this.stunBonusPer = b.stunBonusPer;
        this.shrineBonusPer = b.shrineBonusPer;
        this.allShrinesBonus = b.allShrinesBonus;
    }

    /** The design-default scoring weights. */
    public static final ScoringConfig DEFAULT = builder().build();

    /** Flat points every scored player starts with, so even a slow, battered win scores positive. */
    public int baseline() {
        return baseline;
    }

    /** The target completion time; finishing under it earns time points (over it earns none, never negative). */
    public int parTimeSeconds() {
        return parTimeSeconds;
    }

    /** Points awarded per second the round finishes UNDER {@link #parTimeSeconds()}. */
    public double timePointsPerSecond() {
        return timePointsPerSecond;
    }

    /** Points awarded per HP of {@link #damageBudget()} the player did NOT take. */
    public double damagePointsPerHp() {
        return damagePointsPerHp;
    }

    /** The damage-taken ceiling the caution bonus is measured against; damage beyond it earns no further penalty. */
    public double damageBudget() {
        return damageBudget;
    }

    /** Flat bonus points per hunter the player stunned with a thrown Moonbloom. */
    public int stunBonusPer() {
        return stunBonusPer;
    }

    /** Flat bonus points per shrine the player personally lit (devotion to the objective). */
    public int shrineBonusPer() {
        return shrineBonusPer;
    }

    /** Flat bonus awarded to each scored player when the round lit EVERY discovered shrine (full cleanse). */
    public int allShrinesBonus() {
        return allShrinesBonus;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /** A builder pre-seeded with THIS config's values (the ergonomic runtime-scale seam). */
    @Nonnull
    public Builder toBuilder() {
        Builder b = new Builder();
        b.baseline = this.baseline;
        b.parTimeSeconds = this.parTimeSeconds;
        b.timePointsPerSecond = this.timePointsPerSecond;
        b.damagePointsPerHp = this.damagePointsPerHp;
        b.damageBudget = this.damageBudget;
        b.stunBonusPer = this.stunBonusPer;
        b.shrineBonusPer = this.shrineBonusPer;
        b.allShrinesBonus = this.allShrinesBonus;
        return b;
    }

    /** Fluent builder with the design defaults pre-seeded. */
    public static final class Builder {
        private int baseline = 1000;
        private int parTimeSeconds = 420;          // 7 min target; chase design is ~9 min, 15-min cap
        private double timePointsPerSecond = 5.0;   // +5 / second under par
        private double damagePointsPerHp = 8.0;     // +8 / HP not taken
        private double damageBudget = 200.0;        // measured against 200 HP of damage
        private int stunBonusPer = 50;              // small flat bonus per stun
        private int shrineBonusPer = 75;            // bonus per shrine personally lit (the core objective)
        private int allShrinesBonus = 500;          // flat completion bonus when the round lit every shrine

        private Builder() {
        }

        @Nonnull public Builder baseline(int v) { this.baseline = v; return this; }
        @Nonnull public Builder parTimeSeconds(int v) { this.parTimeSeconds = v; return this; }
        @Nonnull public Builder timePointsPerSecond(double v) { this.timePointsPerSecond = v; return this; }
        @Nonnull public Builder damagePointsPerHp(double v) { this.damagePointsPerHp = v; return this; }
        @Nonnull public Builder damageBudget(double v) { this.damageBudget = v; return this; }
        @Nonnull public Builder stunBonusPer(int v) { this.stunBonusPer = v; return this; }
        @Nonnull public Builder shrineBonusPer(int v) { this.shrineBonusPer = v; return this; }
        @Nonnull public Builder allShrinesBonus(int v) { this.allShrinesBonus = v; return this; }

        @Nonnull
        public ScoringConfig build() {
            return new ScoringConfig(this);
        }
    }
}
