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
 * The authority for extra-spawn rules, folded {@code defaults < pack < owner < runtime}. Mirrors
 * {@link StructureConfig}: the jar ships the baseline rules (see {@link DefaultSpawnRules}); a pack's
 * {@code Server/KweebecNightmare/SpawnRules/*.json} overlays by id (last-pack-wins, or {@code replace}
 * drops the defaults for that type). The owner layer is not wired this pass; the runtime tier lives on
 * {@link com.ziggfreed.kweebec.integration.KweebecNightmareAPI} and post-transforms what this fold yields.
 *
 * <p>{@code AiHunterController.evaluateSpawnRules(...)} reads {@link #effectiveRules()} (the runtime tier
 * applied over this fold via the API), filters by {@link SpawnRuleAsset#trigger()}, and fires the matching
 * rules through a shared {@code EncounterDirector} - so a pack can add, remove, or retune the in-round
 * escalation purely as DATA, no code change.
 */
public final class SpawnRuleConfig {

    private static SpawnRuleConfig instance;

    /** Effective rules by lowercase id (defaults < pack), insertion-ordered for stable evaluation. */
    private final ConcurrentHashMap<String, SpawnRuleAsset> rules = new ConcurrentHashMap<>();

    /**
     * Effective rules in stable insertion order. The {@link ConcurrentHashMap} above keys the merge;
     * this list preserves the authored order the per-trigger evaluation walks.
     */
    private volatile List<SpawnRuleAsset> ordered = List.of();

    /** Cached pack layer (already-decoded rules handed in by the asset load handler). */
    @Nullable private Map<String, SpawnRuleAsset> packLayer = null;
    private boolean packReplace = false;

    private SpawnRuleConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized SpawnRuleConfig getInstance() {
        if (instance == null) {
            instance = new SpawnRuleConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        rules.clear();
        List<SpawnRuleAsset> floor = new ArrayList<>();
        for (SpawnRuleAsset a : DefaultSpawnRules.all()) {
            rules.put(a.getId().toLowerCase(Locale.ROOT), a);
            floor.add(a);
        }
        ordered = List.copyOf(floor);
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s spawn-rule-load listener. The handler hands in each
     * pack {@link SpawnRuleAsset} keyed by lowercase id (decoded by the engine via the shared
     * {@code SpawnRuleAsset.CODEC}).
     *
     * @param layer   decoded rules by lowercase id
     * @param replace {@code true} to drop the jar defaults for the spawn-rules type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, SpawnRuleAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] SpawnRule layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + rules.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, SpawnRuleAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (SpawnRuleAsset a : DefaultSpawnRules.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        rules.clear();
        rules.putAll(eff);
        ordered = List.copyOf(new ArrayList<>(eff.values()));
    }

    // ==================== resolve / queries ====================

    /** A rule by id, or {@code null} if unknown. */
    @Nullable
    public SpawnRuleAsset byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return rules.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * The effective rules (defaults &lt; pack), in stable authored order, BEFORE the runtime tier. The
     * consumer reads {@link com.ziggfreed.kweebec.integration.KweebecNightmareAPI#resolveSpawnRules()} to
     * apply the runtime override/scale on top of this. Unmodifiable.
     */
    @Nonnull
    public List<SpawnRuleAsset> all() {
        return ordered;
    }

    /** All effective rule ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return rules.keySet().stream().sorted().toList();
    }

    /** All effective rules by id (unmodifiable). */
    @Nonnull
    public Map<String, SpawnRuleAsset> getRules() {
        return Collections.unmodifiableMap(rules);
    }
}
