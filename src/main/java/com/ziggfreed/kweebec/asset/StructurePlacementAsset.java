package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import com.ziggfreed.kweebec.arena.StructureCatalog;

/**
 * A pack-authorable corrupted-structure PLACEMENT, loaded from a pack's
 * {@code Server/KweebecNightmare/Structures/*.json} (one placement per file,
 * PascalCase filename). The data source for {@link StructureCatalog}'s seeded
 * per-round selection: each placement binds a prefab key + a design-intent
 * {@link Role} + a candidate (x,z) in the MID grove + a (future-use) weight.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors {@link HunterArchetypeAsset}
 * and {@link MutatorAsset} field-for-field). The engine decodes a placement DIRECTLY
 * into typed fields via {@link #CODEC} - the codec IS the single schema authority on
 * the pack layer and the in-jar {@link DefaultStructures} floor. Replaces the old
 * hardcoded {@code StructureCatalog.CANDIDATES} table as the schema authority.
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start).
 *
 * <p>Pack JSON shape:
 * <pre>{@code
 * { "Name": "Corrupted_Shop_E", "PrefabKey": "KweebecNightmare/Corrupted_Shop",
 *   "Role": "cover", "X": 52.0, "Z": 4.0, "Weight": 1.0 }
 * }</pre>
 */
public final class StructurePlacementAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, StructurePlacementAsset>> {

    /**
     * The narrative/gameplay intent of a placed ruin. Mirrors
     * {@link HunterArchetypeAsset.Kind}: a {@code DEFAULT} + a forgiving
     * {@link #fromString} so an unknown/blank authored {@code Role} degrades to the
     * default rather than failing the load. The placement maps each value to the
     * shared {@link StructureCatalog.Role} so there is ONE Role type the consumer
     * prints.
     */
    public enum Role {
        /** A wall/house a survivor can break line-of-sight behind. */
        COVER,
        /** A bridge/cluster that narrows movement (the hunter funnels through). */
        CHOKEPOINT,
        /** A lamppost ruin: a lit landmark that draws the eye (relight-beacon flavor). */
        BEACON,
        /** A big, far structure that orients the player (the elder / tower). */
        LANDMARK;

        /** The role chosen when none is authored. */
        public static final Role DEFAULT = COVER;

        @Nonnull
        public static Role fromString(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return DEFAULT;
            }
            for (Role r : values()) {
                if (r.name().equalsIgnoreCase(s.trim())) {
                    return r;
                }
            }
            return DEFAULT;
        }

        /** Map this asset-side role to the shared {@link StructureCatalog.Role} the consumer prints. */
        @Nonnull
        public StructureCatalog.Role toCatalogRole() {
            return switch (this) {
                case COVER -> StructureCatalog.Role.COVER;
                case CHOKEPOINT -> StructureCatalog.Role.CHOKEPOINT;
                case BEACON -> StructureCatalog.Role.BEACON;
                case LANDMARK -> StructureCatalog.Role.LANDMARK;
            };
        }
    }

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String prefabKey;
    @Nullable private String role;
    private double x = 0.0;
    private double z = 0.0;
    private double weight = 1.0;

    public static final AssetBuilderCodec<String, StructurePlacementAsset> CODEC = AssetBuilderCodec.builder(
                    StructurePlacementAsset.class,
                    StructurePlacementAsset::new,
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
            .append(new KeyedCodec<>("PrefabKey", Codec.STRING, false), (a, v) -> a.prefabKey = v, a -> a.prefabKey)
            .add()
            .append(new KeyedCodec<>("Role", Codec.STRING, false), (a, v) -> a.role = v, a -> a.role)
            .add()
            .append(new KeyedCodec<>("X", Codec.DOUBLE, false), (a, v) -> a.x = v, a -> a.x)
            .add()
            .append(new KeyedCodec<>("Z", Codec.DOUBLE, false), (a, v) -> a.z = v, a -> a.z)
            .add()
            .append(new KeyedCodec<>("Weight", Codec.DOUBLE, false), (a, v) -> a.weight = v, a -> a.weight)
            .add()
            .build();

    public StructurePlacementAsset() {
    }

    /**
     * Build a placement in code (the jar's {@code defaults} floor), without going
     * through the JSON {@link #CODEC}. The shipped {@code *.json} placements author the
     * same fields; {@link DefaultStructures} is the zero-pack source of truth.
     */
    @Nonnull
    static StructurePlacementAsset of(@Nonnull String id, @Nullable String prefabKey,
                                      @Nullable String role, double x, double z, double weight) {
        StructurePlacementAsset a = new StructurePlacementAsset();
        a.id = id;
        a.prefabKey = prefabKey;
        a.role = role;
        a.x = x;
        a.z = z;
        a.weight = weight;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /** The prefab resolution key (e.g. {@code KweebecNightmare/Corrupted_Shop}). */
    @Nullable
    public String prefabKey() {
        return prefabKey;
    }

    /** The design-intent role, parsed forgivingly to the shared catalog role. */
    @Nonnull
    public StructureCatalog.Role role() {
        return Role.fromString(role).toCatalogRole();
    }

    /** Candidate world X in the mid grove. */
    public double x() {
        return x;
    }

    /** Candidate world Z in the mid grove. */
    public double z() {
        return z;
    }

    /** Selection weight (for future weighting; not consumed by the seeded-shuffle path yet). */
    public double weight() {
        return weight;
    }
}
