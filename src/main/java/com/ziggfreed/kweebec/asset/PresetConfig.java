package com.ziggfreed.kweebec.asset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The authority for round presets, folded {@code defaults < pack < owner}. Replaces
 * the old hardcoded {@code RoundPreset} enum as the {@link RuleSet} source; the enum
 * is now a thin compatibility shim that delegates here.
 *
 * <p>Folding mechanism (copied from hyMMO's per-type config singletons): the jar
 * ships the three baseline presets as default JSON (loaded via the engine asset
 * store's DEFAULT_PACK layer and re-applied here as the {@code defaults} floor); a
 * pack's {@code Server/KweebecNightmare/Presets/*.json} overlays by id
 * (last-pack-wins, or {@code replace} drops the defaults for that type). The owner
 * layer (a future {@code mods/kweebecnightmare/presets.json}) would overlay on top;
 * it is not wired this pass.
 *
 * <p>A 4th <b>runtime</b> tier lives above this in {@code RoundService} via
 * {@code KweebecNightmareAPI} (an installed MMO calls
 * {@code overridePreset}/{@code scaleRuleSet} BEFORE the round builds its RuleSet).
 * This config is purely the static defaults &lt; pack fold.
 */
public final class PresetConfig {

    /** The preset id used when none is specified. Preserves {@code RoundPreset.DEFAULT == NIGHTMARE}. */
    public static final String DEFAULT = "nightmare";

    private static PresetConfig instance;

    /**
     * Effective BASE presets by lowercase id (defaults < pack), BEFORE the mutator
     * fold. {@link #resolve(String)} stacks each preset's authored mutators on top of
     * its base at resolve time (lazily, so it is robust to the order the preset and
     * mutator load handlers fire in).
     */
    private final ConcurrentHashMap<String, RuleSet> presets = new ConcurrentHashMap<>();
    /** Authored mutator ids per preset (lowercase id -> ordered mutator ids), stacked at resolve. */
    private final ConcurrentHashMap<String, String[]> mutatorIds = new ConcurrentHashMap<>();
    /** Display name keys by lowercase id. */
    private final ConcurrentHashMap<String, String> nameKeys = new ConcurrentHashMap<>();

    /**
     * Cached pack layer (already-decoded RuleSets handed in by the asset load
     * handler); deliberately NOT cleared by reloads so an owner reload re-overlays.
     */
    @Nullable private Map<String, RuleSet> packLayer = null;
    @Nullable private Map<String, String[]> packMutatorIds = null;
    @Nullable private Map<String, String> packNameKeys = null;
    private boolean packReplace = false;

    private PresetConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized PresetConfig getInstance() {
        if (instance == null) {
            instance = new PresetConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    /**
     * The jar's baseline presets (the Nightmare-tuned floor). These match the old
     * {@code RoundPreset} enum exactly so behavior is unchanged when no pack lands.
     * The default JSON in {@code Server/KweebecNightmare/Presets/} is the authoring
     * reference / editor surface; this code path is the in-memory source of truth so
     * the feature works with zero pack files too.
     */
    private synchronized void loadDefaults() {
        presets.clear();
        mutatorIds.clear();
        nameKeys.clear();
        for (DefaultPresets.Preset p : DefaultPresets.all()) {
            RuleSet rs = p.ruleSet();
            presets.put(rs.presetId(), rs);
            mutatorIds.put(rs.presetId(), p.mutatorIds());
            nameKeys.put(rs.presetId(), "kweebecnightmare.preset." + rs.presetId() + ".name");
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s preset-load listener. The
     * handler decodes each pack {@code RoundPresetAsset} into a RuleSet via the
     * shared {@code RoundPresetAsset.CODEC}, so this layer arrives already typed.
     *
     * @param layer       decoded BASE RuleSets by lowercase id (un-mutated)
     * @param mutatorIds  authored mutator ids per preset (stacked at resolve time)
     * @param nameKeys    display name keys by lowercase id
     * @param replace     {@code true} to drop the jar defaults for the presets type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, RuleSet> layer,
                                            @Nonnull Map<String, String[]> mutatorIds,
                                            @Nonnull Map<String, String> nameKeys,
                                            boolean replace) {
        this.packLayer = layer;
        this.packMutatorIds = mutatorIds;
        this.packNameKeys = nameKeys;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Preset layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + presets.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, RuleSet> effPresets = new LinkedHashMap<>();
        Map<String, String[]> effMutators = new LinkedHashMap<>();
        Map<String, String> effNames = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (DefaultPresets.Preset p : DefaultPresets.all()) {
                RuleSet rs = p.ruleSet();
                effPresets.put(rs.presetId(), rs);
                effMutators.put(rs.presetId(), p.mutatorIds());
                effNames.put(rs.presetId(), "kweebecnightmare.preset." + rs.presetId() + ".name");
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            effPresets.putAll(packLayer);
        }
        if (packMutatorIds != null) {
            effMutators.putAll(packMutatorIds);
        }
        if (packNameKeys != null) {
            effNames.putAll(packNameKeys);
        }
        presets.clear();
        presets.putAll(effPresets);
        mutatorIds.clear();
        mutatorIds.putAll(effMutators);
        nameKeys.clear();
        nameKeys.putAll(effNames);
        // Guarantee the default preset always resolves (a replace pack without
        // "nightmare" falls back to the jar baseline so a round can always start).
        if (!presets.containsKey(DEFAULT)) {
            RuleSet fallback = DefaultPresets.nightmare();
            presets.put(DEFAULT, fallback);
            mutatorIds.putIfAbsent(DEFAULT, new String[0]);
            nameKeys.putIfAbsent(DEFAULT, "kweebecnightmare.preset." + DEFAULT + ".name");
            SafeLog.warn("[Kweebec][AssetPacks] no '" + DEFAULT
                    + "' preset after fold; restored jar baseline so rounds can start.");
        }
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve a preset id to its fully-folded {@link RuleSet}. {@code null} / blank /
     * unknown falls back to {@link #DEFAULT}. This is the single static-fold
     * authority: it returns the preset's base {@link RuleSet} with the preset's
     * authored mutators (the {@code mutator fold}) STACKED on top - sitting between
     * the {@code defaults < pack < owner} layering and the runtime SCALE tier (the
     * MMO API tier applies last, above this in RoundService, untouched).
     */
    @Nonnull
    public RuleSet resolve(@Nullable String id) {
        if (id != null && !id.isBlank()) {
            String key = id.toLowerCase(Locale.ROOT);
            RuleSet rs = presets.get(key);
            if (rs != null) {
                return applyMutators(key, rs);
            }
        }
        RuleSet def = presets.get(DEFAULT);
        if (def != null) {
            return applyMutators(DEFAULT, def);
        }
        return DefaultPresets.nightmare();
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

    /** Display name key for a preset id, falling back to the by-convention key. */
    @Nonnull
    public String nameKey(@Nonnull String id) {
        String key = nameKeys.get(id.toLowerCase(Locale.ROOT));
        return key != null ? key : "kweebecnightmare.preset." + id.toLowerCase(Locale.ROOT) + ".name";
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
