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

    /** Effective presets by lowercase id (defaults < pack). */
    private final ConcurrentHashMap<String, RuleSet> presets = new ConcurrentHashMap<>();
    /** Display name keys by lowercase id. */
    private final ConcurrentHashMap<String, String> nameKeys = new ConcurrentHashMap<>();

    /**
     * Cached pack layer (already-decoded RuleSets handed in by the asset load
     * handler); deliberately NOT cleared by reloads so an owner reload re-overlays.
     */
    @Nullable private Map<String, RuleSet> packLayer = null;
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
        nameKeys.clear();
        for (RuleSet rs : DefaultPresets.all()) {
            presets.put(rs.presetId(), rs);
            nameKeys.put(rs.presetId(), "kweebecnightmare.preset." + rs.presetId() + ".name");
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s preset-load listener. The
     * handler decodes each pack {@code RoundPresetAsset} into a RuleSet via the
     * shared {@code RoundPresetAsset.CODEC}, so this layer arrives already typed.
     *
     * @param layer    decoded RuleSets by lowercase id
     * @param nameKeys display name keys by lowercase id
     * @param replace  {@code true} to drop the jar defaults for the presets type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, RuleSet> layer,
                                            @Nonnull Map<String, String> nameKeys,
                                            boolean replace) {
        this.packLayer = layer;
        this.packNameKeys = nameKeys;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Preset layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + presets.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, RuleSet> effPresets = new LinkedHashMap<>();
        Map<String, String> effNames = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (RuleSet rs : DefaultPresets.all()) {
                effPresets.put(rs.presetId(), rs);
                effNames.put(rs.presetId(), "kweebecnightmare.preset." + rs.presetId() + ".name");
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            effPresets.putAll(packLayer);
        }
        if (packNameKeys != null) {
            effNames.putAll(packNameKeys);
        }
        presets.clear();
        presets.putAll(effPresets);
        nameKeys.clear();
        nameKeys.putAll(effNames);
        // Guarantee the default preset always resolves (a replace pack without
        // "nightmare" falls back to the jar baseline so a round can always start).
        if (!presets.containsKey(DEFAULT)) {
            RuleSet fallback = DefaultPresets.nightmare();
            presets.put(DEFAULT, fallback);
            nameKeys.putIfAbsent(DEFAULT, "kweebecnightmare.preset." + DEFAULT + ".name");
            SafeLog.warn("[Kweebec][AssetPacks] no '" + DEFAULT
                    + "' preset after fold; restored jar baseline so rounds can start.");
        }
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve a preset id to its {@link RuleSet}. {@code null} / blank / unknown
     * falls back to {@link #DEFAULT}. This is the single static-fold authority;
     * runtime overrides (the MMO API tier) are applied above this in RoundService.
     */
    @Nonnull
    public RuleSet resolve(@Nullable String id) {
        if (id != null && !id.isBlank()) {
            RuleSet rs = presets.get(id.toLowerCase(Locale.ROOT));
            if (rs != null) {
                return rs;
            }
        }
        RuleSet def = presets.get(DEFAULT);
        if (def != null) {
            return def;
        }
        return DefaultPresets.nightmare();
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
