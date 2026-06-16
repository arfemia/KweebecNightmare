package com.ziggfreed.kweebec.round;

import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Per-player runtime state inside a round: whether they are cocooned (downed),
 * how many times they have been revived, the bleed-out deadline, and whether
 * they have escaped or left. Mutated only on the instance world thread (the
 * state machine + death/cocoon callbacks all run there).
 */
public final class PlayerRoundState {

    private final UUID playerId;

    private boolean cocooned;
    private boolean escaped;
    private boolean leftRound;
    private int downsUsed;
    /** Epoch ms at which an un-rescued cocoon becomes permanent; 0 = not bleeding out. */
    private long bleedOutDeadlineMs;
    /** 0..1 accrual while a teammate channels this player's rescue; resets when nobody is adjacent. */
    private double rescueProgress;
    /** Consecutive ticks this player was not resolvable in the instance world (disconnect detection). */
    private int missedTicks;
    /** Whether the round's entry game-mode (Adventure) has been applied once for this player. */
    private boolean gameModeApplied;

    public PlayerRoundState(@Nonnull UUID playerId) {
        this.playerId = playerId;
    }

    @Nonnull
    public UUID playerId() {
        return playerId;
    }

    public boolean isCocooned() {
        return cocooned;
    }

    public void setCocooned(boolean cocooned) {
        this.cocooned = cocooned;
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public void setEscaped(boolean escaped) {
        this.escaped = escaped;
    }

    public boolean hasLeftRound() {
        return leftRound;
    }

    public void setLeftRound(boolean leftRound) {
        this.leftRound = leftRound;
    }

    public int downsUsed() {
        return downsUsed;
    }

    public void incrementDowns() {
        this.downsUsed++;
    }

    public long bleedOutDeadlineMs() {
        return bleedOutDeadlineMs;
    }

    public void setBleedOutDeadlineMs(long bleedOutDeadlineMs) {
        this.bleedOutDeadlineMs = bleedOutDeadlineMs;
    }

    public double rescueProgress() {
        return rescueProgress;
    }

    public void setRescueProgress(double rescueProgress) {
        this.rescueProgress = Math.max(0.0, Math.min(1.0, rescueProgress));
    }

    public void addRescueProgress(double delta) {
        setRescueProgress(this.rescueProgress + delta);
    }

    public int missedTicks() {
        return missedTicks;
    }

    public int incrementMissedTicks() {
        return ++missedTicks;
    }

    public void resetMissedTicks() {
        this.missedTicks = 0;
    }

    public boolean isGameModeApplied() {
        return gameModeApplied;
    }

    public void setGameModeApplied(boolean gameModeApplied) {
        this.gameModeApplied = gameModeApplied;
    }

    /**
     * Whether this player can still be revived under the rule-set. Once their
     * down count exceeds {@link RuleSet#maxDowns()} the cocoon is permanent.
     */
    public boolean isReviveAllowed(@Nonnull RuleSet rules) {
        return downsUsed <= rules.maxDowns();
    }

    /** A player is "active" (counts toward an alive party) when present, not cocooned, not escaped. */
    public boolean isActive() {
        return !cocooned && !escaped && !leftRound;
    }
}
