package com.ziggfreed.kweebec.round;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.instance.zone.ContestRule;
import com.ziggfreed.common.worldmap.DiscoveryMode;
import com.ziggfreed.common.worldmap.MapDiscovery;
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
    private final int maxHunters;
    private final double hunterSpeedBase;
    private final double hunterSpeedMax;
    private final int shrineBase;
    private final int shrinePerPlayer;
    private final int caveShrineCount;
    private final int roundCapSeconds;
    private final int prepSeconds;
    private final double corruptionPerSecond;
    private final double corruptionPerShrine;
    private final double shrineRelightSeconds;
    private final InventoryMode inventoryMode;
    @Nullable private final String hunterArchetype;
    private final int cleanseCost;
    private final long stunDurationMs;
    private final Map<String, Double> throwableDamage;
    private final Map<String, Double> gatherHealthRestore;
    private final ThrowMode throwMode;
    private final int moonbloomPerShrine;
    private final int moonbloomScatter;
    private final int moonbloomRespawnCount;
    private final int[] moonbloomRespawnAtSeconds;
    private final int minParty;
    private final int maxParty;
    private final boolean exitMarker;
    private final ExtractionMode extractionMode;
    private final double extractionHoldSeconds;
    private final DiscoveryMode shrineDiscovery;
    private final MapDiscovery.Visibility shrineDiscoveryVisibility;
    private final double shrineDiscoveryRadius;
    private final boolean bossEnabled;
    @Nullable private final String bossId;
    private final boolean bossBarsGate;
    private final boolean bossMarker;
    private final double bossHealthMultiplier;
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
    // Jumpscare beat (the proximity/alert scare): which beat fires, shake strength, throttle, on/off.
    private final boolean jumpscareEnabled;
    @Nullable private final String jumpscareBeatId;
    private final double jumpscareShakeIntensity;
    private final int jumpscareCooldownSeconds;
    // Win feedback: the SoundEvent id played to each survivor on a round win (blank = no win sound).
    @Nullable private final String winSoundId;
    // Lose feedback: the SoundEvent id played to each present survivor on a round loss (blank = no lose sound).
    @Nullable private final String loseSoundId;
    // --- PvP shared (Clash + Domination): team size, friendly fire, model swap, arena selection ---
    private final int teamSize;
    private final boolean friendlyFire;
    @Nullable private final String modelSwapId;
    private final double modelSwapScale;
    @Nullable private final String arenaId;
    @Nullable private final String arenaTag;
    // --- Clash (team brawl) knobs (grouped view via clash()) ---
    private final WinCondition winCondition;
    private final RespawnPolicy respawnPolicy;
    private final int respawnDelaySeconds;
    private final int maxLives;
    private final int scoreToWin;
    private final int suddenDeathSeconds;
    private final int mushroomCadenceSeconds;
    private final int mushroomWaveCount;
    private final int mushroomMaxAlive;
    private final String[] mushroomCycle;
    // --- Domination (control point) knobs (grouped view via domination()) ---
    private final int dominationScoreToWin;
    private final double dominationPointHoldSeconds;
    private final int dominationAccrualPerSecond;
    private final double dominationPointRadius;
    private final ContestRule dominationContestRule;
    private final boolean dominationCaptureNeutralizes;
    private final int dominationRespawnDelaySeconds;

    private RuleSet(Builder b) {
        this.presetId = b.presetId;
        this.worldStructure = b.worldStructure;
        this.reviveStyle = b.reviveStyle;
        this.maxDowns = b.maxDowns;
        this.bleedOutSeconds = b.bleedOutSeconds;
        this.hunterCount = b.hunterCount;
        this.maxHunters = b.maxHunters;
        this.hunterSpeedBase = b.hunterSpeedBase;
        this.hunterSpeedMax = b.hunterSpeedMax;
        this.shrineBase = b.shrineBase;
        this.shrinePerPlayer = b.shrinePerPlayer;
        this.caveShrineCount = b.caveShrineCount;
        this.roundCapSeconds = b.roundCapSeconds;
        this.prepSeconds = b.prepSeconds;
        this.corruptionPerSecond = b.corruptionPerSecond;
        this.corruptionPerShrine = b.corruptionPerShrine;
        this.shrineRelightSeconds = b.shrineRelightSeconds;
        this.inventoryMode = b.inventoryMode;
        this.hunterArchetype = b.hunterArchetype;
        this.cleanseCost = b.cleanseCost;
        this.stunDurationMs = b.stunDurationMs;
        this.throwableDamage = Map.copyOf(b.throwableDamage);
        this.gatherHealthRestore = Map.copyOf(b.gatherHealthRestore);
        this.throwMode = b.throwMode;
        this.moonbloomPerShrine = b.moonbloomPerShrine;
        this.moonbloomScatter = b.moonbloomScatter;
        this.moonbloomRespawnCount = b.moonbloomRespawnCount;
        this.moonbloomRespawnAtSeconds = b.moonbloomRespawnAtSeconds;
        this.minParty = b.minParty;
        this.maxParty = b.maxParty;
        this.exitMarker = b.exitMarker;
        this.extractionMode = b.extractionMode;
        this.extractionHoldSeconds = b.extractionHoldSeconds;
        this.shrineDiscovery = b.shrineDiscovery;
        this.shrineDiscoveryVisibility = b.shrineDiscoveryVisibility;
        this.shrineDiscoveryRadius = b.shrineDiscoveryRadius;
        this.bossEnabled = b.bossEnabled;
        this.bossId = b.bossId;
        this.bossBarsGate = b.bossBarsGate;
        this.bossMarker = b.bossMarker;
        this.bossHealthMultiplier = b.bossHealthMultiplier;
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
        this.jumpscareEnabled = b.jumpscareEnabled;
        this.jumpscareBeatId = b.jumpscareBeatId;
        this.jumpscareShakeIntensity = b.jumpscareShakeIntensity;
        this.jumpscareCooldownSeconds = b.jumpscareCooldownSeconds;
        this.winSoundId = b.winSoundId;
        this.loseSoundId = b.loseSoundId;
        this.teamSize = b.teamSize;
        this.friendlyFire = b.friendlyFire;
        this.modelSwapId = b.modelSwapId;
        this.modelSwapScale = b.modelSwapScale;
        this.arenaId = b.arenaId;
        this.arenaTag = b.arenaTag;
        this.winCondition = b.winCondition;
        this.respawnPolicy = b.respawnPolicy;
        this.respawnDelaySeconds = b.respawnDelaySeconds;
        this.maxLives = b.maxLives;
        this.scoreToWin = b.scoreToWin;
        this.suddenDeathSeconds = b.suddenDeathSeconds;
        this.mushroomCadenceSeconds = b.mushroomCadenceSeconds;
        this.mushroomWaveCount = b.mushroomWaveCount;
        this.mushroomMaxAlive = b.mushroomMaxAlive;
        this.mushroomCycle = b.mushroomCycle;
        this.dominationScoreToWin = b.dominationScoreToWin;
        this.dominationPointHoldSeconds = b.dominationPointHoldSeconds;
        this.dominationAccrualPerSecond = b.dominationAccrualPerSecond;
        this.dominationPointRadius = b.dominationPointRadius;
        this.dominationContestRule = b.dominationContestRule;
        this.dominationCaptureNeutralizes = b.dominationCaptureNeutralizes;
        this.dominationRespawnDelaySeconds = b.dominationRespawnDelaySeconds;
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

    /**
     * Per-round LIVE hunter ceiling: how many corrupted Kweebec may be alive at once
     * (difficulty-driven, bounded caller-side by the absolute backstop + scaled per party
     * member). The initial roster is {@link #hunterCount()}; escalation + extra-spawn waves
     * fill up toward this.
     */
    public int maxHunters() {
        return maxHunters;
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

    /**
     * Seconds of PREP grace after the party enters before the hunt begins - the hunter spawns and the
     * heartbeat + corruption ramp start at the PREP -> RITUAL transition this many seconds in. The
     * per-difficulty head start (Amateur 45, Nightmare 30, Hardcore 20); a longer breath is easier.
     * Clamped to at least 0 by the builder. Asset-driven via the preset's {@code PrepSeconds} knob.
     */
    public int prepSeconds() {
        return prepSeconds;
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
     * How a survivor's inventory is treated this round. Wired through {@code RoundInventoryGuard}:
     * the default {@link InventoryMode#PRESERVE_AND_STRIP} snapshots + strips on entry and restores
     * the exact entry state on exit; {@link InventoryMode#KEEP} leaves the inventory untouched.
     */
    @Nonnull
    public InventoryMode inventoryMode() {
        return inventoryMode;
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

    /**
     * Per-difficulty damage override for a thrown glow-throwable, keyed by the throwable's custom
     * {@code DamageCause} id (e.g. {@code KweebecNightmare_Moonbloom}, {@code KweebecNightmare_EmberHit}).
     * When a hit lands on a mob in a round and its cause id is present here, {@code KweebecDamageSystem}
     * overrides the burst's authored damage with this amount, so one preset tunes how hard each authored
     * throwable hits without touching the shared burst asset. Absent key = the burst's authored damage
     * stands. Never null; empty when the preset authors no overrides. Authored via the preset's
     * {@code ThrowableDamage} object knob.
     */
    @Nonnull
    public Map<String, Double> throwableDamage() {
        return throwableDamage;
    }

    /**
     * The per-difficulty damage override for the throwable identified by {@code damageCauseId}, or
     * {@code null} when this preset authors none for it (the burst's authored damage stands).
     */
    @Nullable
    public Double throwableDamage(@Nonnull String damageCauseId) {
        return throwableDamage.get(damageCauseId);
    }

    /**
     * Per-difficulty HEALTH RESTORED when a survivor GATHERS (picks up) a glow-mushroom this round,
     * keyed by the gathered item's id (e.g. {@code KweebecNightmare_Moonbloom},
     * {@code KweebecNightmare_Emberbloom}); the value is the HP restored per shroom picked up (a stack of
     * N heals N times the amount). {@code MoonbloomCollectSystem} reads this on each in-round pickup and
     * tops the gatherer off via ziggfreed-common {@code HealthUtil.heal}, so a preset can make scavenging
     * a survival lifeline on the gentler difficulties and a thin trickle on the harder ones. Absent key =
     * no heal for that item. Never null; empty when the preset authors none. Authored via the preset's
     * {@code GatherHealthRestore} object knob.
     */
    @Nonnull
    public Map<String, Double> gatherHealthRestore() {
        return gatherHealthRestore;
    }

    /**
     * The HP restored per gathered shroom for the item identified by {@code itemId}, or {@code null} when
     * this preset authors no heal for it (no health is restored on its pickup).
     */
    @Nullable
    public Double gatherHealthRestore(@Nonnull String itemId) {
        return gatherHealthRestore.get(itemId);
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
     * WHO must be standing on the Heartwood extraction platform for the co-op escape to complete: every
     * MOBILE survivor ({@link ExtractionMode#ALL_MOBILE}, a downed teammate does not block) or the
     * WHOLE party rescue-first ({@link ExtractionMode#EVERYONE}). The escape is a GROUP hold, not a
     * single survivor reaching the exit. Asset-driven via the preset's {@code ExtractionMode} knob.
     */
    @Nonnull
    public ExtractionMode extractionMode() {
        return extractionMode;
    }

    /**
     * Seconds the required survivor group must hold the extraction platform TOGETHER (continuously) for the
     * escape to complete; the hold resets to zero whenever the group breaks (someone steps off or is
     * caught). {@code 0} extracts the instant everyone is on the pad. Asset-driven via the preset's
     * {@code ExtractionHoldSeconds} knob.
     */
    public double extractionHoldSeconds() {
        return extractionHoldSeconds;
    }

    /**
     * How a survivor DISCOVERS a shrine for the world-map marker (the dark-navigation aid). Default
     * {@link DiscoveryMode#ON_INTERACT} (the first F-press marks it, regardless of Moonbloom on hand);
     * {@link DiscoveryMode#OFF} shows no shrine markers (the Hardcore preset, like {@link #exitMarker()});
     * {@link DiscoveryMode#PROXIMITY} (radius via {@link #shrineDiscoveryRadius()}) is the future seam.
     * Asset-driven via the preset's {@code ShrineDiscovery} knob.
     */
    @Nonnull
    public DiscoveryMode shrineDiscovery() {
        return shrineDiscovery;
    }

    /**
     * WHO sees a discovered shrine marker: {@link MapDiscovery.Visibility#PER_PLAYER} (only the survivor
     * who found it - the default, the Nightmare "self" feel) or {@link MapDiscovery.Visibility#SHARED}
     * (the whole party once anyone finds it - the Amateur "all" feel). Asset-driven via the preset's
     * {@code ShrineDiscoveryVisibility} knob (accepts {@code SELF}/{@code ALL} aliases).
     */
    @Nonnull
    public MapDiscovery.Visibility shrineDiscoveryVisibility() {
        return shrineDiscoveryVisibility;
    }

    /** Reveal radius (blocks) when {@link #shrineDiscovery()} is {@link DiscoveryMode#PROXIMITY}; unused otherwise. */
    public double shrineDiscoveryRadius() {
        return shrineDiscoveryRadius;
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
     * The boss id this round spawns when {@link #bossEnabled()} (resolved against ziggfreed-common's
     * {@link com.ziggfreed.common.instance.encounter.MultiPhaseBossConfig} via
     * {@link com.ziggfreed.kweebec.integration.KweebecNightmareAPI#resolveBoss});
     * {@code null}/blank = the default Warden. Asset-driven via the preset's {@code BossId} knob.
     */
    @Nullable
    public String bossId() {
        return bossId;
    }

    /**
     * Whether the capstone boss BARS the Heartwood Gate: when {@code true} (and {@link #bossEnabled()}),
     * lighting the last shrine spawns the Warden but holds the gate SHUT until it is defeated, so survivors
     * must kill the boss to escape (the "It rose to bar the gate" climax). When {@code false}, an enabled
     * boss is a pure obstacle beside an already-open gate. Default off; Nightmare + Hardcore author it on.
     * Asset-driven via the preset's {@code BossBarsGate} knob; {@code ChaseMode} reads it at the escape beat.
     */
    public boolean bossBarsGate() {
        return bossBarsGate;
    }

    /**
     * Whether an enabled boss drops a WORLD-MAP marker that tracks it (a compass/world-map POI the
     * {@code BossController} places at spawn and follows as it moves). Default on; a preset can author it
     * off (e.g. Hardcore, which also ships the exit marker dark). Asset-driven via the preset's
     * {@code BossMarker} knob. Mirrors {@link #exitMarker()}.
     */
    public boolean bossMarker() {
        return bossMarker;
    }

    /**
     * Per-difficulty flat multiplier on the boss's MAX health, composed with the boss asset's
     * {@code HealthPerPlayer} party scaling ({@code final = bossHealthMultiplier * (1 + perPlayer*(players-1))}).
     * Default {@code 1.0} (no change); harder presets author it up. Asset-driven via the preset's
     * {@code BossHealthMultiplier} knob; {@code BossController} reads it at spawn.
     */
    public double bossHealthMultiplier() {
        return bossHealthMultiplier;
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

    // --- jumpscare beat (per-preset; ScareDirector reads these for the proximity/alert scare) ---

    /**
     * Whether the proximity/alert jumpscare fires this round. Default on; a calmer preset
     * (Amateur) may author it off. Asset-driven via the preset's {@code JumpscareEnabled} knob.
     */
    public boolean jumpscareEnabled() {
        return jumpscareEnabled;
    }

    /**
     * Id of the ziggfreed-common {@code BandedEffectAsset} one-shot beat the jumpscare fires
     * (the overlay {@code EntityEffect} + scream sound + camera shake bundled in one asset),
     * or {@code null}/blank to use the first authored one-shot ({@code BandedEffectConfig.oneShot()}).
     * Asset-driven via the preset's {@code JumpscareBeatId} knob - a preset can point at a
     * different overlay/intensity beat per game mode.
     */
    @Nullable
    public String jumpscareBeatId() {
        return jumpscareBeatId;
    }

    /**
     * Per-preset camera-shake intensity override (0..1) for the jumpscare; {@link Double#NaN}
     * (the default) keeps the beat's own {@code ShakeIntensity}. Asset-driven via the preset's
     * {@code JumpscareShakeIntensity} knob - the quick per-mode dial without authoring a new beat.
     */
    public double jumpscareShakeIntensity() {
        return jumpscareShakeIntensity;
    }

    /** Minimum gap (seconds) between jumpscares for one survivor. Asset-driven via {@code JumpscareCooldownSeconds}. */
    public int jumpscareCooldownSeconds() {
        return jumpscareCooldownSeconds;
    }

    /**
     * The {@code SoundEvent} id played to each surviving player when the round is WON (the extraction
     * fanfare), or {@code null}/blank for no win sound. Default {@code SFX_Discovery_Z1_Medium} (a vanilla
     * triumphant discovery jingle); a pack tunes it per preset via the {@code WinSoundId} knob. Played
     * through the ziggfreed-common {@code Sound3D} seam in {@code ChaseRoundMode.onResolve}.
     */
    @Nullable
    public String winSoundId() {
        return winSoundId;
    }

    /**
     * The {@code SoundEvent} id played to each present survivor when the round is LOST (the defeat
     * stinger), or {@code null}/blank for no lose sound. Default {@code SFX_Hedera_Scream} (a vanilla
     * Void scream); a pack tunes it per preset via the {@code LoseSoundId} knob. Played through the
     * ziggfreed-common {@code Sound3D} seam in {@code ChaseRoundMode.onResolve}.
     */
    @Nullable
    public String loseSoundId() {
        return loseSoundId;
    }

    // --- PvP shared (Clash + Domination) ---

    /** Players per team in a PvP round: 1 (1v1) or 2 (2v2). The queue seats {@code teamSize * 2}. */
    public int teamSize() {
        return Math.max(1, teamSize);
    }

    /** Whether same-team damage applies in a PvP round (read by {@code KweebecDamageSystem}). */
    public boolean friendlyFire() {
        return friendlyFire;
    }

    /** The model a PvP player is reskinned to for the match (e.g. {@code Kweebec_Sapling}); blank = no swap. */
    @Nullable
    public String modelSwapId() {
        return modelSwapId;
    }

    /** Scale applied to the swapped PvP model (1.0 = the model's own scale). */
    public double modelSwapScale() {
        return modelSwapScale;
    }

    /** Pin a specific arena id from the {@code ziggfreed-common} arena catalog; {@code null} = pick by tag. */
    @Nullable
    public String arenaId() {
        return arenaId;
    }

    /** Pick a random arena carrying this tag; {@code null} = default to the mode id ({@code clash}/{@code domination}). */
    @Nullable
    public String arenaTag() {
        return arenaTag;
    }

    /** The Clash (team brawl) knobs as an immutable grouped view. */
    @Nonnull
    public ClashConfig clash() {
        return new ClashConfig(winCondition, respawnPolicy, respawnDelaySeconds, maxLives, scoreToWin,
                suddenDeathSeconds, mushroomCadenceSeconds, mushroomWaveCount, mushroomMaxAlive, mushroomCycle);
    }

    /** The Domination (control point) knobs as an immutable grouped view. */
    @Nonnull
    public DominationConfig domination() {
        return new DominationConfig(dominationScoreToWin, dominationPointHoldSeconds, dominationAccrualPerSecond,
                dominationPointRadius, dominationContestRule, dominationCaptureNeutralizes,
                dominationRespawnDelaySeconds);
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
        b.maxHunters = this.maxHunters;
        b.hunterSpeedBase = this.hunterSpeedBase;
        b.hunterSpeedMax = this.hunterSpeedMax;
        b.shrineBase = this.shrineBase;
        b.shrinePerPlayer = this.shrinePerPlayer;
        b.caveShrineCount = this.caveShrineCount;
        b.roundCapSeconds = this.roundCapSeconds;
        b.prepSeconds = this.prepSeconds;
        b.corruptionPerSecond = this.corruptionPerSecond;
        b.corruptionPerShrine = this.corruptionPerShrine;
        b.shrineRelightSeconds = this.shrineRelightSeconds;
        b.inventoryMode = this.inventoryMode;
        b.hunterArchetype = this.hunterArchetype;
        b.cleanseCost = this.cleanseCost;
        b.stunDurationMs = this.stunDurationMs;
        b.throwableDamage = this.throwableDamage;
        b.gatherHealthRestore = this.gatherHealthRestore;
        b.throwMode = this.throwMode;
        b.moonbloomPerShrine = this.moonbloomPerShrine;
        b.moonbloomScatter = this.moonbloomScatter;
        b.moonbloomRespawnCount = this.moonbloomRespawnCount;
        b.moonbloomRespawnAtSeconds = this.moonbloomRespawnAtSeconds;
        b.minParty = this.minParty;
        b.maxParty = this.maxParty;
        b.exitMarker = this.exitMarker;
        b.extractionMode = this.extractionMode;
        b.extractionHoldSeconds = this.extractionHoldSeconds;
        b.shrineDiscovery = this.shrineDiscovery;
        b.shrineDiscoveryVisibility = this.shrineDiscoveryVisibility;
        b.shrineDiscoveryRadius = this.shrineDiscoveryRadius;
        b.bossEnabled = this.bossEnabled;
        b.bossId = this.bossId;
        b.bossBarsGate = this.bossBarsGate;
        b.bossMarker = this.bossMarker;
        b.bossHealthMultiplier = this.bossHealthMultiplier;
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
        b.jumpscareEnabled = this.jumpscareEnabled;
        b.jumpscareBeatId = this.jumpscareBeatId;
        b.jumpscareShakeIntensity = this.jumpscareShakeIntensity;
        b.jumpscareCooldownSeconds = this.jumpscareCooldownSeconds;
        b.winSoundId = this.winSoundId;
        b.loseSoundId = this.loseSoundId;
        b.teamSize = this.teamSize;
        b.friendlyFire = this.friendlyFire;
        b.modelSwapId = this.modelSwapId;
        b.modelSwapScale = this.modelSwapScale;
        b.arenaId = this.arenaId;
        b.arenaTag = this.arenaTag;
        b.winCondition = this.winCondition;
        b.respawnPolicy = this.respawnPolicy;
        b.respawnDelaySeconds = this.respawnDelaySeconds;
        b.maxLives = this.maxLives;
        b.scoreToWin = this.scoreToWin;
        b.suddenDeathSeconds = this.suddenDeathSeconds;
        b.mushroomCadenceSeconds = this.mushroomCadenceSeconds;
        b.mushroomWaveCount = this.mushroomWaveCount;
        b.mushroomMaxAlive = this.mushroomMaxAlive;
        b.mushroomCycle = this.mushroomCycle;
        b.dominationScoreToWin = this.dominationScoreToWin;
        b.dominationPointHoldSeconds = this.dominationPointHoldSeconds;
        b.dominationAccrualPerSecond = this.dominationAccrualPerSecond;
        b.dominationPointRadius = this.dominationPointRadius;
        b.dominationContestRule = this.dominationContestRule;
        b.dominationCaptureNeutralizes = this.dominationCaptureNeutralizes;
        b.dominationRespawnDelaySeconds = this.dominationRespawnDelaySeconds;
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
        // The per-round LIVE hunter ceiling that escalation + extra-spawn waves fill toward
        // (difficulty-driven; the presets author 15/25/50). The initial roster stays
        // hunterCount-driven; this only bounds how many can be ALIVE at once.
        private int maxHunters = 6;
        private double hunterSpeedBase = 1.0;
        private double hunterSpeedMax = 1.35;
        private int shrineBase = 2;
        private int shrinePerPlayer = 1;
        private int caveShrineCount = 2;
        private int roundCapSeconds = 900;
        private int prepSeconds = 15;
        private double corruptionPerSecond = 0.0014;
        private double corruptionPerShrine = 0.12;
        private double shrineRelightSeconds = 6.0;
        private InventoryMode inventoryMode = InventoryMode.DEFAULT;
        @Nullable private String hunterArchetype = null;
        // The shipped preset JSONs author CleanseCost per preset; this is only the zero-pack fallback.
        private int cleanseCost = 1;
        private long stunDurationMs = 2500L;
        // Per-throwable damage overrides keyed by DamageCause id (empty = each burst's authored damage stands).
        private Map<String, Double> throwableDamage = Map.of();
        // Per-gather health restore keyed by gathered shroom item id (empty = no gather heal).
        private Map<String, Double> gatherHealthRestore = Map.of();
        private ThrowMode throwMode = ThrowMode.DEFAULT;
        private int moonbloomPerShrine = 3;
        private int moonbloomScatter = 12;
        private int moonbloomRespawnCount = 6;
        private int[] moonbloomRespawnAtSeconds = {180, 360};
        private int minParty = 1;
        private int maxParty = ARENA_MAX_PARTY;
        private boolean exitMarker = true;
        // Co-op extraction defaults (the no-soft-lock baseline; presets author per-difficulty).
        private ExtractionMode extractionMode = ExtractionMode.ALL_MOBILE;
        private double extractionHoldSeconds = 5.0;
        // Shrine-discovery markers default to the Nightmare "self" feel: each survivor sees only the
        // shrines they personally first-touched. Amateur authors SHARED ("all"); Hardcore authors OFF.
        private DiscoveryMode shrineDiscovery = DiscoveryMode.ON_INTERACT;
        private MapDiscovery.Visibility shrineDiscoveryVisibility = MapDiscovery.Visibility.PER_PLAYER;
        private double shrineDiscoveryRadius = 24.0;
        private boolean bossEnabled = false;
        @Nullable private String bossId = null;
        private boolean bossBarsGate = false;
        private boolean bossMarker = true;
        private double bossHealthMultiplier = 1.0;
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
        // Jumpscare defaults (the zero-pack baseline; presets author per game mode).
        private boolean jumpscareEnabled = true;
        @Nullable private String jumpscareBeatId = null;       // null -> BandedEffectConfig.oneShot()
        private double jumpscareShakeIntensity = Double.NaN;   // NaN -> use the beat's own ShakeIntensity
        private int jumpscareCooldownSeconds = 12;
        // Win fanfare: a vanilla triumphant discovery jingle by default; a preset may override or blank it.
        @Nullable private String winSoundId = "SFX_Discovery_Z1_Medium";
        // Lose stinger: a vanilla Void scream by default; a preset may override or blank it.
        @Nullable private String loseSoundId = "SFX_Hedera_Scream";
        // PvP shared defaults (irrelevant to a chase preset, which never reads them).
        private int teamSize = 1;
        private boolean friendlyFire = false;
        @Nullable private String modelSwapId = "Kweebec_Sapling";
        private double modelSwapScale = 1.0;
        @Nullable private String arenaId = null;
        @Nullable private String arenaTag = null;
        // Clash defaults (the brief's two-stage rule: last team standing, most hits at the timer).
        private WinCondition winCondition = WinCondition.DEFAULT;
        private RespawnPolicy respawnPolicy = RespawnPolicy.DEFAULT;
        private int respawnDelaySeconds = 5;
        private int maxLives = 1;
        private int scoreToWin = 0;
        private int suddenDeathSeconds = 0;
        private int mushroomCadenceSeconds = 30;
        private int mushroomWaveCount = 1;
        private int mushroomMaxAlive = 12;
        private String[] mushroomCycle = new String[0];
        // Domination defaults.
        private int dominationScoreToWin = 300;
        private double dominationPointHoldSeconds = 5.0;
        private int dominationAccrualPerSecond = 1;
        private double dominationPointRadius = 6.0;
        private ContestRule dominationContestRule = ContestRule.ANY_PRESENCE;
        private boolean dominationCaptureNeutralizes = true;
        private int dominationRespawnDelaySeconds = 5;

        private Builder(@Nonnull String presetId) {
            this.presetId = presetId;
        }

        @Nonnull public Builder worldStructure(@Nonnull String v) { this.worldStructure = v; return this; }
        @Nonnull public Builder reviveStyle(@Nonnull ReviveStyle v) { this.reviveStyle = v; return this; }
        @Nonnull public Builder maxDowns(int v) { this.maxDowns = v; return this; }
        @Nonnull public Builder bleedOutSeconds(int v) { this.bleedOutSeconds = v; return this; }
        @Nonnull public Builder hunterCount(int v) { this.hunterCount = v; return this; }
        @Nonnull public Builder maxHunters(int v) { this.maxHunters = v; return this; }
        @Nonnull public Builder hunterSpeed(double base, double max) { this.hunterSpeedBase = base; this.hunterSpeedMax = max; return this; }
        @Nonnull public Builder shrines(int base, int perPlayer) { this.shrineBase = base; this.shrinePerPlayer = perPlayer; return this; }
        @Nonnull public Builder caveShrineCount(int v) { this.caveShrineCount = v; return this; }
        @Nonnull public Builder roundCapSeconds(int v) { this.roundCapSeconds = v; return this; }
        @Nonnull public Builder prepSeconds(int v) { this.prepSeconds = Math.max(0, v); return this; }
        @Nonnull public Builder corruptionPerSecond(double v) { this.corruptionPerSecond = v; return this; }
        @Nonnull public Builder corruptionPerShrine(double v) { this.corruptionPerShrine = v; return this; }
        @Nonnull public Builder shrineRelightSeconds(double v) { this.shrineRelightSeconds = v; return this; }
        @Nonnull public Builder inventoryMode(@Nonnull InventoryMode v) { this.inventoryMode = v; return this; }
        @Nonnull public Builder hunterArchetype(@Nullable String v) { this.hunterArchetype = v; return this; }
        @Nonnull public Builder cleanseCost(int v) { this.cleanseCost = Math.max(0, v); return this; }
        @Nonnull public Builder stunDurationMs(long v) { this.stunDurationMs = Math.max(0L, v); return this; }
        @Nonnull public Builder throwableDamage(@Nonnull Map<String, Double> v) { this.throwableDamage = Map.copyOf(v); return this; }
        @Nonnull public Builder gatherHealthRestore(@Nonnull Map<String, Double> v) { this.gatherHealthRestore = Map.copyOf(v); return this; }
        @Nonnull public Builder throwMode(@Nonnull ThrowMode v) { this.throwMode = v; return this; }
        @Nonnull public Builder moonbloomPerShrine(int v) { this.moonbloomPerShrine = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomScatter(int v) { this.moonbloomScatter = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomRespawnCount(int v) { this.moonbloomRespawnCount = Math.max(0, v); return this; }
        @Nonnull public Builder moonbloomRespawnAtSeconds(@Nonnull int[] v) { this.moonbloomRespawnAtSeconds = v.clone(); return this; }
        @Nonnull public Builder minParty(int v) { this.minParty = v; return this; }
        @Nonnull public Builder maxParty(int v) { this.maxParty = v; return this; }
        @Nonnull public Builder exitMarker(boolean v) { this.exitMarker = v; return this; }
        @Nonnull public Builder extractionMode(@Nonnull ExtractionMode v) { this.extractionMode = v; return this; }
        @Nonnull public Builder extractionHoldSeconds(double v) { this.extractionHoldSeconds = Math.max(0.0, v); return this; }
        @Nonnull public Builder shrineDiscovery(@Nonnull DiscoveryMode v) { this.shrineDiscovery = v; return this; }
        @Nonnull public Builder shrineDiscoveryVisibility(@Nonnull MapDiscovery.Visibility v) { this.shrineDiscoveryVisibility = v; return this; }
        @Nonnull public Builder shrineDiscoveryRadius(double v) { this.shrineDiscoveryRadius = Math.max(0.0, v); return this; }
        @Nonnull public Builder bossEnabled(boolean v) { this.bossEnabled = v; return this; }
        @Nonnull public Builder bossId(@Nullable String v) { this.bossId = v; return this; }
        @Nonnull public Builder bossBarsGate(boolean v) { this.bossBarsGate = v; return this; }
        @Nonnull public Builder bossMarker(boolean v) { this.bossMarker = v; return this; }
        @Nonnull public Builder bossHealthMultiplier(double v) { this.bossHealthMultiplier = v; return this; }
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
        @Nonnull public Builder jumpscareEnabled(boolean v) { this.jumpscareEnabled = v; return this; }
        @Nonnull public Builder jumpscareBeatId(@Nullable String v) { this.jumpscareBeatId = v; return this; }
        @Nonnull public Builder jumpscareShakeIntensity(double v) { this.jumpscareShakeIntensity = v; return this; }
        @Nonnull public Builder jumpscareCooldownSeconds(int v) { this.jumpscareCooldownSeconds = Math.max(0, v); return this; }
        @Nonnull public Builder winSoundId(@Nullable String v) { this.winSoundId = v; return this; }
        @Nonnull public Builder loseSoundId(@Nullable String v) { this.loseSoundId = v; return this; }
        @Nonnull public Builder teamSize(int v) { this.teamSize = Math.max(1, v); return this; }
        @Nonnull public Builder friendlyFire(boolean v) { this.friendlyFire = v; return this; }
        @Nonnull public Builder modelSwapId(@Nullable String v) { this.modelSwapId = v; return this; }
        @Nonnull public Builder modelSwapScale(double v) { this.modelSwapScale = v; return this; }
        @Nonnull public Builder arenaId(@Nullable String v) { this.arenaId = v; return this; }
        @Nonnull public Builder arenaTag(@Nullable String v) { this.arenaTag = v; return this; }
        @Nonnull public Builder winCondition(@Nonnull WinCondition v) { this.winCondition = v; return this; }
        @Nonnull public Builder respawnPolicy(@Nonnull RespawnPolicy v) { this.respawnPolicy = v; return this; }
        @Nonnull public Builder respawnDelaySeconds(int v) { this.respawnDelaySeconds = Math.max(0, v); return this; }
        @Nonnull public Builder maxLives(int v) { this.maxLives = Math.max(1, v); return this; }
        @Nonnull public Builder scoreToWin(int v) { this.scoreToWin = Math.max(0, v); return this; }
        @Nonnull public Builder suddenDeathSeconds(int v) { this.suddenDeathSeconds = Math.max(0, v); return this; }
        @Nonnull public Builder mushroomCadenceSeconds(int v) { this.mushroomCadenceSeconds = Math.max(0, v); return this; }
        @Nonnull public Builder mushroomWaveCount(int v) { this.mushroomWaveCount = Math.max(0, v); return this; }
        @Nonnull public Builder mushroomMaxAlive(int v) { this.mushroomMaxAlive = Math.max(0, v); return this; }
        @Nonnull public Builder mushroomCycle(@Nonnull String[] v) { this.mushroomCycle = v.clone(); return this; }
        @Nonnull public Builder dominationScoreToWin(int v) { this.dominationScoreToWin = Math.max(1, v); return this; }
        @Nonnull public Builder dominationPointHoldSeconds(double v) { this.dominationPointHoldSeconds = Math.max(0.0, v); return this; }
        @Nonnull public Builder dominationAccrualPerSecond(int v) { this.dominationAccrualPerSecond = Math.max(1, v); return this; }
        @Nonnull public Builder dominationPointRadius(double v) { this.dominationPointRadius = Math.max(1.0, v); return this; }
        @Nonnull public Builder dominationContestRule(@Nonnull ContestRule v) { this.dominationContestRule = v; return this; }
        @Nonnull public Builder dominationCaptureNeutralizes(boolean v) { this.dominationCaptureNeutralizes = v; return this; }
        @Nonnull public Builder dominationRespawnDelaySeconds(int v) { this.dominationRespawnDelaySeconds = Math.max(0, v); return this; }

        @Nonnull
        public RuleSet build() {
            return new RuleSet(this);
        }
    }
}
