package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline scare beats, the in-memory {@code defaults} floor
 * {@link ScareBeatConfig} folds packs on top of. Each maps a proximity band (or the
 * one-shot jumpscare slot) to a pre-authored
 * {@link com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect}
 * the {@code feedback/ScareDirector} applies (so the scare mapping is DATA, never
 * hardcoded in the director).
 *
 * <p>The matching {@code Server/KweebecNightmare/ScareBeats/*.json} files are the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 *
 * <p>The {@code of(...)} args are, in order:
 * {@code id, band, minCorruptionTier, effectId, soundId, oneShot}.
 *
 * <p>The three proximity bands deepen with closeness ({@code KweebecNightmare_Dread_1}
 * far, {@code _2} mid, {@code _3} closest); the jumpscare beat ({@code Band} 0,
 * {@code OneShot} true) carries the {@code KweebecNightmare_Jumpscare} hard tint and
 * the {@code SFX_Hedera_Scream} stinger. The EntityEffect / SoundEvent assets these
 * ids name are authored by the scare-effect owner; the director validates each id at
 * runtime ({@code getIndex != Integer.MIN_VALUE}) and degrades silently if absent.
 */
public final class DefaultScareBeats {

    /** Far proximity band - the first hint of dread. */
    public static final String DREAD_1 = "dread_1";
    /** Mid proximity band. */
    public static final String DREAD_2 = "dread_2";
    /** Closest proximity band - maximum dread vignette. */
    public static final String DREAD_3 = "dread_3";
    /** The one-shot jumpscare beat (catch / near-miss). */
    public static final String JUMPSCARE = "jumpscare";

    private DefaultScareBeats() {
    }

    /** Band 1 (far) -> {@code KweebecNightmare_Dread_1}, no gate, no sound, held. */
    @Nonnull
    public static ScareBeatAsset dread1() {
        return ScareBeatAsset.of(DREAD_1, 1, 0, "KweebecNightmare_Dread_1", "", false);
    }

    /** Band 2 (mid) -> {@code KweebecNightmare_Dread_2}, no gate, no sound, held. */
    @Nonnull
    public static ScareBeatAsset dread2() {
        return ScareBeatAsset.of(DREAD_2, 2, 0, "KweebecNightmare_Dread_2", "", false);
    }

    /** Band 3 (closest) -> {@code KweebecNightmare_Dread_3}, no gate, no sound, held. */
    @Nonnull
    public static ScareBeatAsset dread3() {
        return ScareBeatAsset.of(DREAD_3, 3, 0, "KweebecNightmare_Dread_3", "", false);
    }

    /** The one-shot jumpscare: hard tint + the Hedera scream stinger, fired once. */
    @Nonnull
    public static ScareBeatAsset jumpscare() {
        return ScareBeatAsset.of(JUMPSCARE, ScareBeatAsset.JUMPSCARE_BAND, 0,
                "KweebecNightmare_Jumpscare", "SFX_Hedera_Scream", true);
    }

    /** All baseline scare beats, in display order (the three bands then the jumpscare). */
    @Nonnull
    public static List<ScareBeatAsset> all() {
        return List.of(dread1(), dread2(), dread3(), jumpscare());
    }
}
