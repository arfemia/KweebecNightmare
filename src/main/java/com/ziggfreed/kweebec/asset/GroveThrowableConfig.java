package com.ziggfreed.kweebec.asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The authority for grove throwables, folded {@code pack < owner} over an EMPTY jar floor. Unlike the
 * other kweebec asset configs ({@link PresetConfig}/{@link HunterArchetypeConfig}/{@link MutatorConfig})
 * this ships ZERO code defaults - a grove throwable is pure pack CONTENT, so the kweebec asset pack ships
 * the entries as {@code Server/KweebecNightmare/GroveThrowables/*.json} (Gust/Mire, disabled) and any pack
 * adds more. Per the asset-driven paradigm: a new utility throwable = a new JSON file, zero Java.
 *
 * <p>Populated by {@code KweebecAssetRegistrar}'s {@code LoadedAssetsEvent} fold AFTER plugin setup;
 * consumers (the {@code ArenaBuilder} grove-throwable distribution) read it LAZILY at round time. ADD-only
 * (no {@code KweebecPackControlAsset} key), like {@link MutatorConfig}.
 */
public final class GroveThrowableConfig {

    private static GroveThrowableConfig instance;

    /** Effective grove throwables by lowercase id (pack layer; no jar defaults). */
    private final ConcurrentHashMap<String, GroveThrowableAsset> entries = new ConcurrentHashMap<>();

    private GroveThrowableConfig() {
    }

    @Nonnull
    public static synchronized GroveThrowableConfig getInstance() {
        if (instance == null) {
            instance = new GroveThrowableConfig();
        }
        return instance;
    }

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s grove-throwable load listener: replace the effective
     * set with the decoded pack layer (the engine has already merged every pack by id). ADD-only - there is
     * no jar floor to preserve.
     *
     * @param layer decoded grove throwables by lowercase id
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, GroveThrowableAsset> layer) {
        entries.clear();
        entries.putAll(layer);
        SafeLog.info("[Kweebec][AssetPacks] GroveThrowable layer applied (" + layer.size()
                + " entries, " + placeable().size() + " enabled+placeable)");
    }

    /** All effective grove throwables by id (unmodifiable). */
    @Nonnull
    public Map<String, GroveThrowableAsset> all() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * The grove throwables that would actually place something this round (enabled, a prefab, count > 0) -
     * the set the {@code ArenaBuilder} distribution iterates. Empty when nothing is enabled (the shipped
     * default: the Gust/Mire entries are present but {@code Enabled:false}), so the loop is a no-op.
     */
    @Nonnull
    public List<GroveThrowableAsset> placeable() {
        List<GroveThrowableAsset> out = new ArrayList<>();
        for (GroveThrowableAsset a : entries.values()) {
            if (a.isPlaceable()) {
                out.add(a);
            }
        }
        return out;
    }

    /** A grove throwable by id (lowercased), or {@code null} if unknown. */
    public GroveThrowableAsset byId(String id) {
        return id == null ? null : entries.get(id.toLowerCase(Locale.ROOT));
    }
}
