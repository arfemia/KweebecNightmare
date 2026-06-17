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
 * The authority for scare beats, folded {@code defaults < pack < owner}. Mirrors
 * {@link MutatorConfig}: the jar ships the baseline beats (the three proximity bands +
 * the jumpscare, see {@link DefaultScareBeats}); a pack's
 * {@code Server/KweebecNightmare/ScareBeats/*.json} overlays by id (last-pack-wins, or
 * {@code replace} drops the defaults for that type). The owner layer is not wired this
 * pass.
 *
 * <p>The {@code feedback/ScareDirector} resolves the beat for a computed proximity band
 * gated by corruption tier via {@link #bandBeat(int, int)}, and the one-shot jumpscare
 * via {@link #jumpscareBeat()} - so the band/tier-to-effect mapping lives entirely in
 * data, never in the director.
 */
public final class ScareBeatConfig {

    private static ScareBeatConfig instance;

    /** Effective beats by lowercase id (defaults < pack). */
    private final ConcurrentHashMap<String, ScareBeatAsset> beats = new ConcurrentHashMap<>();

    /** Cached pack layer (already-decoded beats handed in by the asset load handler). */
    @Nullable private Map<String, ScareBeatAsset> packLayer = null;
    private boolean packReplace = false;

    private ScareBeatConfig() {
        loadDefaults();
    }

    @Nonnull
    public static synchronized ScareBeatConfig getInstance() {
        if (instance == null) {
            instance = new ScareBeatConfig();
        }
        return instance;
    }

    // ==================== defaults ====================

    private synchronized void loadDefaults() {
        beats.clear();
        for (ScareBeatAsset a : DefaultScareBeats.all()) {
            beats.put(a.getId().toLowerCase(Locale.ROOT), a);
        }
    }

    // ==================== pack layer ====================

    /**
     * Entry point for {@code KweebecAssetRegistrar}'s scare-beat-load listener. The
     * handler hands in each pack {@link ScareBeatAsset} keyed by lowercase id (decoded
     * by the engine via the shared {@code ScareBeatAsset.CODEC}).
     *
     * @param layer   decoded beats by lowercase id
     * @param replace {@code true} to drop the jar defaults for the scare-beats type
     */
    public synchronized void mergePackLayer(@Nonnull Map<String, ScareBeatAsset> layer, boolean replace) {
        this.packLayer = layer;
        this.packReplace = replace;
        applyPackLayer();
        SafeLog.info("[Kweebec][AssetPacks] ScareBeat layer applied (" + layer.size()
                + " entries, mode=" + (replace ? "replace" : "add") + ") - "
                + beats.size() + " effective");
    }

    private synchronized void applyPackLayer() {
        Map<String, ScareBeatAsset> eff = new LinkedHashMap<>();
        // (a) jar defaults floor, unless the pack declares replace.
        if (!packReplace) {
            for (ScareBeatAsset a : DefaultScareBeats.all()) {
                eff.put(a.getId().toLowerCase(Locale.ROOT), a);
            }
        }
        // (b) pack layer overlays by id.
        if (packLayer != null) {
            eff.putAll(packLayer);
        }
        beats.clear();
        beats.putAll(eff);
    }

    // ==================== resolve / queries ====================

    /**
     * The proximity-vignette beat for a computed {@code band} (1-3), gated by the
     * round's current {@code corruptionTier}. Returns the highest-{@code Band} beat
     * whose {@code Band <= band} and whose {@code MinCorruptionTier <= corruptionTier},
     * or {@code null} if none qualifies (band 0 / no matching beat = clear the vignette).
     * Never returns a {@link ScareBeatAsset#oneShot()} beat.
     *
     * <p>Choosing the highest qualifying band (rather than an exact match) means a
     * sparse pack table still produces a sensible vignette - if only {@code Dread_1}
     * and {@code Dread_3} are authored, a band-2 proximity resolves to {@code Dread_1}.
     */
    @Nullable
    public ScareBeatAsset bandBeat(int band, int corruptionTier) {
        if (band <= 0) {
            return null;
        }
        ScareBeatAsset best = null;
        for (ScareBeatAsset a : beats.values()) {
            if (a.oneShot() || a.band() <= 0) {
                continue;
            }
            if (a.band() > band || a.minCorruptionTier() > corruptionTier) {
                continue;
            }
            if (best == null || a.band() > best.band()) {
                best = a;
            }
        }
        return best;
    }

    /**
     * The one-shot jumpscare beat (the first {@link ScareBeatAsset#oneShot()} entry),
     * or {@code null} if none is authored. The director fires this on a catch /
     * near-miss.
     */
    @Nullable
    public ScareBeatAsset jumpscareBeat() {
        for (ScareBeatAsset a : beats.values()) {
            if (a.oneShot()) {
                return a;
            }
        }
        return null;
    }

    /** A beat by id, or {@code null} if unknown (no default fallback). */
    @Nullable
    public ScareBeatAsset byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return beats.get(id.toLowerCase(Locale.ROOT));
    }

    /** All effective beat ids (lowercase), sorted for stable listing. */
    @Nonnull
    public List<String> list() {
        return beats.keySet().stream().sorted().toList();
    }

    /** All effective beats by id (unmodifiable). */
    @Nonnull
    public Map<String, ScareBeatAsset> getBeats() {
        return Collections.unmodifiableMap(beats);
    }
}
