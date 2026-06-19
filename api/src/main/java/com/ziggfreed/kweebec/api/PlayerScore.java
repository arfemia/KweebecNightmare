package com.ziggfreed.kweebec.api;

import javax.annotation.Nonnull;

/**
 * One player's computed round score plus the components and raw inputs that produced
 * it. Immutable POJO carried per-player on {@link KweebecRoundScoredEvent} (so a
 * consumer can reward the total OR re-weight the components) and recorded on the
 * leaderboard.
 *
 * <p>Lives in the {@code api} module (not the main mod) so the native scored event can
 * carry it across the jar boundary without a circular dependency; the mod's
 * {@code ScoreCalculator} produces it directly.
 */
public final class PlayerScore {

    private final int total;
    private final int timeComponent;
    private final int damageComponent;
    private final int stunBonus;
    private final int durationSeconds;
    private final float damageTaken;
    private final int mobsStunned;
    private final int moonbloomCollected;
    private final int shrinesLit;
    private final boolean win;

    public PlayerScore(int total, int timeComponent, int damageComponent, int stunBonus,
                       int durationSeconds, float damageTaken, int mobsStunned,
                       int moonbloomCollected, int shrinesLit, boolean win) {
        this.total = total;
        this.timeComponent = timeComponent;
        this.damageComponent = damageComponent;
        this.stunBonus = stunBonus;
        this.durationSeconds = durationSeconds;
        this.damageTaken = damageTaken;
        this.mobsStunned = mobsStunned;
        this.moonbloomCollected = moonbloomCollected;
        this.shrinesLit = shrinesLit;
        this.win = win;
    }

    /** The total score (never negative). */
    public int total() {
        return total;
    }

    /** Points from finishing under par time. */
    public int timeComponent() {
        return timeComponent;
    }

    /** Points from damage avoided. */
    public int damageComponent() {
        return damageComponent;
    }

    /** Points from hunters stunned. */
    public int stunBonus() {
        return stunBonus;
    }

    /** Round length in seconds this player experienced. */
    public int durationSeconds() {
        return durationSeconds;
    }

    /** Total damage this player took. */
    public float damageTaken() {
        return damageTaken;
    }

    /** Hunters this player stunned with a thrown Moonbloom. */
    public int mobsStunned() {
        return mobsStunned;
    }

    /** Moonbloom charges this player gathered this round (a lifetime-stat input; not weighted into total). */
    public int moonbloomCollected() {
        return moonbloomCollected;
    }

    /** Shrines this player personally lit this round (a lifetime-stat input; not weighted into total). */
    public int shrinesLit() {
        return shrinesLit;
    }

    /** Whether this player's round ended in a win (escaped / survived). */
    public boolean win() {
        return win;
    }

    @Override
    @Nonnull
    public String toString() {
        return "PlayerScore{total=" + total + ", time=" + timeComponent
                + ", dmg=" + damageComponent + ", stun=" + stunBonus
                + ", dur=" + durationSeconds + "s, dmgTaken=" + damageTaken
                + ", stuns=" + mobsStunned + ", bloom=" + moonbloomCollected
                + ", shrines=" + shrinesLit + ", win=" + win + "}";
    }
}
