package com.ziggfreed.kweebec.round;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.protocol.GameMode;

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
    /** Whether the dread music bed (ForcedMusicTracker index) has been pushed once for this player. */
    private boolean musicApplied;
    /** Last game-mode observed for this player (1 Hz); used to detect a Creative -> Adventure switch. */
    @Nullable
    private GameMode lastGameMode;
    /** Total damage this player has taken this round (scoring input; less = a higher score). */
    private float damageTaken;
    /** How many hunters this player has stunned with a thrown Moonbloom this round (scoring bonus input). */
    private int mobsStunned;
    /** How many Moonbloom charges this player gathered this round (lifetime-stat input). */
    private int moonbloomCollected;
    /** How many shrines this player personally lit this round (lifetime-stat input). */
    private int shrinesLit;

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

    public boolean isMusicApplied() {
        return musicApplied;
    }

    public void setMusicApplied(boolean musicApplied) {
        this.musicApplied = musicApplied;
    }

    @Nullable
    public GameMode lastGameMode() {
        return lastGameMode;
    }

    public void setLastGameMode(@Nullable GameMode lastGameMode) {
        this.lastGameMode = lastGameMode;
    }

    /** Total damage taken this round (scoring input). */
    public float damageTaken() {
        return damageTaken;
    }

    /** Accumulate damage taken (world thread; called from the damage system). Negative deltas are ignored. */
    public void addDamageTaken(float delta) {
        if (delta > 0f) {
            this.damageTaken += delta;
        }
    }

    /** How many hunters this player stunned with a thrown Moonbloom this round. */
    public int mobsStunned() {
        return mobsStunned;
    }

    /** Record one hunter stunned by this player (world thread). */
    public void incrementMobsStunned() {
        this.mobsStunned++;
    }

    /** Moonbloom charges this player gathered this round (lifetime-stat input). */
    public int moonbloomCollected() {
        return moonbloomCollected;
    }

    /** Record gathered Moonbloom charges (world thread; from the harvest hook). Non-positive deltas ignored. */
    public void addMoonbloomCollected(int delta) {
        if (delta > 0) {
            this.moonbloomCollected += delta;
        }
    }

    /** Shrines this player personally lit this round (lifetime-stat input). */
    public int shrinesLit() {
        return shrinesLit;
    }

    /** Record one shrine lit by this player (world thread; from the shrine-submit interaction). */
    public void incrementShrinesLit() {
        this.shrinesLit++;
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
