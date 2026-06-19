package com.ziggfreed.kweebec.asset;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Registers Kweebec Nightmare's OWN custom asset types and their load listeners during
 * plugin {@code setup()}. The store-registration + the {@code LoadedAssetsEvent} fold now
 * route through ziggfreed-common's {@link AssetStoreRegistrar} (the byte-identical private
 * copy this class used to carry was deleted), scoped to Kweebec's domain content types
 * (presets, hunters, mutators, spawn rules, bosses, structures, scare beats) plus the
 * {@code Control} store.
 *
 * <p>The cross-cutting instance-preset layer ({@code InstancePresetAsset}) is NO LONGER
 * registered here: ziggfreed-common owns that store at {@code Server/ZiggfreedCommon/Instances}
 * and folds it itself (a store is keyed by asset class and may be registered only once -
 * registering it here too would crash the load). Kweebec just READS the resolved presets
 * back via {@code InstancePresetConfig.getInstance()}.
 *
 * <p>The stores are pure delivery channels: a pack drops JSON under
 * {@code Server/KweebecNightmare/<Type>/}, the engine loads and merges it by id
 * (last-pack-wins), and the load listeners fold the loaded entries into the existing
 * config singletons. The {@link KweebecPackControlAsset} store is registered FIRST and
 * every content store declares {@code loadsAfter(KweebecPackControlAsset.class)}, so
 * per-type merge modes ({@code add}/{@code replace}) resolve before any content merge
 * handler runs.
 */
public final class KweebecAssetRegistrar {

    /** Asset path roots under a pack's {@code Server/}. */
    private static final String CONTROL_PATH = "KweebecNightmare/Control";
    private static final String PRESETS_PATH = "KweebecNightmare/Presets";
    private static final String HUNTERS_PATH = "KweebecNightmare/Hunters";
    private static final String MUTATORS_PATH = "KweebecNightmare/Mutators";

    private KweebecAssetRegistrar() {
    }

    public static void registerAll(@Nonnull JavaPlugin plugin) {
        // Control store - no loadsAfter; both content stores load after it so the
        // per-type add/replace modes are resolvable before a content fold runs.
        AssetStoreRegistrar.registerStore(KweebecPackControlAsset.class, new DefaultAssetMap<String, KweebecPackControlAsset>(),
                CONTROL_PATH, KweebecPackControlAsset::getId, KweebecPackControlAsset.CODEC, null);

        // Round presets (Pattern A) - loadsAfter the control store.
        AssetStoreRegistrar.registerStore(RoundPresetAsset.class, new DefaultAssetMap<String, RoundPresetAsset>(),
                PRESETS_PATH, RoundPresetAsset::getId, RoundPresetAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, RoundPresetAsset.class,
                KweebecAssetRegistrar::onPresetAssetsLoaded);

        // Hunter archetypes (Pattern A) - loadsAfter the control store.
        AssetStoreRegistrar.registerStore(HunterArchetypeAsset.class, new DefaultAssetMap<String, HunterArchetypeAsset>(),
                HUNTERS_PATH, HunterArchetypeAsset::getId, HunterArchetypeAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, HunterArchetypeAsset.class,
                KweebecAssetRegistrar::onHunterAssetsLoaded);

        // Round mutators (Pattern A) - loadsAfter the control store.
        AssetStoreRegistrar.registerStore(MutatorAsset.class, new DefaultAssetMap<String, MutatorAsset>(),
                MUTATORS_PATH, MutatorAsset::getId, MutatorAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, MutatorAsset.class,
                KweebecAssetRegistrar::onMutatorAssetsLoaded);

        SafeLog.info("[Kweebec][AssetPacks] Registered Kweebec content asset stores (Presets, Hunters, Mutators, Control); Bosses + BandedEffects + Placements + EncounterRules + Instances are owned by ziggfreed-common");
    }

    // ==================== load listeners (Pattern A typed-map fold) ====================

    /**
     * Fold pack {@link RoundPresetAsset}s into {@link PresetConfig}. Iterates the typed asset
     * map (Pattern A), skips engine-base entries, decodes each to a {@link RuleSet} via the
     * asset's {@code toRuleSet}. The display name + the enabled flag are NOT read here - they
     * are cross-cutting fields the co-keyed common {@code InstancePresetAsset} owns
     * ({@code PresetConfig.nameKey} delegates to it; presentation/enable filtering reads it).
     */
    static void onPresetAssetsLoaded(
            LoadedAssetsEvent<String, RoundPresetAsset, DefaultAssetMap<String, RoundPresetAsset>> event) {
        DefaultAssetMap<String, RoundPresetAsset> assetMap = event.getAssetMap();
        Map<String, RuleSet> layer = new LinkedHashMap<>();
        Map<String, String[]> mutatorIds = new LinkedHashMap<>();
        for (Map.Entry<String, RoundPresetAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            RoundPresetAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            layer.put(id, asset.toRuleSet(id));
            mutatorIds.put(id, asset.mutators());
        }
        boolean replace = KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.PRESETS);
        PresetConfig.getInstance().mergePackLayer(layer, mutatorIds, replace);
    }

    /**
     * Fold pack {@link MutatorAsset}s into {@link MutatorConfig}. Pattern A typed-map
     * fold (the mutator is already the consumer's value type, so no extra decode step).
     */
    static void onMutatorAssetsLoaded(
            LoadedAssetsEvent<String, MutatorAsset, DefaultAssetMap<String, MutatorAsset>> event) {
        DefaultAssetMap<String, MutatorAsset> assetMap = event.getAssetMap();
        Map<String, MutatorAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, MutatorAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            MutatorAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        // Mutators are ADD-only (KweebecPackControlAsset has no "Mutators" key yet).
        MutatorConfig.getInstance().mergePackLayer(layer, false);
    }

    /**
     * Fold pack {@link HunterArchetypeAsset}s into {@link HunterArchetypeConfig}.
     * Pattern A typed-map fold (the archetype is already the consumer's value type).
     */
    static void onHunterAssetsLoaded(
            LoadedAssetsEvent<String, HunterArchetypeAsset, DefaultAssetMap<String, HunterArchetypeAsset>> event) {
        DefaultAssetMap<String, HunterArchetypeAsset> assetMap = event.getAssetMap();
        Map<String, HunterArchetypeAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, HunterArchetypeAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            HunterArchetypeAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        boolean replace = KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.HUNTERS);
        HunterArchetypeConfig.getInstance().mergePackLayer(layer, replace);
    }
}
