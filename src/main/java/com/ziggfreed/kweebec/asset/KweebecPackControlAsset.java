package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

/**
 * Per-pack merge-mode declaration, loaded from a pack's
 * {@code Server/KweebecNightmare/Control/<packname>.json}. Each optional field
 * names a content type and declares {@code "add"} (default - union with existing
 * content) or {@code "replace"} (drop the built-in default layer for that type).
 * Absent fields default to {@code add}.
 *
 * <p>Mirrors hyMMO's {@code PackControlAsset}, scoped to Kweebec's content
 * types ({@code Presets}, {@code Hunters}, {@code SpawnRules}). The Control store is registered FIRST
 * and every content store {@code loadsAfter(KweebecPackControlAsset.class)} so the
 * per-type modes resolve before any content merge handler runs.
 *
 * <p>Author example:
 * <pre>{@code { "Name": "MyKweebecPack", "Presets": "replace", "Hunters": "add" } }</pre>
 */
public final class KweebecPackControlAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, KweebecPackControlAsset>> {

    /** Content-type key for the round presets store. */
    public static final String PRESETS = "presets";
    /** Content-type key for the hunter archetypes store. */
    public static final String HUNTERS = "hunters";
    /** Content-type key for the extra-spawn-rules store. */
    public static final String SPAWNRULES = "spawnrules";
    /** Content-type key for the boss-capstone store. */
    public static final String BOSSES = "bosses";

    private String id;
    private AssetExtraInfo.Data data;

    private String presets;
    private String hunters;
    private String spawnRules;
    private String bosses;

    public static final AssetBuilderCodec<String, KweebecPackControlAsset> CODEC = AssetBuilderCodec.builder(
                    KweebecPackControlAsset.class,
                    KweebecPackControlAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Hytale's KeyedCodec requires JSON keys to start upper-case, so the
            // control-file fields are PascalCase; the internal type keys stay lower.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("Presets", Codec.STRING, false), (a, v) -> a.presets = v, a -> a.presets)
            .add()
            .append(new KeyedCodec<>("Hunters", Codec.STRING, false), (a, v) -> a.hunters = v, a -> a.hunters)
            .add()
            .append(new KeyedCodec<>("SpawnRules", Codec.STRING, false), (a, v) -> a.spawnRules = v, a -> a.spawnRules)
            .add()
            .append(new KeyedCodec<>("Bosses", Codec.STRING, false), (a, v) -> a.bosses = v, a -> a.bosses)
            .add()
            .build();

    public KweebecPackControlAsset() {
    }

    @Override
    public String getId() {
        return id;
    }

    /** Declared mode for a content type key, defaulting to {@code "add"} when absent. */
    @Nonnull
    public String getMode(@Nonnull String typeKey) {
        String value = switch (typeKey) {
            case PRESETS -> presets;
            case HUNTERS -> hunters;
            case SPAWNRULES -> spawnRules;
            case BOSSES -> bosses;
            default -> null;
        };
        return (value == null || value.isBlank()) ? "add" : value;
    }
}
