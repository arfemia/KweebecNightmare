package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline hunter archetypes - today, the single {@code stalker} that
 * reproduces the hardcoded hunter in {@code AiHunterController} exactly
 * ({@code KweebecNightmare_Blight}, count 1, the existing speed-band ladder and the
 * parallel {@code KweebecNightmare_HunterPace_*} pace effects). This is the
 * in-memory {@code defaults} floor {@link HunterArchetypeConfig} folds packs on top
 * of, so hunter behavior is unchanged when no pack lands.
 *
 * <p>The matching {@code Server/KweebecNightmare/Hunters/Stalker.json} is the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 */
public final class DefaultHunters {

    /** The archetype id used when a {@link com.ziggfreed.kweebec.round.RuleSet} names none. */
    public static final String DEFAULT = "stalker";

    private DefaultHunters() {
    }

    /**
     * The baseline relentless stalker (today's only hunter). Mirrors the
     * {@code SPEED_BANDS} / {@code BAND_EFFECT_IDS} ladder in {@code AiHunterController}
     * (an empty-string effect id = the role's 1.0x baseline, no effect), and the on-hit
     * punishment knobs authored in {@code Stalker.json} (the slow + stacking + enrage).
     */
    @Nonnull
    public static HunterArchetypeAsset stalker() {
        return HunterArchetypeAsset.of(
                DEFAULT,
                HunterArchetypeAsset.Kind.STALKER.name().toLowerCase(),
                "KweebecNightmare_Blight",
                1,
                1.0,
                0,
                new double[] {0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5},
                new String[] {
                        "KweebecNightmare_HunterPace_090",
                        "",
                        "KweebecNightmare_HunterPace_110",
                        "KweebecNightmare_HunterPace_120",
                        "KweebecNightmare_HunterPace_130",
                        "KweebecNightmare_HunterPace_140",
                        "KweebecNightmare_HunterPace_150",
                },
                // on-hit punishment (mirrors Stalker.json): slow + proximity stacking + desperation enrage.
                "KweebecNightmare_HunterSlow_1", // onHitSlowEffectId
                1.5,  // onHitSlowSeconds
                1.0,  // onHitDamageMult
                0.0,  // onHitDamageFlat
                4,    // onHitStackCap
                8.0,  // onHitStackWindowSeconds
                20.0, // enrageAfterSeconds
                1.3,  // enrageSpeedMult
                1.25, // enrageDamageMult
                5.0,  // enrageDurationSeconds
                null  // enrageSoundId (no custom enrage cue authored for the baseline stalker)
        );
    }

    /** All baseline archetypes, in display order. */
    @Nonnull
    public static List<HunterArchetypeAsset> all() {
        return List.of(stalker());
    }
}
