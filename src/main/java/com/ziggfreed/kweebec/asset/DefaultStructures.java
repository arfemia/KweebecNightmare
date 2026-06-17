package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline corrupted-structure placements - the in-memory {@code defaults}
 * floor {@link StructureConfig} folds packs on top of. Reproduces the old hardcoded
 * {@code StructureCatalog.CANDIDATES} table EXACTLY (the same prefab keys, roles, and
 * mid-grove (x,z) candidates, including the Grandfather -> GuardTower landmark fix),
 * so structure placement is unchanged when no pack lands.
 *
 * <p>The matching {@code Server/KweebecNightmare/Structures/*.json} files are the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 *
 * <p>The {@code of(...)} args are, in order: {@code id, prefabKey, role, x, z, weight}.
 */
public final class DefaultStructures {

    private static final String KEY_PREFIX = "KweebecNightmare/";

    /** Default selection weight (reserved for future weighting; the seeded shuffle ignores it). */
    private static final double DEFAULT_WEIGHT = 1.0;

    private DefaultStructures() {
    }

    /**
     * All baseline placements, in the same order the old hardcoded table declared
     * them - mid-grove ruins in varied directions (r ~ 35..60), each clear of the
     * inner gameplay disc. Keys match the committed {@code Corrupted_*} prefab set.
     */
    @Nonnull
    public static List<StructurePlacementAsset> all() {
        return List.of(
                // North-east cover house.
                StructurePlacementAsset.of("Corrupted_House_NE", KEY_PREFIX + "Corrupted_House_Small",
                        "cover", 38.0, 30.0, DEFAULT_WEIGHT),
                // East shop (cover).
                StructurePlacementAsset.of("Corrupted_Shop_E", KEY_PREFIX + "Corrupted_Shop",
                        "cover", 52.0, 4.0, DEFAULT_WEIGHT),
                // South-east lamppost beacon.
                StructurePlacementAsset.of("Corrupted_Lamppost_SE", KEY_PREFIX + "Corrupted_Lamppost",
                        "beacon", 36.0, -34.0, DEFAULT_WEIGHT),
                // South-west guard tower landmark.
                StructurePlacementAsset.of("Corrupted_GuardTower_SW", KEY_PREFIX + "Corrupted_GuardTower",
                        "landmark", -40.0, -38.0, DEFAULT_WEIGHT),
                // West house (cover).
                StructurePlacementAsset.of("Corrupted_House_W", KEY_PREFIX + "Corrupted_House_Small",
                        "cover", -54.0, 2.0, DEFAULT_WEIGHT),
                // North-west lamppost beacon.
                StructurePlacementAsset.of("Corrupted_Lamppost_NW", KEY_PREFIX + "Corrupted_Lamppost",
                        "beacon", -34.0, 34.0, DEFAULT_WEIGHT),
                // North bridge chokepoint (between spawn-side and the mid grove).
                StructurePlacementAsset.of("Corrupted_Bridge_N", KEY_PREFIX + "Corrupted_Bridge",
                        "chokepoint", 6.0, 56.0, DEFAULT_WEIGHT),
                // Far north-east guard tower (the big orienting landmark; no native Grandfather prefab exists).
                StructurePlacementAsset.of("Corrupted_GuardTower_NE", KEY_PREFIX + "Corrupted_GuardTower",
                        "landmark", 44.0, 44.0, DEFAULT_WEIGHT),
                // South shop (cover), opposite side from the east shop.
                StructurePlacementAsset.of("Corrupted_Shop_S", KEY_PREFIX + "Corrupted_Shop",
                        "cover", -8.0, -52.0, DEFAULT_WEIGHT),
                // West guard tower landmark.
                StructurePlacementAsset.of("Corrupted_GuardTower_W", KEY_PREFIX + "Corrupted_GuardTower",
                        "landmark", -56.0, -14.0, DEFAULT_WEIGHT));
    }
}
