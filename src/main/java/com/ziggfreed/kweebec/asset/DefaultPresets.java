package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.round.ReviveStyle;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The jar's baseline presets, the in-memory {@code defaults} floor
 * {@link PresetConfig} folds packs on top of. The original three
 * (Amateur / Nightmare / Hardcore) reproduce the old hardcoded {@code RoundPreset}
 * enum EXACTLY, so round behavior is unchanged when no pack lands; the cycle-3
 * variety presets (Endless / Swarm / Pitch / Blitz) are new built-ins for instant
 * replayability, a couple carrying a {@code Mutators} list to demonstrate stacking.
 *
 * <p>Each baseline is a {@link Preset} pairing a base {@link RuleSet} with the
 * mutator ids that {@link PresetConfig#resolve(String)} stacks on top of it (see
 * {@link MutatorAsset}). The mutator deltas are additive + commutative.
 *
 * <p>The matching {@code Server/KweebecNightmare/Presets/*.json} files are the
 * authoring reference + editor surface (and the engine's DEFAULT_PACK asset layer);
 * this class is the source of truth for the zero-pack case.
 */
public final class DefaultPresets {

    /**
     * A baseline preset entry: its base {@link RuleSet} plus the ordered mutator ids
     * to stack on top of it during the fold. Mutator ids resolve against
     * {@link MutatorConfig}; an unknown id is skipped (never fatal).
     */
    public record Preset(@Nonnull RuleSet ruleSet, @Nonnull String[] mutatorIds) {
        @Nonnull
        static Preset of(@Nonnull RuleSet ruleSet, @Nonnull String... mutatorIds) {
            return new Preset(ruleSet, mutatorIds);
        }
    }

    private DefaultPresets() {
    }

    /** Forgiving, solo-friendly: unlimited revives, one slow hunter, gentle corruption. */
    @Nonnull
    public static RuleSet amateur() {
        return RuleSet.builder("amateur")
                .worldStructure("KweebecNightmare_Grove_Calm")
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
                .worldStructure("KweebecNightmare_Grove_Dread")
                .reviveStyle(ReviveStyle.HARDCORE)
                .maxDowns(0)
                .bleedOutSeconds(20)
                .hunterCount(2)
                .hunterSpeed(1.1, 1.5)
                .corruptionPerSecond(0.002)
                .corruptionPerShrine(0.15)
                .shrineRelightSeconds(7.0)
                .hunterArchetype("stalker")
                .exitMarker(false)
                .bossEnabled(true)
                .bossId("warden")
                .build();
    }

    // ==================== cycle-3 variety presets ====================

    /**
     * A long, exploratory marathon: a generous cap, more underground shrines, a
     * gentler corruption climb so the round breathes. No mutators - it tunes the
     * knobs directly to make a wholly different pacing.
     */
    @Nonnull
    public static RuleSet endless() {
        return RuleSet.builder("endless")
                .reviveStyle(ReviveStyle.COOP_RESCUE)
                .maxDowns(2)
                .bleedOutSeconds(40)
                .hunterCount(1)
                .hunterSpeed(1.0, 1.3)
                .shrines(3, 1)
                .caveShrineCount(4)
                .roundCapSeconds(1500)
                .corruptionPerSecond(0.0009)
                .corruptionPerShrine(0.10)
                .shrineRelightSeconds(6.0)
                .hunterArchetype("stalker")
                .build();
    }

    /**
     * Nightmare pacing, but it STACKS the {@code swarm} mutator (+1 hunter) to
     * demonstrate the mutator fold - the resolved round runs two hunters without
     * re-authoring every knob.
     */
    @Nonnull
    public static RuleSet swarm() {
        return RuleSet.builder("swarm")
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

    /**
     * Pitch-dark dread: a steep corruption ramp (more darkness + heartbeat sooner)
     * with fewer surface shrines so each relight pins you longer in the dark. Stacks
     * the {@code decay} mutator on top for an extra passive-corruption bump.
     */
    @Nonnull
    public static RuleSet pitch() {
        return RuleSet.builder("pitch")
                .reviveStyle(ReviveStyle.COOP_RESCUE)
                .maxDowns(1)
                .bleedOutSeconds(25)
                .hunterCount(1)
                .hunterSpeed(1.05, 1.45)
                .shrines(2, 1)
                .caveShrineCount(1)
                .roundCapSeconds(900)
                .corruptionPerSecond(0.0022)
                .corruptionPerShrine(0.16)
                .shrineRelightSeconds(7.0)
                .hunterArchetype("stalker")
                .build();
    }

    /**
     * A frantic sprint: short cap, fast relights, a quick brutal hunter. Stacks the
     * {@code blitz} mutator (shorter cap + faster relights) on top of an
     * already-tight base.
     */
    @Nonnull
    public static RuleSet blitz() {
        return RuleSet.builder("blitz")
                .reviveStyle(ReviveStyle.COOP_RESCUE)
                .maxDowns(1)
                .bleedOutSeconds(20)
                .hunterCount(1)
                .hunterSpeed(1.15, 1.5)
                .shrines(2, 1)
                .caveShrineCount(0)
                .roundCapSeconds(600)
                .corruptionPerSecond(0.0018)
                .corruptionPerShrine(0.14)
                .shrineRelightSeconds(5.0)
                .hunterArchetype("stalker")
                .build();
    }

    /**
     * All baseline presets paired with their stacked mutator ids, in display order.
     * Amateur / Nightmare / Hardcore carry no mutators (unchanged); Endless tunes
     * knobs directly; Swarm / Pitch / Blitz each demonstrate one stacked mutator.
     */
    @Nonnull
    public static List<Preset> all() {
        return List.of(
                Preset.of(amateur()),
                Preset.of(nightmare()),
                Preset.of(hardcore()),
                Preset.of(endless()),
                Preset.of(swarm(), DefaultMutators.SWARM),
                Preset.of(pitch(), DefaultMutators.DECAY),
                Preset.of(blitz(), DefaultMutators.BLITZ));
    }
}
