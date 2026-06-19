package com.ziggfreed.kweebec.integration;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.instance.encounter.MultiPhaseBossAsset;
import com.ziggfreed.common.instance.encounter.MultiPhaseBossConfig;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.common.instance.encounter.EncounterRuleAsset;
import com.ziggfreed.common.instance.encounter.EncounterRuleConfig;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.score.ScoringConfig;
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

    /** Runtime scoring-config override; {@code null} = use {@link ScoringConfig#DEFAULT}. */
    private static final AtomicReference<ScoringConfig> SCORING_OVERRIDE = new AtomicReference<>(null);

    /** Runtime scoring-config scale; {@code null} = identity. */
    private static final AtomicReference<UnaryOperator<ScoringConfig>> SCORING_SCALE = new AtomicReference<>(null);

    /** Runtime spawn-rules override; {@code null} = use the common {@link EncounterRuleConfig} fold. */
    private static final AtomicReference<List<EncounterRuleAsset>> SPAWN_RULES_OVERRIDE = new AtomicReference<>(null);

    /** Runtime spawn-rules scale (post-transform of the resolved list); {@code null} = identity. */
    private static final AtomicReference<UnaryOperator<List<EncounterRuleAsset>>> SPAWN_RULES_SCALE = new AtomicReference<>(null);

    /** Runtime boss-id override; {@code null} = use the boss id the round requested (or the default). */
    private static final AtomicReference<String> BOSS_OVERRIDE = new AtomicReference<>(null);

    /** Runtime boss scale (post-transform of the resolved boss); {@code null} = identity. */
    private static final AtomicReference<UnaryOperator<MultiPhaseBossAsset>> BOSS_SCALE = new AtomicReference<>(null);

    /** Kweebec's default boss id (the corrupted-Kweebec Warden), the resolve fallback. */
    private static final String DEFAULT_BOSS = "warden";

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

    // ==================== scoring runtime tier ====================

    /**
     * Force the round-scoring weights for every subsequent round (e.g. an MMO tuning how a capstone
     * run is scored). Pass {@code null} to clear and use {@link ScoringConfig#DEFAULT}.
     */
    public static void overrideScoring(@Nullable ScoringConfig scoring) {
        SCORING_OVERRIDE.set(scoring);
        SafeLog.info("[Kweebec][API] scoring override " + (scoring == null ? "cleared" : "set"));
    }

    /**
     * Register a runtime transform applied to the resolved {@link ScoringConfig} (after any override),
     * typically via {@link ScoringConfig#toBuilder()}. Pass {@code null} to clear.
     */
    public static void scaleScoring(@Nullable UnaryOperator<ScoringConfig> scale) {
        SCORING_SCALE.set(scale);
        SafeLog.info("[Kweebec][API] scoring scale " + (scale == null ? "cleared" : "registered"));
    }

    /**
     * Resolve the effective {@link ScoringConfig} for a round against the {@link ScoringConfig#DEFAULT}
     * base. Equivalent to {@link #resolveScoring(ScoringConfig)} with the default base.
     */
    @Nonnull
    public static ScoringConfig resolveScoring() {
        return resolveScoring(ScoringConfig.DEFAULT);
    }

    /**
     * Resolve the effective {@link ScoringConfig} for a round: the runtime override (if set) ELSE the
     * given per-preset {@code presetScoring} base, then the runtime scale if registered. Always
     * non-null. Used by {@code RoundService.resolve}, which passes the round's preset scoring so each
     * difficulty can score differently while an installed MMO can still force/scale it at runtime.
     */
    @Nonnull
    public static ScoringConfig resolveScoring(@Nonnull ScoringConfig presetScoring) {
        ScoringConfig base = SCORING_OVERRIDE.get();
        if (base == null) {
            base = presetScoring;
        }
        UnaryOperator<ScoringConfig> scale = SCORING_SCALE.get();
        if (scale == null) {
            return base;
        }
        try {
            ScoringConfig scaled = scale.apply(base);
            return scaled != null ? scaled : base;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][API] scoring scale threw; using unscaled config: " + t.getMessage());
            return base;
        }
    }

    // ==================== spawn-rules runtime tier (extra-hunter escalation) ====================

    /**
     * Force the EXTRA-SPAWN RULE set for every subsequent round, replacing whatever the
     * {@code defaults < pack} {@link EncounterRuleConfig} fold yields (e.g. an MMO scripting a bespoke
     * escalation for a capstone run, or disabling extra spawns by passing an empty list). Pass
     * {@code null} to clear the override and honor the static fold again. The list is copied defensively.
     *
     * @param rules the rule set to force, or {@code null} to clear (empty list = no extra spawns)
     */
    public static void overrideSpawnRules(@Nullable List<EncounterRuleAsset> rules) {
        SPAWN_RULES_OVERRIDE.set(rules == null ? null : List.copyOf(rules));
        SafeLog.info("[Kweebec][API] spawn-rules override "
                + (rules == null ? "cleared" : "set to " + rules.size() + " rule(s)"));
    }

    /** The active spawn-rules override, or {@code null} when none is set. */
    @Nullable
    public static List<EncounterRuleAsset> spawnRulesOverride() {
        return SPAWN_RULES_OVERRIDE.get();
    }

    /**
     * Register a runtime transform applied to the resolved EXTRA-SPAWN RULE list (after any override, over
     * the static fold) - e.g. filter out a trigger, or remap counts. The operator receives the resolved
     * list and returns the effective one. Pass {@code null} to clear the scale.
     *
     * @param scale the per-round transform, or {@code null} to clear
     */
    public static void scaleSpawnRules(@Nullable UnaryOperator<List<EncounterRuleAsset>> scale) {
        SPAWN_RULES_SCALE.set(scale);
        SafeLog.info("[Kweebec][API] spawn-rules scale " + (scale == null ? "cleared" : "registered"));
    }

    /** The active spawn-rules scale operator, or {@code null} when none is set. */
    @Nullable
    public static UnaryOperator<List<EncounterRuleAsset>> spawnRulesScale() {
        return SPAWN_RULES_SCALE.get();
    }

    /**
     * Resolve the effective EXTRA-SPAWN RULE list for a round, composing all tiers: the runtime override
     * (if set) ELSE the static {@code defaults < pack < owner} fold via {@link EncounterRuleConfig}, then the
     * registered runtime scale (if any). Always returns a non-null (possibly empty) list. Consumed by
     * {@code AiHunterController.evaluateSpawnRules}.
     */
    @Nonnull
    public static List<EncounterRuleAsset> resolveSpawnRules() {
        List<EncounterRuleAsset> base = SPAWN_RULES_OVERRIDE.get();
        if (base == null) {
            base = List.copyOf(EncounterRuleConfig.getInstance().all().values());
        }
        UnaryOperator<List<EncounterRuleAsset>> scale = SPAWN_RULES_SCALE.get();
        if (scale == null) {
            return base;
        }
        try {
            List<EncounterRuleAsset> scaled = scale.apply(base);
            return scaled != null ? scaled : base;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][API] spawn-rules scale threw; using unscaled list: " + t.getMessage());
            return base;
        }
    }

    // ==================== boss runtime tier (capstone) ====================

    /**
     * Force the boss id for every subsequent round, replacing whatever the round requested (e.g. an MMO
     * scripting a bespoke capstone). Pass {@code null} to clear and honor each round's own id again.
     */
    public static void overrideBoss(@Nullable String bossId) {
        BOSS_OVERRIDE.set(bossId == null || bossId.isBlank() ? null : bossId);
        SafeLog.info("[Kweebec][API] boss override " + (bossId == null ? "cleared" : "set to '" + bossId + "'"));
    }

    /** The active boss-id override, or {@code null} when none is set. */
    @Nullable
    public static String bossOverride() {
        return BOSS_OVERRIDE.get();
    }

    /**
     * Register a runtime transform applied to the resolved {@link MultiPhaseBossAsset} (after any override,
     * over the static fold). Pass {@code null} to clear.
     */
    public static void scaleBoss(@Nullable UnaryOperator<MultiPhaseBossAsset> scale) {
        BOSS_SCALE.set(scale);
        SafeLog.info("[Kweebec][API] boss scale " + (scale == null ? "cleared" : "registered"));
    }

    /** The active boss scale operator, or {@code null} when none is set. */
    @Nullable
    public static UnaryOperator<MultiPhaseBossAsset> bossScale() {
        return BOSS_SCALE.get();
    }

    /**
     * Resolve the boss id a round requested to its effective {@link MultiPhaseBossAsset}, composing all
     * tiers: the runtime boss-id override (if any) replaces the requested id, the ziggfreed-common
     * {@link MultiPhaseBossConfig} fold ({@code defaults < pack < owner}) resolves it (falling back to the
     * default Warden for an unknown id), then the registered runtime scale (if any) post-transforms the
     * result. May return {@code null} when neither the requested id nor the Warden is authored (the boss is
     * pack JSON now, so a missing pack yields no boss - {@code BossController.forRound} skips it gracefully).
     * Consumed by {@code boss/BossController}.
     *
     * @param bossId the boss id the round requested ({@code null}/blank = the default Warden)
     */
    @Nullable
    public static MultiPhaseBossAsset resolveBoss(@Nullable String bossId) {
        String override = BOSS_OVERRIDE.get();
        String effectiveId = override != null ? override : bossId;
        MultiPhaseBossConfig config = MultiPhaseBossConfig.getInstance();
        MultiPhaseBossAsset resolved = config.resolve(effectiveId == null || effectiveId.isBlank() ? DEFAULT_BOSS : effectiveId);
        if (resolved == null) {
            resolved = config.resolve(DEFAULT_BOSS); // common has no built-in fallback; pin Kweebec's Warden
        }
        UnaryOperator<MultiPhaseBossAsset> scale = BOSS_SCALE.get();
        if (scale == null || resolved == null) {
            return resolved;
        }
        try {
            MultiPhaseBossAsset scaled = scale.apply(resolved);
            return scaled != null ? scaled : resolved;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][API] boss scale threw; using unscaled boss: " + t.getMessage());
            return resolved;
        }
    }
}
