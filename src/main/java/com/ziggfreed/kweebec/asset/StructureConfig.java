package com.ziggfreed.kweebec.asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The authority for corrupted-structure placements, folded {@code defaults < pack <
 * owner}. Mirrors {@link MutatorConfig}: the jar ships the baseline candidate table
 * (see {@link DefaultStructures}); a pack's {@code Server/KweebecNightmare/Structures/*.json}
 * overlays by id (last-pack-wins, or {@code replace} drops the defaults for that type).
 * The owner layer is not wired this pass.
 *
 * <p>{@code StructureCatalog.select(seed, mask)} reads {@link #all()} (the effective
 * placement set), maps each {@link StructurePlacementAsset} to a
 * {@code StructureCatalog.Placement}, then seed-shuffles + mask-filters - so a pack can
 * add, remove, or relocate the per-round ruin candidates purely as DATA, no code change.
 */
public final class StructureConfig {

    private static StructureConfig instance;

    /** Effective placements by lowercase id (defaults < pack), insertion-ordered for stable selection. */
    private final ConcurrentHashMap<String, StructurePlacementAsset> placements = new ConcurrentHashMap<>();

    /**
     * Effective placements in stable insertion order. The {@link ConcurrentHashMap} above
     * keys the merge; this list preserves the authored order the seeded shuffle expects.
     */
    private volatile List<StructurePlacementAsset> ordered = List.of();

    /** Cached pack layer (already-decoded placements handed in by the asset load handler). */
    @Nullable private Map<String, StructurePlacementAsset> packLayer = null;
    private boolean packReplace = false;

    private StructureConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized StructureConfig getInstance() {
        if (instance == null) {
            instance = new StructureConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        placements.clear();
        List<StructurePlacementAsset> floor = new ArrayList<>();
        for (StructurePlacementAsset a : DefaultStructures.all()) {
            placements.put(a.getId().toLowerCase(Locale.ROOT), a);
            floor.add(a);
        }
        ordered = List.copyOf(floor);
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s structure-load listener. The
     * handler hands in each pack {@link StructurePlacementAsset} keyed by lowercase id
     * (decoded by the engine via the shared {@code StructurePlacementAsset.CODEC}).
     *
     * @param layer   decoded placements by lowercase id
     * @param replace {@code true} to drop the jar defaults for the structures type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, StructurePlacementAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Structure layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + placements.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, StructurePlacementAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (StructurePlacementAsset a : DefaultStructures.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        placements.clear();
        placements.putAll(eff);
        ordered = List.copyOf(new ArrayList<>(eff.values()));
    }

    // ==================== resolve / queries ====================

    /** A placement by id, or {@code null} if unknown. */
    @Nullable
    public StructurePlacementAsset byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return placements.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * All effective placements, in stable authored order (the candidate table
     * {@code StructureCatalog.select} seed-shuffles + mask-filters). Unmodifiable.
     */
    @Nonnull
    public List<StructurePlacementAsset> all() {
        return ordered;
    }

    /** All effective placement ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return placements.keySet().stream().sorted().toList();
    }

    /** All effective placements by id (unmodifiable). */
    @Nonnull
    public Map<String, StructurePlacementAsset> getPlacements() {
        return Collections.unmodifiableMap(placements);
    }
}
