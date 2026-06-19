package com.ziggfreed.kweebec.asset;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.codec.AssetCodec;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.ziggfreed.common.asset.AssetStoreRegistrar;
import com.ziggfreed.common.instance.preset.InstancePresetAsset;
import com.ziggfreed.kweebec.experience.KweebecExperience;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Registers Kweebec Nightmare's custom asset types and their load listeners during
 * plugin {@code setup()}. Mirrors hyMMO's {@code AssetStoreRegistrar}
 * field-for-field (the {@code registerStore} HytaleAssetStore.Builder chain and the
 * {@code LoadedAssetsEvent} fold), scoped to Kweebec's two Pattern-A content types
 * ({@code Presets}, {@code Hunters}) plus the {@code Control} store.
 *
 * <p>The stores are pure delivery channels: a pack drops JSON under
 * {@code Server/KweebecNightmare/<Type>/}, the engine loads and merges it by id
 * (last-pack-wins), and the load listeners fold the loaded entries into the existing
 * config singletons ({@link PresetConfig} / {@link HunterArchetypeConfig}).
 *
 * <p>The {@link KweebecPackControlAsset} store is registered FIRST and both content
 * stores declare {@code loadsAfter(KweebecPackControlAsset.class)}, so per-type
 * merge modes ({@code add}/{@code replace}) resolve before any content merge handler
 * runs. {@code setup()} runs before the engine's load event, so these stores exist
 * before any pack is loaded.
 *
 * <p>Presets and Hunters are <b>Pattern A</b> structured assets (the engine decodes
 * each into typed fields directly via its CODEC, no {@code Payload} blob), so each
 * handler iterates the typed asset map and folds the decoded objects - the same
 * shape as hyMMO's {@code onQuestGiverAssetsLoaded}, NOT the raw-JSON
 * {@code buildLayer} path the DSL types use.
 */
public final class KweebecAssetRegistrar {

    /** Asset path roots under a pack's {@code Server/}. */
    private static final String CONTROL_PATH = "KweebecNightmare/Control";
    private static final String PRESETS_PATH = "KweebecNightmare/Presets";
    private static final String HUNTERS_PATH = "KweebecNightmare/Hunters";
    private static final String MUTATORS_PATH = "KweebecNightmare/Mutators";
    private static final String SPAWNRULES_PATH = "KweebecNightmare/SpawnRules";
    private static final String BOSSES_PATH = "KweebecNightmare/Bosses";
    private static final String STRUCTURES_PATH = "KweebecNightmare/Structures";
    private static final String SCAREBEATS_PATH = "KweebecNightmare/ScareBeats";
    private static final String INSTANCES_PATH = "KweebecNightmare/Instances";

    private KweebecAssetRegistrar() {
    }

    public static void registerAll(@Nonnull JavaPlugin plugin) {
        // Control store - no loadsAfter; both content stores load after it so the
        // per-type add/replace modes are resolvable before a content fold runs.
        registerStore(KweebecPackControlAsset.class, new DefaultAssetMap<String, KweebecPackControlAsset>(),
                CONTROL_PATH, KweebecPackControlAsset::getId, KweebecPackControlAsset.CODEC, null);

        // Round presets (Pattern A) - loadsAfter the control store.
        registerStore(RoundPresetAsset.class, new DefaultAssetMap<String, RoundPresetAsset>(),
                PRESETS_PATH, RoundPresetAsset::getId, RoundPresetAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, RoundPresetAsset.class,
                KweebecAssetRegistrar::onPresetAssetsLoaded);

        // Hunter archetypes (Pattern A) - loadsAfter the control store.
        registerStore(HunterArchetypeAsset.class, new DefaultAssetMap<String, HunterArchetypeAsset>(),
                HUNTERS_PATH, HunterArchetypeAsset::getId, HunterArchetypeAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, HunterArchetypeAsset.class,
                KweebecAssetRegistrar::onHunterAssetsLoaded);

        // Round mutators (Pattern A) - loadsAfter the control store.
        registerStore(MutatorAsset.class, new DefaultAssetMap<String, MutatorAsset>(),
                MUTATORS_PATH, MutatorAsset::getId, MutatorAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, MutatorAsset.class,
                KweebecAssetRegistrar::onMutatorAssetsLoaded);

        // Extra-spawn rules (Pattern A) - loadsAfter the control store.
        registerStore(SpawnRuleAsset.class, new DefaultAssetMap<String, SpawnRuleAsset>(),
                SPAWNRULES_PATH, SpawnRuleAsset::getId, SpawnRuleAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, SpawnRuleAsset.class,
                KweebecAssetRegistrar::onSpawnRuleAssetsLoaded);

        // Boss capstones (Pattern A) - loadsAfter the control store.
        registerStore(BossAsset.class, new DefaultAssetMap<String, BossAsset>(),
                BOSSES_PATH, BossAsset::getId, BossAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, BossAsset.class,
                KweebecAssetRegistrar::onBossAssetsLoaded);

        // Corrupted-structure placements (Pattern A) - loadsAfter the control store.
        registerStore(StructurePlacementAsset.class, new DefaultAssetMap<String, StructurePlacementAsset>(),
                STRUCTURES_PATH, StructurePlacementAsset::getId, StructurePlacementAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, StructurePlacementAsset.class,
                KweebecAssetRegistrar::onStructureAssetsLoaded);

        // Scare beats (Pattern A) - loadsAfter the control store.
        registerStore(ScareBeatAsset.class, new DefaultAssetMap<String, ScareBeatAsset>(),
                SCAREBEATS_PATH, ScareBeatAsset::getId, ScareBeatAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, ScareBeatAsset.class,
                KweebecAssetRegistrar::onScareBeatAssetsLoaded);

        // Instance presets (the GENERIC ziggfreed-common cross-cutting layer: queue policy +
        // leaderboard config + the asset-driven reward list), registered via the lifted common
        // registrar. Field-disjoint from RoundPresetAsset over the SAME preset-id namespace.
        AssetStoreRegistrar.registerStore(InstancePresetAsset.class,
                new DefaultAssetMap<String, InstancePresetAsset>(), INSTANCES_PATH,
                InstancePresetAsset::getId, InstancePresetAsset.CODEC,
                new Class<?>[]{KweebecPackControlAsset.class});
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, InstancePresetAsset.class,
                KweebecExperience::onInstanceAssetsLoaded);

        SafeLog.info("[Kweebec][AssetPacks] Registered Kweebec content asset stores (Presets, Hunters, Mutators, SpawnRules, Bosses, Structures, ScareBeats, Instances, Control)");
    }

    // ==================== load listeners (Pattern A typed-map fold) ====================

    /**
     * Fold pack {@link RoundPresetAsset}s into {@link PresetConfig}. Iterates the
     * typed asset map (Pattern A), skips engine-base entries, decodes each to a
     * {@link RuleSet} + name key via the asset's own {@code toRuleSet}/{@code nameKey}.
     */
    static void onPresetAssetsLoaded(
            LoadedAssetsEvent<String, RoundPresetAsset, DefaultAssetMap<String, RoundPresetAsset>> event) {
        DefaultAssetMap<String, RoundPresetAsset> assetMap = event.getAssetMap();
        Map<String, RuleSet> layer = new LinkedHashMap<>();
        Map<String, String[]> mutatorIds = new LinkedHashMap<>();
        Map<String, String> nameKeys = new LinkedHashMap<>();
        for (Map.Entry<String, RoundPresetAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            RoundPresetAsset asset = entry.getValue();
            if (asset == null || !asset.isEnabled()) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            layer.put(id, asset.toRuleSet(id));
            mutatorIds.put(id, asset.mutators());
            nameKeys.put(id, asset.nameKey(id));
        }
        boolean replace = KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.PRESETS);
        PresetConfig.getInstance().mergePackLayer(layer, mutatorIds, nameKeys, replace);
    }

    /**
     * Fold pack {@link MutatorAsset}s into {@link MutatorConfig}. Pattern A typed-map
     * fold (the mutator is already the consumer's value type, so no extra decode
     * step) - the same shape as {@link #onHunterAssetsLoaded}.
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
        // Mutators are ADD-only: the per-type replace control lives in
        // KweebecPackControlAsset (parent-owned this pass), which has no "Mutators"
        // field yet, so a pack always UNIONS its mutators over the jar defaults. To
        // enable replace, the parent adds a "Mutators" key to KweebecPackControlAsset
        // (mirroring its Presets/Hunters fields) and this line becomes
        // KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.MUTATORS).
        MutatorConfig.getInstance().mergePackLayer(layer, false);
    }

    /**
     * Fold pack {@link SpawnRuleAsset}s into {@link SpawnRuleConfig}. Pattern A typed-map fold (the rule
     * is already the consumer's value type, so no extra decode step) - the same shape as
     * {@link #onMutatorAssetsLoaded}. The per-type replace mode is honored via the
     * {@link KweebecPackControlAsset#SPAWNRULES} control key.
     */
    static void onSpawnRuleAssetsLoaded(
            LoadedAssetsEvent<String, SpawnRuleAsset, DefaultAssetMap<String, SpawnRuleAsset>> event) {
        DefaultAssetMap<String, SpawnRuleAsset> assetMap = event.getAssetMap();
        Map<String, SpawnRuleAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, SpawnRuleAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            SpawnRuleAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        boolean replace = KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.SPAWNRULES);
        SpawnRuleConfig.getInstance().mergePackLayer(layer, replace);
    }

    /**
     * Fold pack {@link BossAsset}s into {@link BossConfig}. Pattern A typed-map fold (the boss is already
     * the consumer's value type, so no extra decode step) - the same shape as {@link #onSpawnRuleAssetsLoaded}.
     * The per-type replace mode is honored via the {@link KweebecPackControlAsset#BOSSES} control key.
     */
    static void onBossAssetsLoaded(
            LoadedAssetsEvent<String, BossAsset, DefaultAssetMap<String, BossAsset>> event) {
        DefaultAssetMap<String, BossAsset> assetMap = event.getAssetMap();
        Map<String, BossAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, BossAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            BossAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        boolean replace = KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.BOSSES);
        BossConfig.getInstance().mergePackLayer(layer, replace);
    }

    /**
     * Fold pack {@link StructurePlacementAsset}s into {@link StructureConfig}. Pattern
     * A typed-map fold (the placement is already the consumer's value type, so no extra
     * decode step) - the same shape as {@link #onMutatorAssetsLoaded}.
     */
    static void onStructureAssetsLoaded(
            LoadedAssetsEvent<String, StructurePlacementAsset, DefaultAssetMap<String, StructurePlacementAsset>> event) {
        DefaultAssetMap<String, StructurePlacementAsset> assetMap = event.getAssetMap();
        Map<String, StructurePlacementAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, StructurePlacementAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            StructurePlacementAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        // Structures are ADD-only: the per-type replace control lives in
        // KweebecPackControlAsset (parent-owned this pass), which has no "Structures"
        // field yet, so a pack always UNIONS its placements over the jar defaults. To
        // enable replace, the parent adds a "Structures" key to KweebecPackControlAsset
        // (mirroring its Presets/Hunters fields) and this line becomes
        // KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.STRUCTURES).
        StructureConfig.getInstance().mergePackLayer(layer, false);
    }

    /**
     * Fold pack {@link ScareBeatAsset}s into {@link ScareBeatConfig}. Pattern A
     * typed-map fold (the beat is already the consumer's value type, so no extra decode
     * step) - the same shape as {@link #onMutatorAssetsLoaded}.
     */
    static void onScareBeatAssetsLoaded(
            LoadedAssetsEvent<String, ScareBeatAsset, DefaultAssetMap<String, ScareBeatAsset>> event) {
        DefaultAssetMap<String, ScareBeatAsset> assetMap = event.getAssetMap();
        Map<String, ScareBeatAsset> layer = new LinkedHashMap<>();
        for (Map.Entry<String, ScareBeatAsset> entry : assetMap.getAssetMap().entrySet()) {
            String key = entry.getKey();
            if (DefaultAssetMap.DEFAULT_PACK_KEY.equals(assetMap.getAssetPack(key))) {
                continue;
            }
            ScareBeatAsset asset = entry.getValue();
            if (asset == null) {
                continue;
            }
            layer.put(key.toLowerCase(Locale.ROOT), asset);
        }
        // Scare beats are ADD-only: the per-type replace control lives in
        // KweebecPackControlAsset (parent-owned this pass), which has no "ScareBeats"
        // field yet, so a pack always UNIONS its beats over the jar defaults. To enable
        // replace, the parent adds a "ScareBeats" key to KweebecPackControlAsset
        // (mirroring its Presets/Hunters fields) and this line becomes
        // KweebecPackControlRegistry.isReplace(KweebecPackControlAsset.SCAREBEATS).
        ScareBeatConfig.getInstance().mergePackLayer(layer, false);
    }

    /**
     * Fold pack {@link HunterArchetypeAsset}s into {@link HunterArchetypeConfig}.
     * Pattern A typed-map fold (the archetype is already the consumer's value type,
     * so no extra decode step).
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

    // ==================== store registration ====================

    // The base AssetStore.Builder is package-protected, so we chain through the
    // public HytaleAssetStore.Builder, casting each fluent result back to it (the
    // inherited setters return the erased base type) - mirrors hyMMO's
    // AssetStoreRegistrar.registerStore and the engine's own built-in plugins.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T extends JsonAssetWithMap<String, M>, M extends AssetMap<String, T>> void registerStore(
            Class<T> assetClass, M map, String path, Function<T, String> keyFn,
            AssetCodec<String, T> codec, Class<?>[] loadsAfter) {
        HytaleAssetStore.Builder b = HytaleAssetStore.builder(assetClass, map);
        b = (HytaleAssetStore.Builder) b.setPath(path);
        b = (HytaleAssetStore.Builder) b.setKeyFunction(keyFn);
        b = (HytaleAssetStore.Builder) b.setCodec(codec);
        if (loadsAfter != null && loadsAfter.length > 0) {
            b = (HytaleAssetStore.Builder) b.loadsAfter(loadsAfter);
        }
        AssetRegistry.register(b.build());
    }
}
