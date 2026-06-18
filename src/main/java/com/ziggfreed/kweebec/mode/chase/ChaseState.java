package com.ziggfreed.kweebec.mode.chase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * Chase-mode gameplay state hung off a round instance: shrines, current phase,
 * the corruption meter, and the one-shot ESCAPE flags. Mutated only on the
 * instance world thread by {@link ChaseMode}.
 *
 * <p><b>Shrines are INTERACTION-DISCOVERED (0.4.x rework), not position-tracked.</b> A shrine is any
 * {@code KweebecNightmare_Shrine} furnace BLOCK in the world - baked into a worldgen-placed host prefab
 * (3 surface, deterministic) or a runtime-carved cave shaft (2 cave) = {@link #totalShrines} for normal
 * mode. We never pre-compute shrine positions: the furnace's own {@code Use} RootInteraction fires from
 * the placed block, and {@link #shrineForBlock} lazily registers a {@link ShrineState} keyed by that block
 * position the first time a survivor offers Moonbloom at it. The win is a COUNT ({@link #allShrinesLit});
 * the total is known up front from the deterministic worldgen/carve placement. See
 * [[kweebec-worldgen-both-seams]] (the objective-as-block correction).
 */
public final class ChaseState {

    /** The expected total shrine count this round (3 worldgen surface + cave count). Drives the win denominator. */
    private final int totalShrines;
    /** Lazily-discovered shrines, keyed by furnace block position; created on first interaction. World-thread only. */
    private final Map<Vector3i, ShrineState> discovered = new HashMap<>();
    /** Per-cave resolved surface top-Y (cave anchor index -> Y), so {@code ArenaBuilder}'s +4s/+9s re-carve reuses the same Y instead of re-probing the already-carved surface (which would stack shafts). World-thread only. */
    private final Map<Integer, Integer> caveCarveY = new HashMap<>();
    /** Synthetic ShrineState index base for discovered shrines (kept distinct from any future fixed indices, for logs). */
    private static final int DISCOVERED_INDEX_BASE = 100;

    private volatile ChasePhase phase = ChasePhase.PREP;
    /** 0..1 corruption meter; ramps with time + per shrine, drives hunter speed / dark / heartbeat. */
    private volatile double corruption;
    private volatile boolean gateOpen;
    private volatile boolean alertFired;
    /** Epoch ms when PREP ends and the ritual (and hunter) begins. */
    private volatile long prepEndsAtMs;
    /** How many mid-match Moonbloom respawn waves have already fired (indexes RuleSet.moonbloomRespawnAtSeconds). */
    private int moonbloomRespawnsFired;

    /**
     * Build chase state for a round. {@code totalShrines} is the KNOWN total furnace count
     * ({@code ArenaLayout.SURFACE_WORLDGEN_SHRINES} worldgen surface hosts + {@code RuleSet.caveShrineCount()}
     * runtime-carved caves). No anchors are pre-built: shrines are discovered via their furnace interaction.
     */
    public ChaseState(int totalShrines) {
        this.totalShrines = Math.max(0, totalShrines);
    }

    /**
     * The DISCOVERED shrines (those a survivor has interacted with at least once). Used by the
     * {@code ChaseMode} lit reconciler to re-assert the green-fire block state. An undiscovered shrine
     * furnace is just the authored default-state block in the world - it needs no per-tick work until lit.
     */
    @Nonnull
    public List<ShrineState> shrines() {
        return new ArrayList<>(discovered.values());
    }

    /**
     * The shrine whose furnace is the block at {@code (x,y,z)}, or null if no shrine has been discovered
     * there yet. World-thread only.
     */
    @Nullable
    public ShrineState shrineAt(int x, int y, int z) {
        return discovered.get(new Vector3i(x, y, z));
    }

    /**
     * Resolve (CREATING on first touch) the shrine for the furnace block a survivor pressed F on. The
     * {@code KweebecNightmare_Shrine_Use} RootInteraction only fires on a shrine furnace block, so a block
     * not yet in the map IS a real shrine being discovered - register it. Returns null only once the known
     * {@link #totalShrines} have already been discovered (ignore any unexpected extra furnace). World-thread only.
     */
    @Nullable
    public ShrineState shrineForBlock(int x, int y, int z) {
        Vector3i pos = new Vector3i(x, y, z);
        ShrineState existing = discovered.get(pos);
        if (existing != null) {
            return existing;
        }
        if (discovered.size() >= totalShrines) {
            return null;
        }
        ShrineState s = new ShrineState(DISCOVERED_INDEX_BASE + discovered.size(), new Anchor(x, y, z, 0f));
        s.setBlockPos(pos);
        discovered.put(pos, s);
        return s;
    }

    public int totalShrines() {
        return totalShrines;
    }

    public int litShrines() {
        int n = 0;
        for (ShrineState s : discovered.values()) {
            if (s.isLit()) {
                n++;
            }
        }
        return n;
    }

    public boolean allShrinesLit() {
        return litShrines() >= totalShrines;
    }

    /** The resolved cave surface top-Y for a cave index, or {@code null} if not yet carved. World-thread only. */
    @Nullable
    public Integer caveCarveY(int caveIndex) {
        return caveCarveY.get(caveIndex);
    }

    public void setCaveCarveY(int caveIndex, int topY) {
        caveCarveY.put(caveIndex, topY);
    }

    /**
     * Vestigial (the pre-0.4.0 channel-bar relight): the furnace interaction supersedes channelling, so
     * there is never a channeller. Kept so {@code AiHunterController} still compiles; always null = the
     * hunter's safe no-channeller fallback (nearest-survivor pursuit).
     */
    @Nullable
    public UUID loudestChanneller() {
        return null;
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

    /** How many Moonbloom respawn waves have fired this round. World-thread only. */
    public int moonbloomRespawnsFired() {
        return moonbloomRespawnsFired;
    }

    public void incrementMoonbloomRespawnsFired() {
        this.moonbloomRespawnsFired++;
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
