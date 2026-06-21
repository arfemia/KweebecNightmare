package com.ziggfreed.kweebec.asset;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.ziggfreed.common.instance.zone.ContestRule;
import com.ziggfreed.common.worldmap.DiscoveryMode;
import com.ziggfreed.common.worldmap.MapDiscovery;
import com.ziggfreed.kweebec.round.ExtractionMode;
import com.ziggfreed.kweebec.round.InventoryMode;
import com.ziggfreed.kweebec.round.RespawnPolicy;
import com.ziggfreed.kweebec.round.ReviveStyle;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.round.ThrowMode;
import com.ziggfreed.kweebec.round.WinCondition;
import com.ziggfreed.kweebec.score.ScoringConfig;

/**
 * A pack-authorable round preset, loaded from a pack's
 * {@code Server/KweebecNightmare/Presets/*.json}. The data source for the
 * difficulty {@link RuleSet} an installed MMO can override at runtime; replaces the
 * old hardcoded {@code RoundPreset} enum as the schema authority.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors hyMMO's {@code QuestGiverAsset}).
 * A preset is a flat, self-contained record with no template DSL, so the engine
 * decodes it DIRECTLY into typed fields via {@link #CODEC} - the codec IS the single
 * schema authority on both the pack layer (engine asset load) and the owner layer
 * ({@code mods/kweebecnightmare/presets.json}, decoded through the same CODEC).
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start). The 13
 * {@link RuleSet} knobs map straight onto the unchanged {@link RuleSet} builder via
 * {@link #toRuleSet(String)}; {@code InventoryMode} / {@code HunterArchetype} are the
 * preset-level gameplay metadata. The CROSS-CUTTING {@code NameKey} / {@code Enabled} /
 * {@code RewardOnExit} live ONLY on the co-keyed common {@code InstancePresetAsset}
 * ({@code Server/ZiggfreedCommon/Instances/}), never here - the two are field-disjoint
 * over the shared preset id.
 *
 * <p>Pack JSON shape (all fields optional; absent = the Nightmare-baseline default
 * from the {@link RuleSet} builder):
 * <pre>{@code
 * { "Name": "nightmare",
 *   "ReviveStyle": "COOP_RESCUE", "MaxDowns": 1, "BleedOutSeconds": 30,
 *   "HunterCount": 1, "HunterSpeedBase": 1.0, "HunterSpeedMax": 1.35,
 *   "ShrineBase": 2, "ShrinePerPlayer": 1, "CaveShrineCount": 2,
 *   "RoundCapSeconds": 900, "CorruptionPerSecond": 0.0014,
 *   "CorruptionPerShrine": 0.12, "ShrineRelightSeconds": 6.0,
 *   "InventoryMode": "PRESERVE_AND_STRIP", "HunterArchetype": "stalker",
 *   "Baseline": 1000, "ParTimeSeconds": 420, "TimePointsPerSecond": 5.0,
 *   "DamagePointsPerHp": 8.0, "DamageBudget": 200.0, "StunBonusPer": 50,
 *   "ShrineBonusPer": 75, "AllShrinesBonus": 500,
 *   "JumpscareEnabled": true, "JumpscareBeatId": "jumpscare",
 *   "JumpscareShakeIntensity": 0.7, "JumpscareCooldownSeconds": 12,
 *   "WinSoundId": "SFX_Discovery_Z1_Medium" }
 * }</pre>
 *
 * <p>{@code WinSoundId} is the {@code SoundEvent} id played to each survivor on a round win (the
 * extraction fanfare; absent = the {@link RuleSet} default {@code SFX_Discovery_Z1_Medium}). It plays
 * through the ziggfreed-common {@code Sound3D} seam in {@code ChaseRoundMode.onResolve}.
 *
 * <p>The 4 jumpscare knobs are per-game-mode: {@code JumpscareEnabled} toggles the proximity/alert
 * scare; {@code JumpscareBeatId} names a ziggfreed-common {@code BandedEffect} one-shot (overlay
 * {@code EntityEffect} + scream + camera shake bundled), so a preset can use a different overlay/shake
 * beat per mode (absent = the first authored one-shot); {@code JumpscareShakeIntensity} overrides that
 * beat's shake strength (0..1); {@code JumpscareCooldownSeconds} throttles it. Read by {@code ScareDirector}.
 *
 * <p>The 8 scoring knobs ({@code Baseline}..{@code AllShrinesBonus}) drive the asset-driven,
 * per-difficulty round scoring (see {@code score/ScoringConfig}): each absent keeps the
 * {@link ScoringConfig} default, so a preset can reward speed/caution/aggression/devotion
 * differently. {@code toRuleSet} folds them into the {@link RuleSet}'s {@link RuleSet#scoring()}.
 */
public final class RoundPresetAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, RoundPresetAsset>> {

    /** Sentinel for "field absent" on the optional int / long / double knobs (MIN_VALUE / NaN = use the builder default). */
    private static final int UNSET_INT = Integer.MIN_VALUE;
    private static final long UNSET_LONG = Long.MIN_VALUE;
    private static final double UNSET_DOUBLE = Double.NaN;

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String worldStructure;
    @Nullable private String reviveStyle;
    @Nullable private String inventoryMode;
    @Nullable private String hunterArchetype;
    @Nullable private String[] mutators;

    private int maxDowns = UNSET_INT;
    private int bleedOutSeconds = UNSET_INT;
    private int hunterCount = UNSET_INT;
    private double hunterSpeedBase = UNSET_DOUBLE;
    private double hunterSpeedMax = UNSET_DOUBLE;
    private int shrineBase = UNSET_INT;
    private int shrinePerPlayer = UNSET_INT;
    private int caveShrineCount = UNSET_INT;
    private int roundCapSeconds = UNSET_INT;
    private double corruptionPerSecond = UNSET_DOUBLE;
    private double corruptionPerShrine = UNSET_DOUBLE;
    private double shrineRelightSeconds = UNSET_DOUBLE;
    // 1.4.0 Moonbloom loop knobs.
    private int cleanseCost = UNSET_INT;
    private long stunDurationMs = UNSET_LONG;
    // Per-throwable damage overrides keyed by DamageCause id (null = absent = each burst's authored damage stands).
    @Nullable private Map<String, Double> throwableDamage;
    @Nullable private String throwMode;
    private int moonbloomPerShrine = UNSET_INT;
    private int moonbloomScatter = UNSET_INT;
    private int moonbloomRespawnCount = UNSET_INT;
    @Nullable private int[] moonbloomRespawnAtSeconds;
    // Matchmaking party knobs (the queue's min/max seats; max clamps to the arena budget).
    private int minParty = UNSET_INT;
    private int maxParty = UNSET_INT;
    // Exit map marker toggle (null = absent = the RuleSet default = on; Hardcore authors false).
    @Nullable private Boolean exitMarker;
    // Shrine-discovery marker knobs (null/NaN = absent = the RuleSet default). ShrineDiscovery is the
    // trigger (OFF/ON_INTERACT/PROXIMITY); ShrineDiscoveryVisibility is who sees it (SELF/ALL aliases,
    // or PER_PLAYER/SHARED); ShrineDiscoveryRadius is the PROXIMITY reveal distance.
    @Nullable private String shrineDiscovery;
    @Nullable private String shrineDiscoveryVisibility;
    private double shrineDiscoveryRadius = UNSET_DOUBLE;
    // Co-op extraction knobs (null/NaN = absent = the RuleSet default). ExtractionMode is WHO must hold
    // the platform (ALL_MOBILE / EVERYONE); ExtractionHoldSeconds is how long the group holds it together.
    @Nullable private String extractionMode;
    private double extractionHoldSeconds = UNSET_DOUBLE;
    // Boss capstone toggle + id (null = absent = the RuleSet default = off / default Warden). The harder
    // presets author BossEnabled=true; BossId selects a non-default boss from the BossConfig fold;
    // BossBarsGate=true holds the Heartwood Gate shut until the boss is defeated (else the boss is a
    // pure obstacle beside an already-open gate).
    @Nullable private Boolean bossEnabled;
    @Nullable private String bossId;
    @Nullable private Boolean bossBarsGate;
    // Jumpscare beat knobs (null/UNSET = absent = the RuleSet default). JumpscareEnabled toggles the
    // proximity/alert scare; JumpscareBeatId names a ziggfreed-common BandedEffect one-shot (overlay
    // EntityEffect + scream + camera shake) so a preset can use a different overlay/intensity per mode;
    // JumpscareShakeIntensity overrides the beat's shake strength; JumpscareCooldownSeconds throttles it.
    @Nullable private Boolean jumpscareEnabled;
    @Nullable private String jumpscareBeatId;
    private double jumpscareShakeIntensity = UNSET_DOUBLE;
    private int jumpscareCooldownSeconds = UNSET_INT;
    // Win fanfare SoundEvent id played to each survivor on a round win (null/blank = the RuleSet default).
    @Nullable private String winSoundId;
    // Per-preset scoring weights (each absent = the ScoringConfig default). The asset-driven
    // scoring calculation: a preset may reward speed/caution/aggression/devotion differently.
    private int scoreBaseline = UNSET_INT;
    private int parTimeSeconds = UNSET_INT;
    private double timePointsPerSecond = UNSET_DOUBLE;
    private double damagePointsPerHp = UNSET_DOUBLE;
    private double damageBudget = UNSET_DOUBLE;
    private int stunBonusPer = UNSET_INT;
    private int shrineBonusPer = UNSET_INT;
    private int allShrinesBonus = UNSET_INT;
    // --- PvP shared (Clash + Domination): null/UNSET = absent = the RuleSet default ---
    private int teamSize = UNSET_INT;
    @Nullable private Boolean friendlyFire;
    @Nullable private String modelSwapId;
    private double modelSwapScale = UNSET_DOUBLE;
    @Nullable private String arenaId;
    @Nullable private String arenaTag;
    // --- Clash knobs ---
    @Nullable private String winCondition;
    @Nullable private String respawnPolicy;
    private int respawnDelaySeconds = UNSET_INT;
    private int maxLives = UNSET_INT;
    private int scoreToWin = UNSET_INT;
    private int suddenDeathSeconds = UNSET_INT;
    private int mushroomCadenceSeconds = UNSET_INT;
    private int mushroomWaveCount = UNSET_INT;
    private int mushroomMaxAlive = UNSET_INT;
    @Nullable private String[] mushroomCycle;
    // --- Domination knobs ---
    private int dominationScoreToWin = UNSET_INT;
    private double dominationPointHoldSeconds = UNSET_DOUBLE;
    private int dominationAccrualPerSecond = UNSET_INT;
    private double dominationPointRadius = UNSET_DOUBLE;
    @Nullable private String dominationContestRule;
    @Nullable private Boolean dominationCaptureNeutralizes;
    private int dominationRespawnDelaySeconds = UNSET_INT;

    public static final AssetBuilderCodec<String, RoundPresetAsset> CODEC = AssetBuilderCodec.builder(
                    RoundPresetAsset.class,
                    RoundPresetAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Name is an optional human-readable echo of the asset key (the
            // authoritative key is the filename) - consumed by a no-op setter so it
            // doesn't trip the "Unused key(s)" warning, and emitted on encode.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("WorldStructure", Codec.STRING, false), (a, v) -> a.worldStructure = v, a -> a.worldStructure)
            .add()
            .append(new KeyedCodec<>("ReviveStyle", Codec.STRING, false), (a, v) -> a.reviveStyle = v, a -> a.reviveStyle)
            .add()
            .append(new KeyedCodec<>("MaxDowns", Codec.INTEGER, false), (a, v) -> a.maxDowns = v, a -> a.maxDowns)
            .add()
            .append(new KeyedCodec<>("BleedOutSeconds", Codec.INTEGER, false), (a, v) -> a.bleedOutSeconds = v, a -> a.bleedOutSeconds)
            .add()
            .append(new KeyedCodec<>("HunterCount", Codec.INTEGER, false), (a, v) -> a.hunterCount = v, a -> a.hunterCount)
            .add()
            .append(new KeyedCodec<>("HunterSpeedBase", Codec.DOUBLE, false), (a, v) -> a.hunterSpeedBase = v, a -> a.hunterSpeedBase)
            .add()
            .append(new KeyedCodec<>("HunterSpeedMax", Codec.DOUBLE, false), (a, v) -> a.hunterSpeedMax = v, a -> a.hunterSpeedMax)
            .add()
            .append(new KeyedCodec<>("ShrineBase", Codec.INTEGER, false), (a, v) -> a.shrineBase = v, a -> a.shrineBase)
            .add()
            .append(new KeyedCodec<>("ShrinePerPlayer", Codec.INTEGER, false), (a, v) -> a.shrinePerPlayer = v, a -> a.shrinePerPlayer)
            .add()
            .append(new KeyedCodec<>("CaveShrineCount", Codec.INTEGER, false), (a, v) -> a.caveShrineCount = v, a -> a.caveShrineCount)
            .add()
            .append(new KeyedCodec<>("RoundCapSeconds", Codec.INTEGER, false), (a, v) -> a.roundCapSeconds = v, a -> a.roundCapSeconds)
            .add()
            .append(new KeyedCodec<>("CorruptionPerSecond", Codec.DOUBLE, false), (a, v) -> a.corruptionPerSecond = v, a -> a.corruptionPerSecond)
            .add()
            .append(new KeyedCodec<>("CorruptionPerShrine", Codec.DOUBLE, false), (a, v) -> a.corruptionPerShrine = v, a -> a.corruptionPerShrine)
            .add()
            .append(new KeyedCodec<>("ShrineRelightSeconds", Codec.DOUBLE, false), (a, v) -> a.shrineRelightSeconds = v, a -> a.shrineRelightSeconds)
            .add()
            .append(new KeyedCodec<>("InventoryMode", Codec.STRING, false), (a, v) -> a.inventoryMode = v, a -> a.inventoryMode)
            .add()
            .append(new KeyedCodec<>("HunterArchetype", Codec.STRING, false), (a, v) -> a.hunterArchetype = v, a -> a.hunterArchetype)
            .add()
            .append(new KeyedCodec<>("Mutators", Codec.STRING_ARRAY, false), (a, v) -> a.mutators = v, a -> a.mutators)
            .add()
            .append(new KeyedCodec<>("CleanseCost", Codec.INTEGER, false), (a, v) -> a.cleanseCost = v, a -> a.cleanseCost)
            .add()
            .append(new KeyedCodec<>("StunDurationMs", Codec.LONG, false), (a, v) -> a.stunDurationMs = v, a -> a.stunDurationMs)
            .add()
            .append(new KeyedCodec<>("ThrowableDamage", new MapCodec<>(Codec.DOUBLE, HashMap::new), false), (a, v) -> a.throwableDamage = v, a -> a.throwableDamage)
            .add()
            .append(new KeyedCodec<>("ThrowMode", Codec.STRING, false), (a, v) -> a.throwMode = v, a -> a.throwMode)
            .add()
            .append(new KeyedCodec<>("MoonbloomPerShrine", Codec.INTEGER, false), (a, v) -> a.moonbloomPerShrine = v, a -> a.moonbloomPerShrine)
            .add()
            .append(new KeyedCodec<>("MoonbloomScatter", Codec.INTEGER, false), (a, v) -> a.moonbloomScatter = v, a -> a.moonbloomScatter)
            .add()
            .append(new KeyedCodec<>("MoonbloomRespawnCount", Codec.INTEGER, false), (a, v) -> a.moonbloomRespawnCount = v, a -> a.moonbloomRespawnCount)
            .add()
            .append(new KeyedCodec<>("MoonbloomRespawnAtSeconds", Codec.INT_ARRAY, false), (a, v) -> a.moonbloomRespawnAtSeconds = v, a -> a.moonbloomRespawnAtSeconds)
            .add()
            .append(new KeyedCodec<>("MinParty", Codec.INTEGER, false), (a, v) -> a.minParty = v, a -> a.minParty)
            .add()
            .append(new KeyedCodec<>("MaxParty", Codec.INTEGER, false), (a, v) -> a.maxParty = v, a -> a.maxParty)
            .add()
            .append(new KeyedCodec<>("ExitMarker", Codec.BOOLEAN, false), (a, v) -> a.exitMarker = v, a -> a.exitMarker)
            .add()
            .append(new KeyedCodec<>("ShrineDiscovery", Codec.STRING, false), (a, v) -> a.shrineDiscovery = v, a -> a.shrineDiscovery)
            .add()
            .append(new KeyedCodec<>("ShrineDiscoveryVisibility", Codec.STRING, false), (a, v) -> a.shrineDiscoveryVisibility = v, a -> a.shrineDiscoveryVisibility)
            .add()
            .append(new KeyedCodec<>("ShrineDiscoveryRadius", Codec.DOUBLE, false), (a, v) -> a.shrineDiscoveryRadius = v, a -> a.shrineDiscoveryRadius)
            .add()
            .append(new KeyedCodec<>("ExtractionMode", Codec.STRING, false), (a, v) -> a.extractionMode = v, a -> a.extractionMode)
            .add()
            .append(new KeyedCodec<>("ExtractionHoldSeconds", Codec.DOUBLE, false), (a, v) -> a.extractionHoldSeconds = v, a -> a.extractionHoldSeconds)
            .add()
            .append(new KeyedCodec<>("BossEnabled", Codec.BOOLEAN, false), (a, v) -> a.bossEnabled = v, a -> a.bossEnabled)
            .add()
            .append(new KeyedCodec<>("BossId", Codec.STRING, false), (a, v) -> a.bossId = v, a -> a.bossId)
            .add()
            .append(new KeyedCodec<>("BossBarsGate", Codec.BOOLEAN, false), (a, v) -> a.bossBarsGate = v, a -> a.bossBarsGate)
            .add()
            .append(new KeyedCodec<>("JumpscareEnabled", Codec.BOOLEAN, false), (a, v) -> a.jumpscareEnabled = v, a -> a.jumpscareEnabled)
            .add()
            .append(new KeyedCodec<>("JumpscareBeatId", Codec.STRING, false), (a, v) -> a.jumpscareBeatId = v, a -> a.jumpscareBeatId)
            .add()
            .append(new KeyedCodec<>("JumpscareShakeIntensity", Codec.DOUBLE, false), (a, v) -> a.jumpscareShakeIntensity = v, a -> a.jumpscareShakeIntensity)
            .add()
            .append(new KeyedCodec<>("JumpscareCooldownSeconds", Codec.INTEGER, false), (a, v) -> a.jumpscareCooldownSeconds = v, a -> a.jumpscareCooldownSeconds)
            .add()
            .append(new KeyedCodec<>("WinSoundId", Codec.STRING, false), (a, v) -> a.winSoundId = v, a -> a.winSoundId)
            .add()
            .append(new KeyedCodec<>("Baseline", Codec.INTEGER, false), (a, v) -> a.scoreBaseline = v, a -> a.scoreBaseline)
            .add()
            .append(new KeyedCodec<>("ParTimeSeconds", Codec.INTEGER, false), (a, v) -> a.parTimeSeconds = v, a -> a.parTimeSeconds)
            .add()
            .append(new KeyedCodec<>("TimePointsPerSecond", Codec.DOUBLE, false), (a, v) -> a.timePointsPerSecond = v, a -> a.timePointsPerSecond)
            .add()
            .append(new KeyedCodec<>("DamagePointsPerHp", Codec.DOUBLE, false), (a, v) -> a.damagePointsPerHp = v, a -> a.damagePointsPerHp)
            .add()
            .append(new KeyedCodec<>("DamageBudget", Codec.DOUBLE, false), (a, v) -> a.damageBudget = v, a -> a.damageBudget)
            .add()
            .append(new KeyedCodec<>("StunBonusPer", Codec.INTEGER, false), (a, v) -> a.stunBonusPer = v, a -> a.stunBonusPer)
            .add()
            .append(new KeyedCodec<>("ShrineBonusPer", Codec.INTEGER, false), (a, v) -> a.shrineBonusPer = v, a -> a.shrineBonusPer)
            .add()
            .append(new KeyedCodec<>("AllShrinesBonus", Codec.INTEGER, false), (a, v) -> a.allShrinesBonus = v, a -> a.allShrinesBonus)
            .add()
            // --- PvP shared (Clash + Domination) ---
            .append(new KeyedCodec<>("TeamSize", Codec.INTEGER, false), (a, v) -> a.teamSize = v, a -> a.teamSize)
            .add()
            .append(new KeyedCodec<>("FriendlyFire", Codec.BOOLEAN, false), (a, v) -> a.friendlyFire = v, a -> a.friendlyFire)
            .add()
            .append(new KeyedCodec<>("ModelSwapId", Codec.STRING, false), (a, v) -> a.modelSwapId = v, a -> a.modelSwapId)
            .add()
            .append(new KeyedCodec<>("ModelSwapScale", Codec.DOUBLE, false), (a, v) -> a.modelSwapScale = v, a -> a.modelSwapScale)
            .add()
            .append(new KeyedCodec<>("ArenaId", Codec.STRING, false), (a, v) -> a.arenaId = v, a -> a.arenaId)
            .add()
            .append(new KeyedCodec<>("ArenaTag", Codec.STRING, false), (a, v) -> a.arenaTag = v, a -> a.arenaTag)
            .add()
            // --- Clash ---
            .append(new KeyedCodec<>("WinCondition", Codec.STRING, false), (a, v) -> a.winCondition = v, a -> a.winCondition)
            .add()
            .append(new KeyedCodec<>("RespawnPolicy", Codec.STRING, false), (a, v) -> a.respawnPolicy = v, a -> a.respawnPolicy)
            .add()
            .append(new KeyedCodec<>("RespawnDelaySeconds", Codec.INTEGER, false), (a, v) -> a.respawnDelaySeconds = v, a -> a.respawnDelaySeconds)
            .add()
            .append(new KeyedCodec<>("MaxLives", Codec.INTEGER, false), (a, v) -> a.maxLives = v, a -> a.maxLives)
            .add()
            .append(new KeyedCodec<>("ScoreToWin", Codec.INTEGER, false), (a, v) -> a.scoreToWin = v, a -> a.scoreToWin)
            .add()
            .append(new KeyedCodec<>("SuddenDeathSeconds", Codec.INTEGER, false), (a, v) -> a.suddenDeathSeconds = v, a -> a.suddenDeathSeconds)
            .add()
            .append(new KeyedCodec<>("MushroomCadenceSeconds", Codec.INTEGER, false), (a, v) -> a.mushroomCadenceSeconds = v, a -> a.mushroomCadenceSeconds)
            .add()
            .append(new KeyedCodec<>("MushroomWaveCount", Codec.INTEGER, false), (a, v) -> a.mushroomWaveCount = v, a -> a.mushroomWaveCount)
            .add()
            .append(new KeyedCodec<>("MushroomMaxAlive", Codec.INTEGER, false), (a, v) -> a.mushroomMaxAlive = v, a -> a.mushroomMaxAlive)
            .add()
            .append(new KeyedCodec<>("MushroomCycle", Codec.STRING_ARRAY, false), (a, v) -> a.mushroomCycle = v, a -> a.mushroomCycle)
            .add()
            // --- Domination ---
            .append(new KeyedCodec<>("DominationScoreToWin", Codec.INTEGER, false), (a, v) -> a.dominationScoreToWin = v, a -> a.dominationScoreToWin)
            .add()
            .append(new KeyedCodec<>("DominationPointHoldSeconds", Codec.DOUBLE, false), (a, v) -> a.dominationPointHoldSeconds = v, a -> a.dominationPointHoldSeconds)
            .add()
            .append(new KeyedCodec<>("DominationAccrualPerSecond", Codec.INTEGER, false), (a, v) -> a.dominationAccrualPerSecond = v, a -> a.dominationAccrualPerSecond)
            .add()
            .append(new KeyedCodec<>("DominationPointRadius", Codec.DOUBLE, false), (a, v) -> a.dominationPointRadius = v, a -> a.dominationPointRadius)
            .add()
            .append(new KeyedCodec<>("DominationContestRule", Codec.STRING, false), (a, v) -> a.dominationContestRule = v, a -> a.dominationContestRule)
            .add()
            .append(new KeyedCodec<>("DominationCaptureNeutralizes", Codec.BOOLEAN, false), (a, v) -> a.dominationCaptureNeutralizes = v, a -> a.dominationCaptureNeutralizes)
            .add()
            .append(new KeyedCodec<>("DominationRespawnDelaySeconds", Codec.INTEGER, false), (a, v) -> a.dominationRespawnDelaySeconds = v, a -> a.dominationRespawnDelaySeconds)
            .add()
            .build();

    public RoundPresetAsset() {
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Ids of the {@link MutatorAsset}s this preset applies on top of its base
     * {@link RuleSet} (resolved + stacked in {@link PresetConfig#resolve(String)}).
     * An empty array (the default) means a plain preset with no mutators; an unknown
     * id is skipped during the fold. The deltas are additive + commutative, so list
     * order does not matter.
     */
    @Nonnull
    public String[] mutators() {
        return mutators != null ? mutators : new String[0];
    }

    /**
     * Build the unchanged runtime {@link RuleSet} from this asset's fields. Any knob
     * left UNSET (absent in the JSON) keeps the {@link RuleSet.Builder} default (the
     * Nightmare baseline), so a partial preset only overrides what it authors.
     *
     * @param presetId the preset id (asset key on the pack layer, map key on the owner layer)
     */
    @Nonnull
    public RuleSet toRuleSet(@Nonnull String presetId) {
        RuleSet.Builder b = RuleSet.builder(presetId.toLowerCase());
        if (worldStructure != null && !worldStructure.isBlank()) {
            b.worldStructure(worldStructure);
        }
        if (reviveStyle != null) {
            b.reviveStyle(ReviveStyle.fromString(reviveStyle));
        }
        if (maxDowns != UNSET_INT) {
            b.maxDowns(maxDowns);
        }
        if (bleedOutSeconds != UNSET_INT) {
            b.bleedOutSeconds(bleedOutSeconds);
        }
        if (hunterCount != UNSET_INT) {
            b.hunterCount(hunterCount);
        }
        // hunterSpeed is a paired setter; only override when at least one band is authored.
        if (!Double.isNaN(hunterSpeedBase) || !Double.isNaN(hunterSpeedMax)) {
            double base = Double.isNaN(hunterSpeedBase) ? 1.0 : hunterSpeedBase;
            double max = Double.isNaN(hunterSpeedMax) ? base : hunterSpeedMax;
            b.hunterSpeed(base, max);
        }
        // shrines is a paired setter; only override when at least one is authored.
        if (shrineBase != UNSET_INT || shrinePerPlayer != UNSET_INT) {
            int base = shrineBase != UNSET_INT ? shrineBase : 2;
            int per = shrinePerPlayer != UNSET_INT ? shrinePerPlayer : 1;
            b.shrines(base, per);
        }
        if (caveShrineCount != UNSET_INT) {
            b.caveShrineCount(caveShrineCount);
        }
        if (roundCapSeconds != UNSET_INT) {
            b.roundCapSeconds(roundCapSeconds);
        }
        if (!Double.isNaN(corruptionPerSecond)) {
            b.corruptionPerSecond(corruptionPerSecond);
        }
        if (!Double.isNaN(corruptionPerShrine)) {
            b.corruptionPerShrine(corruptionPerShrine);
        }
        if (!Double.isNaN(shrineRelightSeconds)) {
            b.shrineRelightSeconds(shrineRelightSeconds);
        }
        b.inventoryMode(InventoryMode.fromString(inventoryMode));
        if (hunterArchetype != null && !hunterArchetype.isBlank()) {
            b.hunterArchetype(hunterArchetype.toLowerCase());
        }
        // Moonbloom loop knobs (each absent = the RuleSet builder default).
        if (cleanseCost != UNSET_INT) {
            b.cleanseCost(cleanseCost);
        }
        if (stunDurationMs != UNSET_LONG) {
            b.stunDurationMs(stunDurationMs);
        }
        if (throwableDamage != null && !throwableDamage.isEmpty()) {
            b.throwableDamage(throwableDamage);
        }
        if (throwMode != null && !throwMode.isBlank()) {
            b.throwMode(ThrowMode.fromString(throwMode));
        }
        if (moonbloomPerShrine != UNSET_INT) {
            b.moonbloomPerShrine(moonbloomPerShrine);
        }
        if (moonbloomScatter != UNSET_INT) {
            b.moonbloomScatter(moonbloomScatter);
        }
        if (moonbloomRespawnCount != UNSET_INT) {
            b.moonbloomRespawnCount(moonbloomRespawnCount);
        }
        if (moonbloomRespawnAtSeconds != null) {
            b.moonbloomRespawnAtSeconds(moonbloomRespawnAtSeconds);
        }
        if (minParty != UNSET_INT) {
            b.minParty(minParty);
        }
        if (maxParty != UNSET_INT) {
            b.maxParty(maxParty);
        }
        if (exitMarker != null) {
            b.exitMarker(exitMarker);
        }
        if (shrineDiscovery != null && !shrineDiscovery.isBlank()) {
            try {
                b.shrineDiscovery(DiscoveryMode.valueOf(shrineDiscovery.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown trigger -> keep the builder default (ON_INTERACT).
            }
        }
        MapDiscovery.Visibility vis = parseVisibility(shrineDiscoveryVisibility);
        if (vis != null) {
            b.shrineDiscoveryVisibility(vis);
        }
        if (!Double.isNaN(shrineDiscoveryRadius)) {
            b.shrineDiscoveryRadius(shrineDiscoveryRadius);
        }
        if (extractionMode != null && !extractionMode.isBlank()) {
            b.extractionMode(ExtractionMode.fromString(extractionMode));
        }
        if (!Double.isNaN(extractionHoldSeconds)) {
            b.extractionHoldSeconds(extractionHoldSeconds);
        }
        if (bossEnabled != null) {
            b.bossEnabled(bossEnabled);
        }
        if (bossId != null && !bossId.isBlank()) {
            b.bossId(bossId.toLowerCase());
        }
        if (bossBarsGate != null) {
            b.bossBarsGate(bossBarsGate);
        }
        if (jumpscareEnabled != null) {
            b.jumpscareEnabled(jumpscareEnabled);
        }
        if (jumpscareBeatId != null && !jumpscareBeatId.isBlank()) {
            b.jumpscareBeatId(jumpscareBeatId);
        }
        if (!Double.isNaN(jumpscareShakeIntensity)) {
            b.jumpscareShakeIntensity(jumpscareShakeIntensity);
        }
        if (jumpscareCooldownSeconds != UNSET_INT) {
            b.jumpscareCooldownSeconds(jumpscareCooldownSeconds);
        }
        if (winSoundId != null && !winSoundId.isBlank()) {
            b.winSoundId(winSoundId);
        }
        // --- PvP shared (Clash + Domination) ---
        if (teamSize != UNSET_INT) {
            b.teamSize(teamSize);
        }
        if (friendlyFire != null) {
            b.friendlyFire(friendlyFire);
        }
        if (modelSwapId != null) {
            b.modelSwapId(modelSwapId);
        }
        if (!Double.isNaN(modelSwapScale)) {
            b.modelSwapScale(modelSwapScale);
        }
        if (arenaId != null && !arenaId.isBlank()) {
            b.arenaId(arenaId);
        }
        if (arenaTag != null && !arenaTag.isBlank()) {
            b.arenaTag(arenaTag);
        }
        // --- Clash ---
        if (winCondition != null && !winCondition.isBlank()) {
            b.winCondition(WinCondition.fromString(winCondition));
        }
        if (respawnPolicy != null && !respawnPolicy.isBlank()) {
            b.respawnPolicy(RespawnPolicy.fromString(respawnPolicy));
        }
        if (respawnDelaySeconds != UNSET_INT) {
            b.respawnDelaySeconds(respawnDelaySeconds);
        }
        if (maxLives != UNSET_INT) {
            b.maxLives(maxLives);
        }
        if (scoreToWin != UNSET_INT) {
            b.scoreToWin(scoreToWin);
        }
        if (suddenDeathSeconds != UNSET_INT) {
            b.suddenDeathSeconds(suddenDeathSeconds);
        }
        if (mushroomCadenceSeconds != UNSET_INT) {
            b.mushroomCadenceSeconds(mushroomCadenceSeconds);
        }
        if (mushroomWaveCount != UNSET_INT) {
            b.mushroomWaveCount(mushroomWaveCount);
        }
        if (mushroomMaxAlive != UNSET_INT) {
            b.mushroomMaxAlive(mushroomMaxAlive);
        }
        if (mushroomCycle != null) {
            b.mushroomCycle(mushroomCycle);
        }
        // --- Domination ---
        if (dominationScoreToWin != UNSET_INT) {
            b.dominationScoreToWin(dominationScoreToWin);
        }
        if (!Double.isNaN(dominationPointHoldSeconds)) {
            b.dominationPointHoldSeconds(dominationPointHoldSeconds);
        }
        if (dominationAccrualPerSecond != UNSET_INT) {
            b.dominationAccrualPerSecond(dominationAccrualPerSecond);
        }
        if (!Double.isNaN(dominationPointRadius)) {
            b.dominationPointRadius(dominationPointRadius);
        }
        if (dominationContestRule != null && !dominationContestRule.isBlank()) {
            b.dominationContestRule(ContestRule.valueOf(dominationContestRule.trim().toUpperCase()));
        }
        if (dominationCaptureNeutralizes != null) {
            b.dominationCaptureNeutralizes(dominationCaptureNeutralizes);
        }
        if (dominationRespawnDelaySeconds != UNSET_INT) {
            b.dominationRespawnDelaySeconds(dominationRespawnDelaySeconds);
        }
        b.scoring(buildScoring());
        return b.build();
    }

    /**
     * Parse the {@code ShrineDiscoveryVisibility} knob: the author's {@code SELF}/{@code ALL} vocabulary
     * (plus the {@code PER_PLAYER}/{@code SHARED} enum names), case-insensitive. Returns {@code null} for
     * absent / blank / unknown so the caller keeps the {@link RuleSet} builder default.
     */
    @Nullable
    private static MapDiscovery.Visibility parseVisibility(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        switch (raw.trim().toUpperCase()) {
            case "SELF":
            case "PER_PLAYER":
                return MapDiscovery.Visibility.PER_PLAYER;
            case "ALL":
            case "SHARED":
                return MapDiscovery.Visibility.SHARED;
            default:
                return null;
        }
    }

    /**
     * Build this preset's {@link ScoringConfig} from its authored scoring knobs, each absent field
     * keeping the {@link ScoringConfig} default. Returns {@link ScoringConfig#DEFAULT} unchanged when
     * the preset authors no scoring (the common case).
     */
    @Nonnull
    private ScoringConfig buildScoring() {
        ScoringConfig.Builder s = ScoringConfig.DEFAULT.toBuilder();
        if (scoreBaseline != UNSET_INT) {
            s.baseline(scoreBaseline);
        }
        if (parTimeSeconds != UNSET_INT) {
            s.parTimeSeconds(parTimeSeconds);
        }
        if (!Double.isNaN(timePointsPerSecond)) {
            s.timePointsPerSecond(timePointsPerSecond);
        }
        if (!Double.isNaN(damagePointsPerHp)) {
            s.damagePointsPerHp(damagePointsPerHp);
        }
        if (!Double.isNaN(damageBudget)) {
            s.damageBudget(damageBudget);
        }
        if (stunBonusPer != UNSET_INT) {
            s.stunBonusPer(stunBonusPer);
        }
        if (shrineBonusPer != UNSET_INT) {
            s.shrineBonusPer(shrineBonusPer);
        }
        if (allShrinesBonus != UNSET_INT) {
            s.allShrinesBonus(allShrinesBonus);
        }
        return s.build();
    }
}
