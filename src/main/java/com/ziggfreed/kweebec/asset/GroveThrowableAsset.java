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
 * A pack-authorable GROVE THROWABLE: a harvestable glow-throwable resource the grove grows so survivors
 * can gather + throw it (the data-driven generalization of the hardcoded Moonbloom scatter, for the
 * utility variants - Gustbloom knockback, Mirebloom slow - and any future grove throwable). Loaded from a
 * pack's {@code Server/KweebecNightmare/GroveThrowables/*.json}; each entry names the plant-cluster prefab
 * to scatter and how/where/when, so adding a variant is a new JSON file with ZERO Java.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors {@link MutatorAsset} field-for-field). The engine
 * decodes a grove throwable DIRECTLY into typed fields via {@link #CODEC} - the codec IS the single schema
 * authority on the pack layer. {@link GroveThrowableConfig} folds the loaded entries; the jar ships ZERO
 * code defaults (the kweebec asset pack ships the entries as JSON, disabled), per the asset-driven paradigm.
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a lower-case first letter
 * at static init, throwing at server start).
 *
 * <p>The Gust/Mire entries SHIP {@code "Enabled": false} - the full gather loop (item / plant block / drop /
 * throw chain) is present but DORMANT until a pack flips {@code Enabled} (or authors a new entry). The
 * boss-phase Emberbloom clusters are independent of this type (driven by the common boss asset).
 *
 * <p>Pack JSON shape (all knobs optional; absent = the documented default):
 * <pre>{@code
 * { "Name": "gustbloom", "Enabled": false,
 *   "PrefabKey": "KweebecNightmare/Gustbloom",
 *   "PerShrineCount": 0, "ScatterCount": 6,
 *   "RespawnWithWaves": true, "MinCorruptionTier": 0,
 *   "NameKey": "kweebecnightmare.item.gustbloom.name" }
 * }</pre>
 */
public final class GroveThrowableAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, GroveThrowableAsset>> {

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String nameKey;
    private boolean enabled = false;
    @Nullable private String prefabKey;
    private int perShrineCount = 0;
    private int scatterCount = 0;
    private boolean respawnWithWaves = true;
    private int minCorruptionTier = 0;

    public static final AssetBuilderCodec<String, GroveThrowableAsset> CODEC = AssetBuilderCodec.builder(
                    GroveThrowableAsset.class,
                    GroveThrowableAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Name is an optional human-readable echo of the asset key (the authoritative key is the
            // filename) - consumed by a no-op setter so it doesn't trip the "Unused key(s)" warning.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("NameKey", Codec.STRING, false), (a, v) -> a.nameKey = v, a -> a.nameKey)
            .add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false), (a, v) -> a.enabled = v, a -> a.enabled)
            .add()
            .append(new KeyedCodec<>("PrefabKey", Codec.STRING, false), (a, v) -> a.prefabKey = v, a -> a.prefabKey)
            .add()
            .append(new KeyedCodec<>("PerShrineCount", Codec.INTEGER, false), (a, v) -> a.perShrineCount = v, a -> a.perShrineCount)
            .add()
            .append(new KeyedCodec<>("ScatterCount", Codec.INTEGER, false), (a, v) -> a.scatterCount = v, a -> a.scatterCount)
            .add()
            .append(new KeyedCodec<>("RespawnWithWaves", Codec.BOOLEAN, false), (a, v) -> a.respawnWithWaves = v, a -> a.respawnWithWaves)
            .add()
            .append(new KeyedCodec<>("MinCorruptionTier", Codec.INTEGER, false), (a, v) -> a.minCorruptionTier = v, a -> a.minCorruptionTier)
            .add()
            .build();

    public GroveThrowableAsset() {
    }

    @Override
    public String getId() {
        return id;
    }

    /** Lang key for the throwable's display name, falling back to {@code kweebecnightmare.item.<id>.name}. */
    @Nonnull
    public String nameKey() {
        if (nameKey != null && !nameKey.isBlank()) {
            return nameKey;
        }
        return "kweebecnightmare.item." + (id == null ? "" : id.toLowerCase()) + ".name";
    }

    /** Whether this grove throwable is DISTRIBUTED this build (false = the gather loop ships but stays dormant). */
    public boolean enabled() {
        return enabled;
    }

    /** Prefab key (one harvestable plant block) the {@code ArenaBuilder} pastes for a cluster; blank = skipped. */
    @Nullable
    public String prefabKey() {
        return prefabKey;
    }

    /** Plants ringed at EACH surface shrine on each placement wave (the guaranteed supply); clamped {@code >= 0}. */
    public int perShrineCount() {
        return Math.max(0, perShrineCount);
    }

    /** Plants scattered across the grove on each placement wave (the exploration supply); clamped {@code >= 0}. */
    public int scatterCount() {
        return Math.max(0, scatterCount);
    }

    /** Whether this throwable ALSO regrows on each Moonbloom respawn wave (default true), not just at round start. */
    public boolean respawnWithWaves() {
        return respawnWithWaves;
    }

    /** Lowest corruption tier at/after which this throwable starts appearing (a difficulty-ramp gate); default 0. */
    public int minCorruptionTier() {
        return Math.max(0, minCorruptionTier);
    }

    /** True when this entry would actually place something (enabled, has a prefab, and at least one count > 0). */
    public boolean isPlaceable() {
        return enabled && prefabKey != null && !prefabKey.isBlank()
                && (perShrineCount() > 0 || scatterCount() > 0);
    }
}
