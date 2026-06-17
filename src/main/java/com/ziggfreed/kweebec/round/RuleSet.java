package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The configurable stakes of a round - the replayability pillar. Every knob the
 * design calls out (rescue style, lives, bleed-out, hunter count + speed, shrine
 * count, round length, corruption ramp) lives here as an immutable value built
 * once per round from a {@link RoundPreset}. An installed MMO Skill Tree can
 * select a preset or scale these numbers via the public API.
 *
 * <p>Build via {@link #builder(String)}; presets are the canonical sources
 * ({@link RoundPreset#ruleSet()}).
 */
public final class RuleSet {

    private final String presetId;
    private final ReviveStyle reviveStyle;
    private final int maxDowns;
    private final int bleedOutSeconds;
    private final int hunterCount;
    private final double hunterSpeedBase;
    private final double hunterSpeedMax;
    private final int shrineBase;
    private final int shrinePerPlayer;
    private final int caveShrineCount;
    private final int roundCapSeconds;
    private final double corruptionPerSecond;
    private final double corruptionPerShrine;
    private final double shrineRelightSeconds;
    private final InventoryMode inventoryMode;
    private final RewardOnExit rewardOnExit;
    @Nullable private final String hunterArchetype;

    private RuleSet(Builder b) {
        this.presetId = b.presetId;
        this.reviveStyle = b.reviveStyle;
        this.maxDowns = b.maxDowns;
        this.bleedOutSeconds = b.bleedOutSeconds;
        this.hunterCount = b.hunterCount;
        this.hunterSpeedBase = b.hunterSpeedBase;
        this.hunterSpeedMax = b.hunterSpeedMax;
        this.shrineBase = b.shrineBase;
        this.shrinePerPlayer = b.shrinePerPlayer;
        this.caveShrineCount = b.caveShrineCount;
        this.roundCapSeconds = b.roundCapSeconds;
        this.corruptionPerSecond = b.corruptionPerSecond;
        this.corruptionPerShrine = b.corruptionPerShrine;
        this.shrineRelightSeconds = b.shrineRelightSeconds;
        this.inventoryMode = b.inventoryMode;
        this.rewardOnExit = b.rewardOnExit;
        this.hunterArchetype = b.hunterArchetype;
    }

    /** Preset id (e.g. {@code "nightmare"}); used in native events + the preset name lang key. */
    @Nonnull
    public String presetId() {
        return presetId;
    }

    @Nonnull
    public ReviveStyle reviveStyle() {
        return reviveStyle;
    }

    /** Revives a single player may receive before a catch is permanent. {@link Integer#MAX_VALUE} = unlimited. */
    public int maxDowns() {
        return maxDowns;
    }

    /** Seconds a cocooned player has before the cocoon becomes permanent; {@code -1} = no timeout. */
    public int bleedOutSeconds() {
        return bleedOutSeconds;
    }

    public int hunterCount() {
        return hunterCount;
    }

    /** Hunter walk-speed multiplier at zero corruption (best-effort applied to the role). */
    public double hunterSpeedBase() {
        return hunterSpeedBase;
    }

    /** Hunter walk-speed multiplier at full corruption. */
    public double hunterSpeedMax() {
        return hunterSpeedMax;
    }

    /** Hunter speed multiplier for a given corruption fraction (0..1), linearly interpolated. */
    public double hunterSpeedAt(double corruption) {
        double c = Math.max(0.0, Math.min(1.0, corruption));
        return hunterSpeedBase + (hunterSpeedMax - hunterSpeedBase) * c;
    }

    public int roundCapSeconds() {
        return roundCapSeconds;
    }

    public double corruptionPerSecond() {
        return corruptionPerSecond;
    }

    public double corruptionPerShrine() {
        return corruptionPerShrine;
    }

    public double shrineRelightSeconds() {
        return shrineRelightSeconds;
    }

    /** Surface (ring) shrines to relight for a given party size: {@code shrineBase + shrinePerPlayer * partySize}. */
    public int shrineCount(int partySize) {
        return shrineBase + shrinePerPlayer * Math.max(1, partySize);
    }

    /**
     * Number of UNDERGROUND relight shrines added on top of the surface ring (capped to the predefined
     * cave anchors). Each is a descend-and-return objective; {@code ArenaBuilder} alternates the descent
     * style (spiral staircase / ladder). 0 = a pure-surface round.
     */
    public int caveShrineCount() {
        return caveShrineCount;
    }

    /**
     * How a survivor's inventory is treated this round. DATA ONLY this pass
     * (Phase 1B): authored + overridable, but no inventory behavior is wired yet
     * (the snapshot/strip/restore mechanism lands in Phase 2C).
     */
    @Nonnull
    public InventoryMode inventoryMode() {
        return inventoryMode;
    }

    /**
     * When the round's exit reward is granted. DATA ONLY this pass (Phase 1B):
     * authored + overridable, but no reward-granting behavior is wired yet.
     */
    @Nonnull
    public RewardOnExit rewardOnExit() {
        return rewardOnExit;
    }

    /**
     * Id of the {@code HunterArchetypeAsset} the hunter roster draws from, or
     * {@code null} for the built-in baseline. The schema is authored now so
     * Phase-2A hunter variety reads it as data; {@code AiHunterController} does not
     * yet consume it this pass.
     */
    @Nullable
    public String hunterArchetype() {
        return hunterArchetype;
    }

    @Nonnull
    public static Builder builder(@Nonnull String presetId) {
        return new Builder(presetId);
    }

    /**
     * A builder pre-seeded with THIS rule-set's values - the ergonomic seam for the
     * runtime scale tier (an installed MMO's {@code scaleRuleSet} operator can copy
     * the resolved preset and tweak a few knobs, e.g. {@code rs.toBuilder()
     * .hunterCount(rs.hunterCount() + 1).build()}).
     */
    @Nonnull
    public Builder toBuilder() {
        Builder b = new Builder(presetId);
        b.reviveStyle = this.reviveStyle;
        b.maxDowns = this.maxDowns;
        b.bleedOutSeconds = this.bleedOutSeconds;
        b.hunterCount = this.hunterCount;
        b.hunterSpeedBase = this.hunterSpeedBase;
        b.hunterSpeedMax = this.hunterSpeedMax;
        b.shrineBase = this.shrineBase;
        b.shrinePerPlayer = this.shrinePerPlayer;
        b.caveShrineCount = this.caveShrineCount;
        b.roundCapSeconds = this.roundCapSeconds;
        b.corruptionPerSecond = this.corruptionPerSecond;
        b.corruptionPerShrine = this.corruptionPerShrine;
        b.shrineRelightSeconds = this.shrineRelightSeconds;
        b.inventoryMode = this.inventoryMode;
        b.rewardOnExit = this.rewardOnExit;
        b.hunterArchetype = this.hunterArchetype;
        return b;
    }

    /** Fluent builder with the design defaults (the Nightmare baseline) pre-seeded. */
    public static final class Builder {
        private final String presetId;
        private ReviveStyle reviveStyle = ReviveStyle.COOP_RESCUE;
        private int maxDowns = 1;
        private int bleedOutSeconds = 30;
        private int hunterCount = 1;
        private double hunterSpeedBase = 1.0;
        private double hunterSpeedMax = 1.35;
        private int shrineBase = 2;
        private int shrinePerPlayer = 1;
        private int caveShrineCount = 2;
        private int roundCapSeconds = 900;
        private double corruptionPerSecond = 0.0014;
        private double corruptionPerShrine = 0.12;
        private double shrineRelightSeconds = 6.0;
        private InventoryMode inventoryMode = InventoryMode.DEFAULT;
        private RewardOnExit rewardOnExit = RewardOnExit.DEFAULT;
        @Nullable private String hunterArchetype = null;

        private Builder(@Nonnull String presetId) {
            this.presetId = presetId;
        }

        @Nonnull public Builder reviveStyle(@Nonnull ReviveStyle v) { this.reviveStyle = v; return this; }
        @Nonnull public Builder maxDowns(int v) { this.maxDowns = v; return this; }
        @Nonnull public Builder bleedOutSeconds(int v) { this.bleedOutSeconds = v; return this; }
        @Nonnull public Builder hunterCount(int v) { this.hunterCount = v; return this; }
        @Nonnull public Builder hunterSpeed(double base, double max) { this.hunterSpeedBase = base; this.hunterSpeedMax = max; return this; }
        @Nonnull public Builder shrines(int base, int perPlayer) { this.shrineBase = base; this.shrinePerPlayer = perPlayer; return this; }
        @Nonnull public Builder caveShrineCount(int v) { this.caveShrineCount = v; return this; }
        @Nonnull public Builder roundCapSeconds(int v) { this.roundCapSeconds = v; return this; }
        @Nonnull public Builder corruptionPerSecond(double v) { this.corruptionPerSecond = v; return this; }
        @Nonnull public Builder corruptionPerShrine(double v) { this.corruptionPerShrine = v; return this; }
        @Nonnull public Builder shrineRelightSeconds(double v) { this.shrineRelightSeconds = v; return this; }
        @Nonnull public Builder inventoryMode(@Nonnull InventoryMode v) { this.inventoryMode = v; return this; }
        @Nonnull public Builder rewardOnExit(@Nonnull RewardOnExit v) { this.rewardOnExit = v; return this; }
        @Nonnull public Builder hunterArchetype(@Nullable String v) { this.hunterArchetype = v; return this; }

        @Nonnull
        public RuleSet build() {
            return new RuleSet(this);
        }
    }
}
