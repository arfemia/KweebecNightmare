package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

/**
 * A pack-authorable hunter archetype, loaded from a pack's
 * {@code Server/KweebecNightmare/Hunters/*.json}. The data source for Phase-2A
 * hunter variety: each archetype binds an NPC role + its weighted roster slot + the
 * corruption-scaled speed bands and their pre-authored pace effects.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors hyMMO's {@code QuestGiverAsset}).
 * Authored NOW, ahead of its consumer, so the Phase-2A roster reads it as DATA
 * rather than hardcode-then-refactor (the repo DRY rule). {@code AiHunterController}
 * does NOT consume it this pass; it is generalized to the asset roster in Phase 2A.
 * The shipped {@code Stalker.json} reproduces today's single hardcoded hunter
 * exactly ({@code KweebecNightmare_Blight}, count 1, the existing speed-band ladder).
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start).
 *
 * <p>Pack JSON shape ({@code SpeedBands} and {@code BandEffectIds} are parallel
 * arrays - index i's multiplier maps to index i's pace effect id; an empty string
 * effect id = the role's 1.0x baseline, no effect):
 * <pre>{@code
 * { "Name": "stalker", "Kind": "stalker", "RoleName": "KweebecNightmare_Blight",
 *   "Count": 1, "Weight": 1.0, "SpawnTier": 0,
 *   "SpeedBands": [0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5],
 *   "BandEffectIds": ["KweebecNightmare_HunterPace_090", "",
 *                     "KweebecNightmare_HunterPace_110", "KweebecNightmare_HunterPace_120",
 *                     "KweebecNightmare_HunterPace_130", "KweebecNightmare_HunterPace_140",
 *                     "KweebecNightmare_HunterPace_150"] }
 * }</pre>
 */
public final class HunterArchetypeAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, HunterArchetypeAsset>> {

    /** Behavior family of a hunter archetype; authored as the {@code Kind} string. */
    public enum Kind {
        /** Baseline relentless pursuer (today's only hunter). */
        STALKER,
        /** Charge/lunge attacker (higher pace, lower HP). */
        LUNGER,
        /** Ranged spitter that kites and maintains distance. */
        SPITTER,
        /** Teleport/blink ambusher (low HP, fast). */
        AMBUSHER;

        /** The kind chosen when none is authored. */
        public static final Kind DEFAULT = STALKER;

        @Nonnull
        public static Kind fromString(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return DEFAULT;
            }
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(s.trim())) {
                    return k;
                }
            }
            return DEFAULT;
        }
    }

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String kind;
    @Nullable private String roleName;
    private int count = 1;
    private double weight = 1.0;
    private int spawnTier = 0;
    @Nullable private double[] speedBands;
    @Nullable private String[] bandEffectIds;

    // --- on-hit punishment (per-archetype override of the RuleSet baseline) ---
    // A non-null/non-zero value here WINS over the RuleSet default; an absent / zero
    // field defers to the rule-set baseline (see HunterController.resolveOnHitConfigFor).
    @Nullable private String onHitSlowEffectId;
    private double onHitSlowSeconds = 0.0;
    private double onHitDamageMult = 0.0;
    private double onHitDamageFlat = 0.0;
    private int onHitStackCap = 0;
    private double onHitStackWindowSeconds = 0.0;
    private double enrageAfterSeconds = 0.0;
    private double enrageSpeedMult = 0.0;
    private double enrageDamageMult = 0.0;
    private double enrageDurationSeconds = 0.0;
    @Nullable private String enrageSoundId;

    public static final AssetBuilderCodec<String, HunterArchetypeAsset> CODEC = AssetBuilderCodec.builder(
                    HunterArchetypeAsset.class,
                    HunterArchetypeAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("Kind", Codec.STRING, false), (a, v) -> a.kind = v, a -> a.kind)
            .add()
            .append(new KeyedCodec<>("RoleName", Codec.STRING, false), (a, v) -> a.roleName = v, a -> a.roleName)
            .add()
            .append(new KeyedCodec<>("Count", Codec.INTEGER, false), (a, v) -> a.count = v, a -> a.count)
            .add()
            .append(new KeyedCodec<>("Weight", Codec.DOUBLE, false), (a, v) -> a.weight = v, a -> a.weight)
            .add()
            .append(new KeyedCodec<>("SpawnTier", Codec.INTEGER, false), (a, v) -> a.spawnTier = v, a -> a.spawnTier)
            .add()
            .append(new KeyedCodec<>("SpeedBands", Codec.DOUBLE_ARRAY, false), (a, v) -> a.speedBands = v, a -> a.speedBands)
            .add()
            .append(new KeyedCodec<>("BandEffectIds", Codec.STRING_ARRAY, false), (a, v) -> a.bandEffectIds = v, a -> a.bandEffectIds)
            .add()
            .append(new KeyedCodec<>("OnHitSlowEffectId", Codec.STRING, false), (a, v) -> a.onHitSlowEffectId = v, a -> a.onHitSlowEffectId)
            .add()
            .append(new KeyedCodec<>("OnHitSlowSeconds", Codec.DOUBLE, false), (a, v) -> a.onHitSlowSeconds = v, a -> a.onHitSlowSeconds)
            .add()
            .append(new KeyedCodec<>("OnHitDamageMult", Codec.DOUBLE, false), (a, v) -> a.onHitDamageMult = v, a -> a.onHitDamageMult)
            .add()
            .append(new KeyedCodec<>("OnHitDamageFlat", Codec.DOUBLE, false), (a, v) -> a.onHitDamageFlat = v, a -> a.onHitDamageFlat)
            .add()
            .append(new KeyedCodec<>("OnHitStackCap", Codec.INTEGER, false), (a, v) -> a.onHitStackCap = v, a -> a.onHitStackCap)
            .add()
            .append(new KeyedCodec<>("OnHitStackWindowSeconds", Codec.DOUBLE, false), (a, v) -> a.onHitStackWindowSeconds = v, a -> a.onHitStackWindowSeconds)
            .add()
            .append(new KeyedCodec<>("EnrageAfterSeconds", Codec.DOUBLE, false), (a, v) -> a.enrageAfterSeconds = v, a -> a.enrageAfterSeconds)
            .add()
            .append(new KeyedCodec<>("EnrageSpeedMult", Codec.DOUBLE, false), (a, v) -> a.enrageSpeedMult = v, a -> a.enrageSpeedMult)
            .add()
            .append(new KeyedCodec<>("EnrageDamageMult", Codec.DOUBLE, false), (a, v) -> a.enrageDamageMult = v, a -> a.enrageDamageMult)
            .add()
            .append(new KeyedCodec<>("EnrageDurationSeconds", Codec.DOUBLE, false), (a, v) -> a.enrageDurationSeconds = v, a -> a.enrageDurationSeconds)
            .add()
            .append(new KeyedCodec<>("EnrageSoundId", Codec.STRING, false), (a, v) -> a.enrageSoundId = v, a -> a.enrageSoundId)
            .add()
            .build();

    public HunterArchetypeAsset() {
    }

    /**
     * Build an archetype in code (the jar's {@code defaults} floor), without going
     * through the JSON {@link #CODEC}. The shipped {@code Stalker.json} authors the
     * same fields; {@link DefaultHunters} is the zero-pack source of truth.
     */
    @Nonnull
    static HunterArchetypeAsset of(@Nonnull String id, @Nullable String kind, @Nullable String roleName,
                                   int count, double weight, int spawnTier,
                                   @Nullable double[] speedBands, @Nullable String[] bandEffectIds) {
        HunterArchetypeAsset a = new HunterArchetypeAsset();
        a.id = id;
        a.kind = kind;
        a.roleName = roleName;
        a.count = count;
        a.weight = weight;
        a.spawnTier = spawnTier;
        a.speedBands = speedBands;
        a.bandEffectIds = bandEffectIds;
        return a;
    }

    /**
     * Build an archetype in code WITH on-hit punishment overrides set (the
     * {@link DefaultHunters} floor for an archetype that punishes melee). The
     * non-on-hit args mirror {@link #of(String, String, String, int, double, int,
     * double[], String[])}; the on-hit args mirror the per-archetype fields (a zero /
     * null value defers to the rule-set baseline).
     */
    @Nonnull
    static HunterArchetypeAsset of(@Nonnull String id, @Nullable String kind, @Nullable String roleName,
                                   int count, double weight, int spawnTier,
                                   @Nullable double[] speedBands, @Nullable String[] bandEffectIds,
                                   @Nullable String onHitSlowEffectId, double onHitSlowSeconds,
                                   double onHitDamageMult, double onHitDamageFlat, int onHitStackCap,
                                   double onHitStackWindowSeconds, double enrageAfterSeconds,
                                   double enrageSpeedMult, double enrageDamageMult,
                                   double enrageDurationSeconds, @Nullable String enrageSoundId) {
        HunterArchetypeAsset a = of(id, kind, roleName, count, weight, spawnTier, speedBands, bandEffectIds);
        a.onHitSlowEffectId = onHitSlowEffectId;
        a.onHitSlowSeconds = onHitSlowSeconds;
        a.onHitDamageMult = onHitDamageMult;
        a.onHitDamageFlat = onHitDamageFlat;
        a.onHitStackCap = onHitStackCap;
        a.onHitStackWindowSeconds = onHitStackWindowSeconds;
        a.enrageAfterSeconds = enrageAfterSeconds;
        a.enrageSpeedMult = enrageSpeedMult;
        a.enrageDamageMult = enrageDamageMult;
        a.enrageDurationSeconds = enrageDurationSeconds;
        a.enrageSoundId = enrageSoundId;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nonnull
    public Kind kind() {
        return Kind.fromString(kind);
    }

    /** The NPC role id the archetype spawns (e.g. {@code KweebecNightmare_Blight}). */
    @Nullable
    public String roleName() {
        return roleName;
    }

    /** How many of this archetype spawn per roster slot (clamped to at least 1 by the consumer). */
    public int count() {
        return count;
    }

    /** Weighted-selection weight for corruption/party-size driven roster picks. */
    public double weight() {
        return weight;
    }

    /** Corruption tier at/after which this archetype becomes eligible (0 = always). */
    public int spawnTier() {
        return spawnTier;
    }

    /** Corruption-scaled speed multipliers; parallel to {@link #bandEffectIds()}. */
    @Nullable
    public double[] speedBands() {
        return speedBands;
    }

    /**
     * Pre-authored pace-effect ids parallel to {@link #speedBands()}; an empty
     * string (or {@code null} slot) means the role's 1.0x baseline (no effect).
     */
    @Nullable
    public String[] bandEffectIds() {
        return bandEffectIds;
    }

    // --- on-hit punishment (a non-null/non-zero override wins; 0/null defers to the RuleSet baseline) ---

    /** EntityEffect id applied to the victim on a hunter hit (the base slow); {@code null}/blank = use the rule-set default. */
    @Nullable
    public String onHitSlowEffectId() {
        return onHitSlowEffectId;
    }

    /** Slow duration in seconds; {@code 0} = use the rule-set default. */
    public double onHitSlowSeconds() {
        return onHitSlowSeconds;
    }

    /** Multiplier applied to a hunter's outgoing damage; {@code 0} = use the rule-set default (which defaults to 1.0). */
    public double onHitDamageMult() {
        return onHitDamageMult;
    }

    /** Flat bonus added to a hunter's outgoing damage; {@code 0} = use the rule-set default. */
    public double onHitDamageFlat() {
        return onHitDamageFlat;
    }

    /** Highest proximity-stack tier the slow escalates to; {@code 0} = use the rule-set default. */
    public int onHitStackCap() {
        return onHitStackCap;
    }

    /** Seconds within which repeated hits keep escalating the stack; {@code 0} = use the rule-set default. */
    public double onHitStackWindowSeconds() {
        return onHitStackWindowSeconds;
    }

    /** Seconds without landing a hit before this hunter enrages; {@code 0} = use the rule-set default (0 there = off). */
    public double enrageAfterSeconds() {
        return enrageAfterSeconds;
    }

    /** Speed multiplier while enraged; {@code 0} = use the rule-set default. */
    public double enrageSpeedMult() {
        return enrageSpeedMult;
    }

    /** Damage multiplier while enraged; {@code 0} = use the rule-set default. */
    public double enrageDamageMult() {
        return enrageDamageMult;
    }

    /** Enrage duration in seconds; {@code 0} = use the rule-set default. */
    public double enrageDurationSeconds() {
        return enrageDurationSeconds;
    }

    /** Sound id played when this hunter enrages; {@code null}/blank = use the rule-set default. */
    @Nullable
    public String enrageSoundId() {
        return enrageSoundId;
    }
}
