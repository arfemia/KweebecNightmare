package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shipped stake presets. Each bundles a fully-tuned {@link RuleSet}; the default
 * is {@link #NIGHTMARE}. An installed MMO Skill Tree can pick a preset per round
 * (or scale the resulting rule-set numbers) through the public API.
 */
public enum RoundPreset {

    /** Forgiving, solo-friendly: unlimited revives, one slow hunter, gentle corruption. */
    AMATEUR(RuleSet.builder("amateur")
            .reviveStyle(ReviveStyle.FORGIVING)
            .maxDowns(Integer.MAX_VALUE)
            .bleedOutSeconds(45)
            .hunterCount(1)
            .hunterSpeed(0.9, 1.1)
            .corruptionPerSecond(0.0008)
            .corruptionPerShrine(0.08)
            .shrineRelightSeconds(5.0)
            .build()),

    /** The default tuning: one down per player, one ramping hunter, brisk corruption. */
    NIGHTMARE(RuleSet.builder("nightmare")
            .reviveStyle(ReviveStyle.COOP_RESCUE)
            .maxDowns(1)
            .bleedOutSeconds(30)
            .hunterCount(1)
            .hunterSpeed(1.0, 1.35)
            .corruptionPerSecond(0.0014)
            .corruptionPerShrine(0.12)
            .shrineRelightSeconds(6.0)
            .build()),

    /** Brutal: first catch is permanent, two fast hunters, steep corruption. */
    HARDCORE(RuleSet.builder("hardcore")
            .reviveStyle(ReviveStyle.HARDCORE)
            .maxDowns(0)
            .bleedOutSeconds(20)
            .hunterCount(2)
            .hunterSpeed(1.1, 1.5)
            .corruptionPerSecond(0.002)
            .corruptionPerShrine(0.15)
            .shrineRelightSeconds(7.0)
            .build());

    /** The preset chosen when none is specified. */
    public static final RoundPreset DEFAULT = NIGHTMARE;

    private final RuleSet ruleSet;

    RoundPreset(@Nonnull RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    @Nonnull
    public RuleSet ruleSet() {
        return ruleSet;
    }

    /** Lowercase id used in commands + the preset name lang key ({@code kweebecnightmare.preset.<id>.name}). */
    @Nonnull
    public String id() {
        return ruleSet.presetId();
    }

    @Nonnull
    public String nameKey() {
        return "kweebecnightmare.preset." + id() + ".name";
    }

    @Nullable
    public static RoundPreset byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (RoundPreset p : values()) {
            if (p.id().equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }
}
