package com.ziggfreed.kweebec.asset;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.instance.preset.InstancePreset;
import com.ziggfreed.common.instance.preset.InstancePresetConfig;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The authority for round presets. The presets ARE the pack-authored
 * {@code Server/KweebecNightmare/Presets/*.json} (the JSON is the single source of
 * truth - there are NO Java-baked default presets); the engine's asset system folds
 * the bundled defaults with any external pack overlay (by id, last-pack-wins or the
 * Control {@code replace} mode) BEFORE {@code KweebecAssetRegistrar}'s preset-load
 * listener hands the decoded set here via {@link #mergePackLayer}.
 *
 * <p>A 4th <b>runtime</b> tier lives above this in {@code RoundService} via
 * {@code KweebecNightmareAPI} (an installed MMO calls
 * {@code overridePreset}/{@code scaleRuleSet} BEFORE the round builds its RuleSet).
 *
 * <p><b>Read lazily / post-load.</b> The presets are populated by the asset
 * {@code LoadedAssetsEvent} AFTER plugin {@code setup()}; {@link #resolve} is called at
 * round start (post-load). If a preset is queried before its JSON has loaded (or the
 * Presets folder is missing entirely), {@link #resolve} falls back to a schema-default
 * {@link RuleSet} (the value object's own field defaults, NOT a content preset) so a
 * round can still start - and logs a warning so the missing JSON is obvious.
 */
public final class PresetConfig {

    /** The preset id used when none is specified. */
    public static final String DEFAULT = "nightmare";

    private static PresetConfig instance;

    /**
     * Effective BASE presets by lowercase id, BEFORE the mutator fold. The JSON-decoded
     * set handed in by {@link #mergePackLayer}. {@link #resolve(String)} stacks each
     * preset's authored mutators on top of its base at resolve time (lazily, so it is
     * robust to the order the preset and mutator load handlers fire in).
     */
    private final ConcurrentHashMap<String, RuleSet> presets = new ConcurrentHashMap<>();
    /** Authored mutator ids per preset (lowercase id -> ordered mutator ids), stacked at resolve. */
    private final ConcurrentHashMap<String, String[]> mutatorIds = new ConcurrentHashMap<>();

    private PresetConfig() {
        // No Java defaults: presets are loaded from Server/KweebecNightmare/Presets/*.json
        // via mergePackLayer when the asset LoadedAssetsEvent fires (post-setup).
    }

    @Nonnull
    public static synchronized PresetConfig getInstance() {
        if (instance == null) {
            instance = new PresetConfig();
        }
        return instance;
    }

    // ==================== pack/JSON layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s preset-load listener. The handler
     * decodes every loaded {@code RoundPresetAsset} (the bundled defaults AND any external
     * pack overlay, already merged by the engine's asset system) into a RuleSet via the
     * shared {@code RoundPresetAsset.CODEC}, so this layer arrives already typed and
     * fully folded. It REPLACES the effective set wholesale (the incoming map is the
     * complete, already-merged result).
     *
     * @param layer       decoded BASE RuleSets by lowercase id (un-mutated)
     * @param mutatorIds  authored mutator ids per preset (stacked at resolve time)
     * @param replace     unused (the engine's asset merge + Control mode already decided
     *                    add/replace before this fires); kept for call-site symmetry
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, RuleSet> layer,
                                            @Nonnull Map<String, String[]> mutatorIds,
                                            boolean replace) {
        presets.clear();
        presets.putAll(layer);
        this.mutatorIds.clear();
        this.mutatorIds.putAll(mutatorIds);
        SafeLog.info("[Kweebec][AssetPacks] Presets loaded from JSON (" + presets.size() + " effective).");
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve a preset id to its fully-folded {@link RuleSet}. {@code null} / blank /
     * unknown falls back to {@link #DEFAULT}; if even that is not loaded (no Presets JSON
     * yet / missing), a schema-default {@link RuleSet} is returned so a round can start.
     * Returns the preset's base {@link RuleSet} with the preset's authored mutators
     * STACKED on top (the mutator fold) - sitting between the JSON layer and the runtime
     * SCALE tier (the MMO API tier applies last, above this in RoundService).
     */
    @Nonnull
    public RuleSet resolve(@Nullable String id) {
        String key = (id != null && !id.isBlank()) ? id.toLowerCase(Locale.ROOT) : DEFAULT;
        RuleSet rs = presets.get(key);
        if (rs == null && !key.equals(DEFAULT)) {
            rs = presets.get(DEFAULT);
        }
        if (rs == null) {
            SafeLog.warn("[Kweebec][AssetPacks] preset '" + key + "' not loaded from JSON "
                    + "(Server/KweebecNightmare/Presets/*.json missing or not yet loaded); "
                    + "using a schema-default RuleSet so the round can still start.");
            return RuleSet.builder(key).build();
        }
        return applyMutators(key, rs);
    }

    /**
     * Stack the preset's authored mutators onto its base {@link RuleSet}. Each id
     * resolves against {@link MutatorConfig}; an unknown id is skipped (logged once,
     * never fatal). The deltas are additive + commutative, so list order does not
     * matter. A preset with no mutators returns its base unchanged.
     */
    @Nonnull
    private RuleSet applyMutators(@Nonnull String presetKey, @Nonnull RuleSet base) {
        String[] ids = mutatorIds.get(presetKey);
        if (ids == null || ids.length == 0) {
            return base;
        }
        RuleSet result = base;
        MutatorConfig mc = MutatorConfig.getInstance();
        for (String mutId : ids) {
            if (mutId == null || mutId.isBlank()) {
                continue;
            }
            MutatorAsset m = mc.resolve(mutId);
            if (m == null) {
                SafeLog.warn("[Kweebec][AssetPacks] preset '" + presetKey
                        + "' names unknown mutator '" + mutId + "'; skipped.");
                continue;
            }
            result = m.apply(result);
        }
        return result;
    }

    /** A preset RuleSet by id, or {@code null} if unknown (no default fallback). */
    @Nullable
    public RuleSet byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return presets.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Display name key for a preset id. The name is a CROSS-CUTTING field owned by the
     * co-keyed common {@link InstancePreset} ({@code Server/ZiggfreedCommon/Instances/}); this
     * delegates to its authored {@code NameKey}, falling back to the by-convention key
     * {@code kweebecnightmare.preset.<id>.name} when the InstancePreset is absent / authors none.
     */
    @Nonnull
    public String nameKey(@Nonnull String id) {
        String key = id.toLowerCase(Locale.ROOT);
        InstancePreset ip = InstancePresetConfig.getInstance().resolve(key);
        if (ip != null && ip.nameKey() != null && !ip.nameKey().isBlank()) {
            return ip.nameKey();
        }
        return "kweebecnightmare.preset." + key + ".name";
    }

    /** All effective preset ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return presets.keySet().stream().sorted().toList();
    }

    /** All effective presets by id (unmodifiable). */
    @Nonnull
    public Map<String, RuleSet> getPresets() {
        return Collections.unmodifiableMap(presets);
    }
}
