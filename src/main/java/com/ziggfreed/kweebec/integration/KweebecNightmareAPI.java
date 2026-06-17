package com.ziggfreed.kweebec.integration;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The optional integration facade an installed MMO Skill Tree (or any external
 * driver) calls - reflectively, since Kweebec has zero compile dependency on the
 * MMO and vice versa - to bend a round's difficulty WITHOUT touching the asset
 * layers. It is also the single in-mod resolve hook {@code RoundService} uses to
 * turn a preset id into the effective {@link RuleSet} for a round.
 *
 * <p><b>Four-tier difficulty model</b> ({@code defaults < pack < owner < runtime}):
 * <ol>
 *   <li><b>defaults</b> - the jar's baseline presets ({@code DefaultPresets}), the
 *       in-memory floor.</li>
 *   <li><b>pack</b> - a content pack's {@code Server/KweebecNightmare/Presets/*.json}
 *       overlaid by {@link PresetConfig#mergePackLayer}.</li>
 *   <li><b>owner</b> - a future {@code mods/kweebecnightmare/presets.json} (not wired
 *       this pass); still folded inside {@link PresetConfig}.</li>
 *   <li><b>runtime</b> - THIS facade. {@link #overridePreset(String)} forces a
 *       different preset id for the next round(s); {@link #scaleRuleSet(UnaryOperator)}
 *       post-transforms whatever the lower tiers resolved (e.g. add a hunter, speed
 *       it up). Runtime sits ABOVE the static fold and is the last word.</li>
 * </ol>
 *
 * <p>{@link #resolveRuleSet(String)} is the single entry point that composes all
 * four tiers: it runs the {@code defaults < pack < owner} fold via
 * {@link PresetConfig#resolve} (after applying any preset-id override), then applies
 * the registered scale operator. Both runtime overrides are process-global and
 * thread-safe; {@code null} clears each.
 */
public final class KweebecNightmareAPI {

    /** Runtime preset-id override; {@code null} = use the id the round requested. */
    private static final AtomicReference<String> PRESET_OVERRIDE = new AtomicReference<>(null);

    /** Runtime rule-set scale; {@code null} = identity (no post-transform). */
    private static final AtomicReference<UnaryOperator<RuleSet>> SCALE = new AtomicReference<>(null);

    private KweebecNightmareAPI() {
    }

    // ==================== runtime tier (external driver) ====================

    /**
     * Force a preset id for every subsequent round, regardless of what each round
     * requests (e.g. an MMO pushing the party's earned difficulty). Pass {@code null}
     * to clear the override and honor each round's own id again.
     *
     * @param presetId the preset id to force, or {@code null} to clear
     */
    public static void overridePreset(@Nullable String presetId) {
        PRESET_OVERRIDE.set(presetId == null || presetId.isBlank() ? null : presetId);
        SafeLog.info("[Kweebec][API] preset override " + (presetId == null ? "cleared" : "set to '" + presetId + "'"));
    }

    /** The active preset-id override, or {@code null} when none is set. */
    @Nullable
    public static String presetOverride() {
        return PRESET_OVERRIDE.get();
    }

    /**
     * Register a runtime transform applied to EVERY resolved {@link RuleSet} (after
     * the {@code defaults < pack < owner} fold + any preset override). The operator
     * receives the resolved rule-set and returns the effective one - typically built
     * via {@link RuleSet#toBuilder()} (e.g.
     * {@code rs -> rs.toBuilder().hunterCount(rs.hunterCount() + 1).build()}). Pass
     * {@code null} to clear the scale.
     *
     * @param scale the per-round transform, or {@code null} to clear
     */
    public static void scaleRuleSet(@Nullable UnaryOperator<RuleSet> scale) {
        SCALE.set(scale);
        SafeLog.info("[Kweebec][API] rule-set scale " + (scale == null ? "cleared" : "registered"));
    }

    /** The active scale operator, or {@code null} when none is set. */
    @Nullable
    public static UnaryOperator<RuleSet> ruleSetScale() {
        return SCALE.get();
    }

    // ==================== resolve hook (used by RoundService) ====================

    /**
     * Resolve a preset id to the effective {@link RuleSet} for a round, composing all
     * four tiers: a runtime preset-id override (if any) replaces the requested id, the
     * static {@link PresetConfig} fold ({@code defaults < pack < owner}) resolves it,
     * then the registered runtime scale (if any) post-transforms the result. Always
     * returns a non-null rule-set ({@link PresetConfig#resolve} falls back to the
     * default preset for an unknown/blank id).
     *
     * @param presetId the preset id the round requested ({@code null}/blank = the default)
     */
    @Nonnull
    public static RuleSet resolveRuleSet(@Nullable String presetId) {
        String override = PRESET_OVERRIDE.get();
        String effectiveId = override != null ? override : presetId;
        RuleSet resolved = PresetConfig.getInstance().resolve(effectiveId);
        UnaryOperator<RuleSet> scale = SCALE.get();
        if (scale == null) {
            return resolved;
        }
        try {
            RuleSet scaled = scale.apply(resolved);
            return scaled != null ? scaled : resolved;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][API] rule-set scale threw; using unscaled preset: " + t.getMessage());
            return resolved;
        }
    }
}
