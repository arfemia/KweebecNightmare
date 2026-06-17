package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.round.ReviveStyle;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The jar's baseline presets (Amateur / Nightmare / Hardcore), the in-memory
 * {@code defaults} floor {@link PresetConfig} folds packs on top of. These values
 * reproduce the old hardcoded {@code RoundPreset} enum EXACTLY, so round behavior is
 * unchanged when no pack lands.
 *
 * <p>The matching {@code Server/KweebecNightmare/Presets/*.json} files are the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 */
public final class DefaultPresets {

    private DefaultPresets() {
    }

    /** Forgiving, solo-friendly: unlimited revives, one slow hunter, gentle corruption. */
    @Nonnull
    public static RuleSet amateur() {
        return RuleSet.builder("amateur")
                .reviveStyle(ReviveStyle.FORGIVING)
                .maxDowns(Integer.MAX_VALUE)
                .bleedOutSeconds(45)
                .hunterCount(1)
                .hunterSpeed(0.9, 1.1)
                .corruptionPerSecond(0.0008)
                .corruptionPerShrine(0.08)
                .shrineRelightSeconds(5.0)
                .hunterArchetype("stalker")
                .build();
    }

    /** The default tuning: one down per player, one ramping hunter, brisk corruption. */
    @Nonnull
    public static RuleSet nightmare() {
        return RuleSet.builder("nightmare")
                .reviveStyle(ReviveStyle.COOP_RESCUE)
                .maxDowns(1)
                .bleedOutSeconds(30)
                .hunterCount(1)
                .hunterSpeed(1.0, 1.35)
                .corruptionPerSecond(0.0014)
                .corruptionPerShrine(0.12)
                .shrineRelightSeconds(6.0)
                .hunterArchetype("stalker")
                .build();
    }

    /** Brutal: first catch is permanent, two fast hunters, steep corruption. */
    @Nonnull
    public static RuleSet hardcore() {
        return RuleSet.builder("hardcore")
                .reviveStyle(ReviveStyle.HARDCORE)
                .maxDowns(0)
                .bleedOutSeconds(20)
                .hunterCount(2)
                .hunterSpeed(1.1, 1.5)
                .corruptionPerSecond(0.002)
                .corruptionPerShrine(0.15)
                .shrineRelightSeconds(7.0)
                .hunterArchetype("stalker")
                .build();
    }

    /** All three baseline presets, in display order (Amateur, Nightmare, Hardcore). */
    @Nonnull
    public static List<RuleSet> all() {
        return List.of(amateur(), nightmare(), hardcore());
    }
}
