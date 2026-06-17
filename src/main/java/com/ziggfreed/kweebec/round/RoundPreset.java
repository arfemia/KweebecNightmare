package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.asset.DefaultPresets;
import com.ziggfreed.kweebec.asset.PresetConfig;

/**
 * Thin compatibility SHIM over {@link PresetConfig} - the pack-authorable preset
 * authority that replaced this enum as the schema source. The three baseline
 * constants survive only so older call sites (and the by-convention name keys)
 * keep compiling; every {@link RuleSet} they hand out is resolved live through
 * {@link PresetConfig#resolve(String)}, so a pack overlay is honored even via the
 * legacy enum path. Prefer {@code PresetConfig.getInstance()} +
 * {@code KweebecNightmareAPI.resolveRuleSet(...)} in new code.
 *
 * <p>The constants do NOT re-declare the tuned builders (that data lives once in
 * {@link DefaultPresets} / {@link PresetConfig}); they are just stable ids with the
 * {@link #NIGHTMARE} default preserved.
 */
public enum RoundPreset {

    /** Forgiving, solo-friendly. Tuning lives in {@link DefaultPresets#amateur()}. */
    AMATEUR("amateur"),

    /** The default tuning. Tuning lives in {@link DefaultPresets#nightmare()}. */
    NIGHTMARE("nightmare"),

    /** Brutal. Tuning lives in {@link DefaultPresets#hardcore()}. */
    HARDCORE("hardcore");

    /** The preset chosen when none is specified ({@code PresetConfig.DEFAULT}). */
    public static final RoundPreset DEFAULT = NIGHTMARE;

    private final String id;

    RoundPreset(@Nonnull String id) {
        this.id = id;
    }

    /** The effective {@link RuleSet}, resolved live through {@link PresetConfig} (honors pack overlays). */
    @Nonnull
    public RuleSet ruleSet() {
        return PresetConfig.getInstance().resolve(id);
    }

    /** Lowercase id used in commands + the preset name lang key ({@code kweebecnightmare.preset.<id>.name}). */
    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String nameKey() {
        return PresetConfig.getInstance().nameKey(id);
    }

    /** A baseline constant by id, or {@code null} if it is not one of the three jar presets. */
    @Nullable
    public static RoundPreset byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (RoundPreset p : values()) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }
}
