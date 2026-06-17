package com.ziggfreed.kweebec.asset;

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
 * The authority for hunter archetypes, folded {@code defaults < pack < owner}.
 * Mirrors {@link PresetConfig}: the jar ships the {@code stalker} baseline (the
 * single hardcoded hunter, see {@link DefaultHunters}); a pack's
 * {@code Server/KweebecNightmare/Hunters/*.json} overlays by id (last-pack-wins, or
 * {@code replace} drops the defaults for that type). The owner layer is not wired
 * this pass.
 *
 * <p>{@code AiHunterController} does NOT consume this yet (Phase 2A wires the
 * roster); the config is the data home a {@link com.ziggfreed.kweebec.round.RuleSet}'s
 * {@code hunterArchetype} id resolves against once it does.
 */
public final class HunterArchetypeConfig {

    /** The archetype id used when a rule-set names none. Preserves the hardcoded hunter. */
    public static final String DEFAULT = DefaultHunters.DEFAULT;

    private static HunterArchetypeConfig instance;

    /** Effective archetypes by lowercase id (defaults < pack). */
    private final ConcurrentHashMap<String, HunterArchetypeAsset> archetypes = new ConcurrentHashMap<>();

    /** Cached pack layer (already-decoded archetypes handed in by the asset load handler). */
    @Nullable private Map<String, HunterArchetypeAsset> packLayer = null;
    private boolean packReplace = false;

    private HunterArchetypeConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized HunterArchetypeConfig getInstance() {
        if (instance == null) {
            instance = new HunterArchetypeConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        archetypes.clear();
        for (HunterArchetypeAsset a : DefaultHunters.all()) {
            archetypes.put(a.getId().toLowerCase(Locale.ROOT), a);
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s hunter-load listener. The
     * handler hands in each pack {@link HunterArchetypeAsset} keyed by lowercase id
     * (decoded by the engine via the shared {@code HunterArchetypeAsset.CODEC}).
     *
     * @param layer   decoded archetypes by lowercase id
     * @param replace {@code true} to drop the jar defaults for the hunters type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, HunterArchetypeAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Hunter layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + archetypes.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, HunterArchetypeAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (HunterArchetypeAsset a : DefaultHunters.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        archetypes.clear();
        archetypes.putAll(eff);
        // Guarantee the default archetype always resolves (a replace pack without
        // "stalker" falls back to the jar baseline so a hunter can always spawn).
        if (!archetypes.containsKey(DEFAULT)) {
            archetypes.put(DEFAULT, DefaultHunters.stalker());
            SafeLog.warn("[Kweebec][AssetPacks] no '" + DEFAULT
                    + "' hunter after fold; restored jar baseline so a hunter can spawn.");
        }
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve an archetype id to its asset. {@code null} / blank / unknown falls
     * back to {@link #DEFAULT}.
     */
    @Nonnull
    public HunterArchetypeAsset resolve(@Nullable String id) {
        if (id != null && !id.isBlank()) {
            HunterArchetypeAsset a = archetypes.get(id.toLowerCase(Locale.ROOT));
            if (a != null) {
                return a;
            }
        }
        HunterArchetypeAsset def = archetypes.get(DEFAULT);
        return def != null ? def : DefaultHunters.stalker();
    }

    /** An archetype by id, or {@code null} if unknown (no default fallback). */
    @Nullable
    public HunterArchetypeAsset byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return archetypes.get(id.toLowerCase(Locale.ROOT));
    }

    /** All effective archetype ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return archetypes.keySet().stream().sorted().toList();
    }

    /** All effective archetypes by id (unmodifiable). */
    @Nonnull
    public Map<String, HunterArchetypeAsset> getArchetypes() {
        return Collections.unmodifiableMap(archetypes);
    }
}
