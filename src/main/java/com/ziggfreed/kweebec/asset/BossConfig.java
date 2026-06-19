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
 * The authority for boss-capstone definitions, folded {@code defaults < pack < owner}. Mirrors
 * {@link SpawnRuleConfig}: the jar ships the baseline boss (see {@link DefaultBosses}); a pack's
 * {@code Server/KweebecNightmare/Bosses/*.json} overlays by id (last-pack-wins, or {@code replace}
 * drops the defaults for that type). The owner layer is not wired this pass.
 *
 * <p>{@code boss/BossController} resolves the round's boss id (the rule-set's bossId, or the default
 * {@link DefaultBosses#WARDEN}) here, so a pack can retune the multi-phase Warden purely as DATA.
 */
public final class BossConfig {

    private static BossConfig instance;

    /** Effective bosses by lowercase id (defaults < pack). */
    private final ConcurrentHashMap<String, BossAsset> bosses = new ConcurrentHashMap<>();

    /** Cached pack layer (already-decoded bosses handed in by the asset load handler). */
    @Nullable private Map<String, BossAsset> packLayer = null;
    private boolean packReplace = false;

    private BossConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized BossConfig getInstance() {
        if (instance == null) {
            instance = new BossConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        bosses.clear();
        for (BossAsset a : DefaultBosses.all()) {
            bosses.put(a.getId().toLowerCase(Locale.ROOT), a);
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s boss-load listener. The handler hands in each pack
     * {@link BossAsset} keyed by lowercase id (decoded by the engine via the shared {@code BossAsset.CODEC}).
     *
     * @param layer   decoded bosses by lowercase id
     * @param replace {@code true} to drop the jar defaults for the bosses type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, BossAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] Boss layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + bosses.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, BossAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (BossAsset a : DefaultBosses.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        bosses.clear();
        bosses.putAll(eff);
    }

    // ==================== resolve / queries ====================

    /**
     * Resolve a boss id to its asset, falling back to the default {@link DefaultBosses#WARDEN} when the id
     * is unknown/blank (so a round always has a boss to spawn when one is enabled). Never null as long as
     * the default boss is registered.
     */
    @Nullable
    public BossAsset resolve(@Nullable String id) {
        if (id != null && !id.isBlank()) {
            BossAsset hit = bosses.get(id.toLowerCase(Locale.ROOT));
            if (hit != null) {
                return hit;
            }
        }
        return bosses.get(DefaultBosses.WARDEN.toLowerCase(Locale.ROOT));
    }

    /** A boss by id, or {@code null} if unknown (no default fallback, unlike {@link #resolve(String)}). */
    @Nullable
    public BossAsset byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return bosses.get(id.toLowerCase(Locale.ROOT));
    }

    /** All effective boss ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return bosses.keySet().stream().sorted().toList();
    }

    /** All effective bosses by id (unmodifiable). */
    @Nonnull
    public Map<String, BossAsset> getBosses() {
        return Collections.unmodifiableMap(bosses);
    }
}
