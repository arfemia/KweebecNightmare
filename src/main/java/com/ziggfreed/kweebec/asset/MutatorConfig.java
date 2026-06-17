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
 * The authority for round mutators, folded {@code defaults < pack < owner}. Mirrors
 * {@link HunterArchetypeConfig}: the jar ships the baseline mutators (see
 * {@link DefaultMutators}); a pack's {@code Server/KweebecNightmare/Mutators/*.json}
 * overlays by id (last-pack-wins, or {@code replace} drops the defaults for that
 * type). The owner layer is not wired this pass.
 *
 * <p>A {@link RoundPresetAsset}'s authored {@code Mutators} ids resolve against this
 * config in {@link PresetConfig#resolve(String)}, which stacks each one's additive
 * deltas onto the preset's base {@link com.ziggfreed.kweebec.round.RuleSet}. An
 * unknown mutator id is skipped (logged once), never fatal.
 */
public final class MutatorConfig {

    private static MutatorConfig instance;

    /** Effective mutators by lowercase id (defaults < pack). */
    private final ConcurrentHashMap<String, MutatorAsset> mutators = new ConcurrentHashMap<>();

    /** Cached pack layer (already-decoded mutators handed in by the asset load handler). */
    @Nullable private Map<String, MutatorAsset> packLayer = null;
    private boolean packReplace = false;

    private MutatorConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized MutatorConfig getInstance() {
        if (instance == null) {
            instance = new MutatorConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        mutators.clear();
        for (MutatorAsset a : DefaultMutators.all()) {
            mutators.put(a.getId().toLowerCase(Locale.ROOT), a);
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s mutator-load listener. The
     * handler hands in each pack {@link MutatorAsset} keyed by lowercase id (decoded
     * by the engine via the shared {@code MutatorAsset.CODEC}).
     *
     * @param layer   decoded mutators by lowercase id
     * @param replace {@code true} to drop the jar defaults for the mutators type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, MutatorAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Mutator layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + mutators.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, MutatorAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (MutatorAsset a : DefaultMutators.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        mutators.clear();
        mutators.putAll(eff);
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve a mutator id to its asset, or {@code null} if unknown. A preset's
     * {@code Mutators} list resolves each id here; a {@code null} return means the id
     * is skipped during the preset fold (never fatal). Mutators have no DEFAULT
     * fallback - an absent mutator simply contributes no deltas.
     */
    @Nullable
    public MutatorAsset resolve(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return mutators.get(id.toLowerCase(Locale.ROOT));
    }

    /** A mutator by id, or {@code null} if unknown (alias of {@link #resolve(String)}). */
    @Nullable
    public MutatorAsset byId(@Nullable String id) {
        return resolve(id);
    }

    /** All effective mutator ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return mutators.keySet().stream().sorted().toList();
    }

    /** All effective mutators by id (unmodifiable). */
    @Nonnull
    public Map<String, MutatorAsset> getMutators() {
        return Collections.unmodifiableMap(mutators);
    }
}
