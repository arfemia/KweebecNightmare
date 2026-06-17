package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.ziggfreed.kweebec.round.InventoryMode;
import com.ziggfreed.kweebec.round.RewardOnExit;
import com.ziggfreed.kweebec.round.ReviveStyle;
import com.ziggfreed.kweebec.round.RuleSet;

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
 * {@link #toRuleSet(String)}; {@code NameKey} / {@code Enabled} /
 * {@code InventoryMode} / {@code RewardOnExit} / {@code HunterArchetype} are the
 * preset-level metadata.
 *
 * <p>Pack JSON shape (all fields optional; absent = the Nightmare-baseline default
 * from the {@link RuleSet} builder):
 * <pre>{@code
 * { "Name": "nightmare", "Enabled": true, "NameKey": "preset.nightmare.name",
 *   "ReviveStyle": "COOP_RESCUE", "MaxDowns": 1, "BleedOutSeconds": 30,
 *   "HunterCount": 1, "HunterSpeedBase": 1.0, "HunterSpeedMax": 1.35,
 *   "ShrineBase": 2, "ShrinePerPlayer": 1, "CaveShrineCount": 2,
 *   "RoundCapSeconds": 900, "CorruptionPerSecond": 0.0014,
 *   "CorruptionPerShrine": 0.12, "ShrineRelightSeconds": 6.0,
 *   "InventoryMode": "PRESERVE_AND_STRIP", "RewardOnExit": "ON_WIN",
 *   "HunterArchetype": "stalker" }
 * }</pre>
 */
public final class RoundPresetAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, RoundPresetAsset>> {

    /** Sentinel for "field absent" on the optional int / double knobs (NaN / MIN_VALUE = use the builder default). */
    private static final int UNSET_INT = Integer.MIN_VALUE;
    private static final double UNSET_DOUBLE = Double.NaN;

    private String id;
    private AssetExtraInfo.Data data;

    private boolean enabled = true;
    @Nullable private String nameKey;
    @Nullable private String reviveStyle;
    @Nullable private String inventoryMode;
    @Nullable private String rewardOnExit;
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
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false), (a, v) -> a.enabled = v, a -> a.enabled)
            .add()
            .append(new KeyedCodec<>("NameKey", Codec.STRING, false), (a, v) -> a.nameKey = v, a -> a.nameKey)
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
            .append(new KeyedCodec<>("RewardOnExit", Codec.STRING, false), (a, v) -> a.rewardOnExit = v, a -> a.rewardOnExit)
            .add()
            .append(new KeyedCodec<>("HunterArchetype", Codec.STRING, false), (a, v) -> a.hunterArchetype = v, a -> a.hunterArchetype)
            .add()
            .append(new KeyedCodec<>("Mutators", Codec.STRING_ARRAY, false), (a, v) -> a.mutators = v, a -> a.mutators)
            .add()
            .build();

    public RoundPresetAsset() {
    }

    @Override
    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Lang key for the preset's display name. Falls back to the by-convention key
     * {@code preset.<id>.name} (note: the {@code kweebecnightmare.} namespace is
     * supplied by the .lang filename) when no explicit {@code NameKey} is authored.
     */
    @Nonnull
    public String nameKey(@Nonnull String presetId) {
        if (nameKey != null && !nameKey.isBlank()) {
            return nameKey;
        }
        return "kweebecnightmare.preset." + presetId.toLowerCase() + ".name";
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
        b.rewardOnExit(RewardOnExit.fromString(rewardOnExit));
        if (hunterArchetype != null && !hunterArchetype.isBlank()) {
            b.hunterArchetype(hunterArchetype.toLowerCase());
        }
        return b.build();
    }
}
