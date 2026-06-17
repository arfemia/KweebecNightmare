package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

/**
 * A pack-authorable scare beat: the asset-driven {@code (proximity band, corruption
 * tier) -> EntityEffect} mapping the {@code feedback/ScareDirector} reads. NO scare
 * mapping is hardcoded in Java - the director looks up the beat for a computed band +
 * tier and applies the named {@link com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect}
 * to the survivor (mirroring {@code AiHunterController.applySpeed}, but on survivors).
 *
 * <p>Two flavors of beat:
 * <ul>
 *   <li>A <b>proximity vignette band</b> ({@code Band} 1-3, {@code OneShot} false): a
 *       deepening tint/drone {@code EffectId} the director keeps applied while the
 *       survivor's nearest-hunter distance maps to that band (with hysteresis). A
 *       higher {@code Band} = closer / more dread.</li>
 *   <li>A <b>one-shot beat</b> ({@code Band} 0, {@code OneShot} true): a jumpscare hard
 *       tint + optional {@code SoundId} stinger the director fires once on a catch /
 *       near-miss, never held.</li>
 * </ul>
 * {@code MinCorruptionTier} gates a beat to a corruption floor: the director never
 * applies a beat whose {@code MinCorruptionTier} exceeds the round's current tier (a
 * deeper band can be reserved for late-round corruption).
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors {@link HunterArchetypeAsset} /
 * {@link MutatorAsset} field-for-field). The engine decodes a beat DIRECTLY into typed
 * fields via {@link #CODEC} - the codec IS the single schema authority on both the pack
 * layer and the in-jar {@link DefaultScareBeats} floor.
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start).
 *
 * <p>Pack JSON shape (vignette band, then one-shot jumpscare):
 * <pre>{@code
 * { "Name": "dread_2", "Band": 2, "MinCorruptionTier": 0,
 *   "EffectId": "KweebecNightmare_Dread_2", "SoundId": "", "OneShot": false }
 *
 * { "Name": "jumpscare", "Band": 0, "MinCorruptionTier": 0,
 *   "EffectId": "KweebecNightmare_Jumpscare", "SoundId": "SFX_Hedera_Scream",
 *   "OneShot": true }
 * }</pre>
 */
public final class ScareBeatAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, ScareBeatAsset>> {

    /** {@code Band} value of the one-shot (non-proximity) jumpscare beat. */
    public static final int JUMPSCARE_BAND = 0;
    /** Highest proximity band (closest / most dread). */
    public static final int MAX_BAND = 3;

    private String id;
    private AssetExtraInfo.Data data;

    private int band = 0;
    private int minCorruptionTier = 0;
    @Nullable private String effectId;
    @Nullable private String soundId;
    private boolean oneShot = false;

    public static final AssetBuilderCodec<String, ScareBeatAsset> CODEC = AssetBuilderCodec.builder(
                    ScareBeatAsset.class,
                    ScareBeatAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Name is an optional human-readable echo of the asset key (the
            // authoritative key is the filename) - a no-op setter so it does not trip
            // the "Unused key(s)" warning, and is emitted on encode.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("Band", Codec.INTEGER, false), (a, v) -> a.band = v, a -> a.band)
            .add()
            .append(new KeyedCodec<>("MinCorruptionTier", Codec.INTEGER, false), (a, v) -> a.minCorruptionTier = v, a -> a.minCorruptionTier)
            .add()
            .append(new KeyedCodec<>("EffectId", Codec.STRING, false), (a, v) -> a.effectId = v, a -> a.effectId)
            .add()
            .append(new KeyedCodec<>("SoundId", Codec.STRING, false), (a, v) -> a.soundId = v, a -> a.soundId)
            .add()
            .append(new KeyedCodec<>("OneShot", Codec.BOOLEAN, false), (a, v) -> a.oneShot = v, a -> a.oneShot)
            .add()
            .build();

    public ScareBeatAsset() {
    }

    /**
     * Build a beat in code (the jar's {@code defaults} floor), without going through
     * the JSON {@link #CODEC}. The shipped {@code *.json} beats author the same fields;
     * {@link DefaultScareBeats} is the zero-pack source of truth.
     */
    @Nonnull
    static ScareBeatAsset of(@Nonnull String id, int band, int minCorruptionTier,
                             @Nullable String effectId, @Nullable String soundId, boolean oneShot) {
        ScareBeatAsset a = new ScareBeatAsset();
        a.id = id;
        a.band = band;
        a.minCorruptionTier = minCorruptionTier;
        a.effectId = effectId;
        a.soundId = soundId;
        a.oneShot = oneShot;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Proximity band (1-3) this beat covers, or {@link #JUMPSCARE_BAND} (0) for the
     * non-proximity one-shot jumpscare beat.
     */
    public int band() {
        return band;
    }

    /** Corruption-tier floor: the director skips this beat when the round tier is below it. */
    public int minCorruptionTier() {
        return minCorruptionTier;
    }

    /**
     * The {@link com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect}
     * id the director applies (e.g. {@code KweebecNightmare_Dread_2}); blank/null means
     * no effect for this beat.
     */
    @Nullable
    public String effectId() {
        return effectId;
    }

    /**
     * An optional one-shot {@code SoundEvent} stinger id played alongside the effect
     * (e.g. {@code SFX_Hedera_Scream} on the jumpscare); blank/null means no sound.
     */
    @Nullable
    public String soundId() {
        return soundId;
    }

    /**
     * {@code true} for a fire-once beat (jumpscare hard tint), {@code false} for a held
     * proximity vignette band the director swaps with hysteresis.
     */
    public boolean oneShot() {
        return oneShot;
    }
}
