package com.ziggfreed.kweebec.mode.chase;

import java.util.List;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * Chase-mode gameplay state hung off a round instance: shrines, current phase,
 * the corruption meter, and the one-shot ESCAPE flags. Mutated only on the
 * instance world thread by {@link ChaseMode}.
 */
public final class ChaseState {

    private final List<ShrineState> shrines;
    private volatile ChasePhase phase = ChasePhase.PREP;
    /** 0..1 corruption meter; ramps with time + per shrine, drives hunter speed / dark / heartbeat. */
    private volatile double corruption;
    private volatile boolean gateOpen;
    private volatile boolean alertFired;
    /** Epoch ms when PREP ends and the ritual (and hunter) begins. */
    private volatile long prepEndsAtMs;

    public ChaseState(int shrineCount) {
        List<Anchor> anchors = ArenaLayout.shrineAnchors(shrineCount);
        ShrineState[] arr = new ShrineState[anchors.size()];
        for (int i = 0; i < anchors.size(); i++) {
            arr[i] = new ShrineState(i, anchors.get(i));
        }
        this.shrines = List.of(arr);
    }

    @Nonnull
    public List<ShrineState> shrines() {
        return shrines;
    }

    public int totalShrines() {
        return shrines.size();
    }

    public int litShrines() {
        int n = 0;
        for (ShrineState s : shrines) {
            if (s.isLit()) {
                n++;
            }
        }
        return n;
    }

    public boolean allShrinesLit() {
        return litShrines() >= totalShrines();
    }

    @Nonnull
    public ChasePhase phase() {
        return phase;
    }

    public void setPhase(@Nonnull ChasePhase phase) {
        this.phase = phase;
    }

    public double corruption() {
        return corruption;
    }

    public void setCorruption(double corruption) {
        this.corruption = Math.max(0.0, Math.min(1.0, corruption));
    }

    public void addCorruption(double delta) {
        setCorruption(this.corruption + delta);
    }

    public boolean isGateOpen() {
        return gateOpen;
    }

    public void setGateOpen(boolean gateOpen) {
        this.gateOpen = gateOpen;
    }

    public boolean isAlertFired() {
        return alertFired;
    }

    public void setAlertFired(boolean alertFired) {
        this.alertFired = alertFired;
    }

    public long prepEndsAtMs() {
        return prepEndsAtMs;
    }

    public void setPrepEndsAtMs(long prepEndsAtMs) {
        this.prepEndsAtMs = prepEndsAtMs;
    }

    /**
     * Heartbeat / dread tier from corruption: 0 (calm), 1, or 2 (max). Drives the
     * 3-tier proximity heartbeat interval and darkness depth.
     */
    public int corruptionTier() {
        double c = corruption;
        if (c >= 0.67) {
            return 2;
        }
        if (c >= 0.34) {
            return 1;
        }
        return 0;
    }

    /** Convenience: corruption fraction mapped to a hunter speed multiplier. */
    public double hunterSpeed(@Nonnull RuleSet rules) {
        return rules.hunterSpeedAt(corruption);
    }
}
