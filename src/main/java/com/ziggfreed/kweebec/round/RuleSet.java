package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.score.ScoringConfig;

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

    /**
     * The arena budget: the shrine ring + instance are sized for a 1-4 player co-op,
     * so {@link #maxParty()} is clamped here however a preset authors it. The matchmaking
     * queue never seats more than this.
     */
    public static final int ARENA_MAX_PARTY = 4;

    /** The default worldgen biome (WorldStructure) a round generates in - the baseline Nightmare grove. */
    public static final String DEFAULT_WORLD_STRUCTURE = "KweebecNightmare_Grove";

    private final String presetId;
    private final String worldStructure;
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
    private final int cleanseCost;
    private final long stunDurationMs;
    private final ThrowMode throwMode;
    private final int moonbloomPerShrine;
    private final int moonbloomScatter;
    private final int moonbloomRespawnCount;
    private final int[] moonbloomRespawnAtSeconds;
    private final int minParty;
    private final int maxParty;
    private final boolean exitMarker;
    private final boolean bossEnabled;
    @Nullable private final String bossId;
    private final ScoringConfig scoring;
    // On-hit punishment baseline (applies to every hunter unless its archetype overrides a field).
    private final String onHitSlowEffectId;
    private final double onHitSlowSeconds;
    private final double onHitDamageMult;
    private final double onHitDamageFlat;
    private final int onHitStackCap;
    private final double onHitStackWindowSeconds;
    private final double enrageAfterSeconds;
    private final double enrageSpeedMult;
    private final double enrageDamageMult;
    private final double enrageDurationSeconds;
    private final String enrageSoundId;

    private RuleSet(Builder b) {
        this.presetId = b.presetId;
        this.worldStructure = b.worldStructure;
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
        this.cleanseCost = b.cleanseCost;
        this.stunDurationMs = b.stunDurationMs;
        this.throwMode = b.throwMode;
        this.moonbloomPerShrine = b.moonbloomPerShrine;
        this.moonbloomScatter = b.moonbloomScatter;
        this.moonbloomRespawnCount = b.moonbloomRespawnCount;
        this.moonbloomRespawnAtSeconds = b.moonbloomRespawnAtSeconds;
        this.minParty = b.minParty;
        this.maxParty = b.maxParty;
        this.exitMarker = b.exitMarker;
        this.bossEnabled = b.bossEnabled;
        this.bossId = b.bossId;
        this.scoring = b.scoring;
        this.onHitSlowEffectId = b.onHitSlowEffectId;
        this.onHitSlowSeconds = b.onHitSlowSeconds;
        this.onHitDamageMult = b.onHitDamageMult;
        this.onHitDamageFlat = b.onHitDamageFlat;
        this.onHitStackCap = b.onHitStackCap;
        this.onHitStackWindowSeconds = b.onHitStackWindowSeconds;
        this.enrageAfterSeconds = b.enrageAfterSeconds;
        this.enrageSpeedMult = b.enrageSpeedMult;
        this.enrageDamageMult = b.enrageDamageMult;
        this.enrageDurationSeconds = b.enrageDurationSeconds;
        this.enrageSoundId = b.enrageSoundId;
    }

    /**
     * Minimum party size the matchmaking queue waits for before it starts the
     * launch countdown (and the floor to launch). Clamped to at least 1. The lone-player
     * case is governed by the queue's {@code allowSolo}, not this floor.
     */
    public int minParty() {
        return Math.max(1, minParty);
    }

    /**
     * Maximum party size the queue seats before launching at once, clamped to the
     * {@link #ARENA_MAX_PARTY} budget and never below {@link #minParty()}.
     */
    public int maxParty() {
        return Math.max(minParty(), Math.min(maxParty, ARENA_MAX_PARTY));
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

    /**
     * Moonbloom charges required to fully cleanse a shrine, offered INCREMENTALLY at its furnace block
     * (each F-press deposits up to the remaining need; the furnace lights with green fire at this total).
     * A cost of 0 lights a shrine on the first press (the supply-free dial). Authored per preset via the
     * {@code RoundPresetAsset} {@code "CleanseCost"} knob (the easiest preset, Amateur, requires 3).
     */
    public int cleanseCost() {
        return cleanseCost;
    }

    /** How long (ms) a thrown Moonbloom freezes a hunter via the Perfect Utils stun on impact. */
    public long stunDurationMs() {
        return stunDurationMs;
    }

    /** How a thrown Moonbloom delivers its stun: the asset projectile, or the code-only cone fallback. */
    @Nonnull
    public ThrowMode throwMode() {
        return throwMode;
    }

    /** Guaranteed Moonbloom plants stamped in a cluster near EACH shrine anchor at round start. */
    public int moonbloomPerShrine() {
        return moonbloomPerShrine;
    }

    /** Extra Moonbloom plants scattered across the grove at round start (exploration supply). */
    public int moonbloomScatter() {
        return moonbloomScatter;
    }

    /** How many Moonbloom plants the grove regrows on EACH mid-match respawn wave. 0 = no respawn. */
    public int moonbloomRespawnCount() {
        return moonbloomRespawnCount;
    }

    /**
     * Round-elapsed seconds at which the grove regrows {@link #moonbloomRespawnCount()} Moonbloom
     * (the design's "respawn once or twice"). Empty = no respawn. A defensive copy is returned.
     */
    @Nonnull
    public int[] moonbloomRespawnAtSeconds() {
        return moonbloomRespawnAtSeconds.clone();
    }

    /**
     * Whether this round shows an exit map marker (a world-map / compass POI placed at
     * the escape when the Heartwood Gate opens). Default on; the Hardcore preset ships
     * it off so survivors must find their own way out. Asset-driven via the preset's
     * {@code ExitMarker} knob.
     */
    public boolean exitMarker() {
        return exitMarker;
    }

    /**
     * Whether this round spawns the multi-phase boss capstone (the corrupted-Kweebec Warden) at the escape
     * climax (all shrines lit, gate open). Default off; the harder presets author it on. Asset-driven via the
     * preset's {@code BossEnabled} knob. {@code boss/BossController} reads this in {@code ChaseMode.openGate}.
     */
    public boolean bossEnabled() {
        return bossEnabled;
    }

    /**
     * The boss id this round spawns when {@link #bossEnabled()} (resolved against {@link com.ziggfreed.kweebec.asset.BossConfig});
     * {@code null}/blank = the default Warden. Asset-driven via the preset's {@code BossId} knob.
     */
    @Nullable
    public String bossId() {
        return bossId;
    }

    /**
     * The per-preset scoring weights used to score a round of this preset (the base the runtime
     * tier may still override/scale). Authored via the preset's {@code Baseline}/{@code StunBonusPer}/
     * {@code ShrineBonusPer}/{@code AllShrinesBonus}/... knobs; absent fields keep the
     * {@link ScoringConfig} defaults. Never null.
     */
    @Nonnull
    public ScoringConfig scoring() {
        return scoring;
    }

    // --- on-hit punishment baseline (per-archetype non-null/non-zero overrides win) ---

    /** Baseline EntityEffect id applied to a victim on a hunter hit (the tier-1 slow), or blank for none. */
    @Nullable
    public String onHitSlowEffectId() {
        return onHitSlowEffectId;
    }

    /** Baseline slow duration in seconds. */
    public double onHitSlowSeconds() {
        return onHitSlowSeconds;
    }

    /** Baseline multiplier applied to a hunter's outgoing damage (1.0 = unchanged). */
    public double onHitDamageMult() {
        return onHitDamageMult;
    }

    /** Baseline flat bonus added to a hunter's outgoing damage. */
    public double onHitDamageFlat() {
        return onHitDamageFlat;
    }

    /** Baseline highest proximity-stack tier the slow escalates to (clamped to at least 1 by the consumer). */
    public int onHitStackCap() {
        return onHitStackCap;
    }

    /** Baseline window (seconds) within which repeated hits keep escalating the proximity stack. */
    public double onHitStackWindowSeconds() {
        return onHitStackWindowSeconds;
    }

    /** Baseline seconds without landing a hit before a hunter enrages; {@code 0} = enrage off. */
    public double enrageAfterSeconds() {
        return enrageAfterSeconds;
    }

    /** Baseline speed multiplier while enraged. */
    public double enrageSpeedMult() {
        return enrageSpeedMult;
    }

    /** Baseline damage multiplier while enraged. */
    public double enrageDamageMult() {
        return enrageDamageMult;
    }

    /** Baseline enrage duration in seconds. */
    public double enrageDurationSeconds() {
        return enrageDurationSeconds;
    }

    /** Baseline sound id played when a hunter enrages, or blank for none. */
    @Nullable
    public String enrageSoundId() {
        return enrageSoundId;
    }

    /**
     * The worldgen biome (WorldStructure) this round generates in - the per-difficulty world
     * flavor. Default {@link #DEFAULT_WORLD_STRUCTURE}; the per-difficulty presets author
     * {@code KweebecNightmare_Grove_Calm} / {@code _Dread}. {@code RoundService} maps this to the
     * matching instance asset to spawn.
     */
    @Nonnull
    public String worldStructure() {
        return worldStructure;
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
        b.worldStructure = this.worldStructure;
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
        b.cleanseCost = this.cleanseCost;
        b.stunDurationMs = this.stunDurationMs;
        b.throwMode = this.throwMode;
        b.moonbloomPerShrine = this.moonbloomPerShrine;
        b.moonbloomScatter = this.moonbloomScatter;
        b.moonbloomRespawnCount = this.moonbloomRespawnCount;
        b.moonbloomRespawnAtSeconds = this.moonbloomRespawnAtSeconds;
        b.minParty = this.minParty;
        b.maxParty = this.maxParty;
        b.exitMarker = this.exitMarker;
        b.bossEnabled = this.bossEnabled;
        b.bossId = this.bossId;
        b.scoring = this.scoring;
        b.onHitSlowEffectId = this.onHitSlowEffectId;
        b.onHitSlowSeconds = this.onHitSlowSeconds;
        b.onHitDamageMult = this.onHitDamageMult;
        b.onHitDamageFlat = this.onHitDamageFlat;
        b.onHitStackCap = this.onHitStackCap;
        b.onHitStackWindowSeconds = this.onHitStackWindowSeconds;
        b.enrageAfterSeconds = this.enrageAfterSeconds;
        b.enrageSpeedMult = this.enrageSpeedMult;
        b.enrageDamageMult = this.enrageDamageMult;
        b.enrageDurationSeconds = this.enrageDurationSeconds;
        b.enrageSoundId = this.enrageSoundId;
        return b;
    }

    /** Fluent builder with the design defaults (the Nightmare baseline) pre-seeded. */
    public static final class Builder {
        private final String presetId;
        private String worldStructure = DEFAULT_WORLD_STRUCTURE;
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
        // The shipped preset JSONs author CleanseCost per preset; this is only the zero-pack fallback.
        private int cleanseCost = 1;
        private long stunDurationMs = 2500L;
        private ThrowMode throwMode = ThrowMode.DEFAULT;
        private int moonbloomPerShrine = 3;
        private int moonbloomScatter = 12;
        private int moonbloomRespawnCount = 6;
        private int[] moonbloomRespawnAtSeconds = {180, 360};
        private int minParty = 1;
        private int maxParty = ARENA_MAX_PARTY;
        private boolean exitMarker = true;
        private boolean bossEnabled = false;
        @Nullable private String bossId = null;
        private ScoringConfig scoring = ScoringConfig.DEFAULT;
        // On-hit punishment baseline defaults (the zero-pack floor; presets / packs may override).
        @Nullable private String onHitSlowEffectId = "KweebecNightmare_HunterSlow_1";
        private double onHitSlowSeconds = 1.5;
        private double onHitDamageMult = 1.0;
        private double onHitDamageFlat = 0.0;
        private int onHitStackCap = 4;
        private double onHitStackWindowSeconds = 8.0;
        private double enrageAfterSeconds = 0.0; // 0 = enrage off by default; a preset/archetype enables it
        private double enrageSpeedMult = 1.3;
        private double enrageDamageMult = 1.25;
        private double enrageDurationSeconds = 5.0;
        @Nullable private String enrageSoundId = null;

        private Builder(@Nonnull String presetId) {
            this.presetId = presetId;
        }

        @Nonnull public Builder worldStructure(@Nonnull String v) { this.worldStructure = v; return this; }
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
        @Nonnull public Builder cleanseCost(int v) { this.cleanseCost = Math.max(0, v); return this; }
        @Nonnull public Builder stunDurationMs(long v) { this.stunDurationMs = Math.max(0L, v); return this; }
        @Nonnull public Builder throwMode(@Nonnull ThrowMode v) { this.throwMode = v; return this; }
        @Nonnull public Builder moonbloomPerShrine(int v) { this.moonbloomPerShrine = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomScatter(int v) { this.moonbloomScatter = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomRespawnCount(int v) { this.moonbloomRespawnCount = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomRespawnAtSeconds(@Nonnull int[] v) { this.moonbloomRespawnAtSeconds = v.clone(); return this; }
        @Nonnull public Builder minParty(int v) { this.minParty = v; return this; }
        @Nonnull public Builder maxParty(int v) { this.maxParty = v; return this; }
        @Nonnull public Builder exitMarker(boolean v) { this.exitMarker = v; return this; }
        @Nonnull public Builder bossEnabled(boolean v) { this.bossEnabled = v; return this; }
        @Nonnull public Builder bossId(@Nullable String v) { this.bossId = v; return this; }
        @Nonnull public Builder scoring(@Nonnull ScoringConfig v) { this.scoring = v; return this; }
        @Nonnull public Builder onHitSlowEffectId(@Nullable String v) { this.onHitSlowEffectId = v; return this; }
        @Nonnull public Builder onHitSlowSeconds(double v) { this.onHitSlowSeconds = v; return this; }
        @Nonnull public Builder onHitDamageMult(double v) { this.onHitDamageMult = v; return this; }
        @Nonnull public Builder onHitDamageFlat(double v) { this.onHitDamageFlat = v; return this; }
        @Nonnull public Builder onHitStackCap(int v) { this.onHitStackCap = v; return this; }
        @Nonnull public Builder onHitStackWindowSeconds(double v) { this.onHitStackWindowSeconds = v; return this; }
        @Nonnull public Builder enrageAfterSeconds(double v) { this.enrageAfterSeconds = v; return this; }
        @Nonnull public Builder enrageSpeedMult(double v) { this.enrageSpeedMult = v; return this; }
        @Nonnull public Builder enrageDamageMult(double v) { this.enrageDamageMult = v; return this; }
        @Nonnull public Builder enrageDurationSeconds(double v) { this.enrageDurationSeconds = v; return this; }
        @Nonnull public Builder enrageSoundId(@Nullable String v) { this.enrageSoundId = v; return this; }

        @Nonnull
        public RuleSet build() {
            return new RuleSet(this);
        }
    }
}
