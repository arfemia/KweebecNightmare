package com.ziggfreed.kweebec.asset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * A pack-authorable, STACKABLE round mutator, loaded from a pack's
 * {@code Server/KweebecNightmare/Mutators/*.json}. A mutator is a small bundle of
 * ADDITIVE deltas applied on top of an already-resolved {@link RuleSet}: a preset
 * names a list of mutator ids and each one nudges a handful of existing
 * {@link RuleSet} knobs. This is the procedural-variety seam - a preset stays a
 * coherent baseline, and orthogonal twists (a swarm, a faster hunter, a deeper cave
 * count) compose by stacking mutators rather than authoring a full new preset.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors {@link HunterArchetypeAsset}
 * field-for-field). The engine decodes a mutator DIRECTLY into typed fields via
 * {@link #CODEC} - the codec IS the single schema authority on the pack layer and
 * the in-jar {@link DefaultMutators} floor.
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start).
 *
 * <p>The deltas are <b>commutative + additive</b>: each {@link #apply(RuleSet)} only
 * reads the knob it bumps and writes back the sum, so stacking N mutators in any
 * order yields the same {@link RuleSet}. Counts are clamped to {@code >= 0} (and a
 * speed band is floored at a small positive) so a stack can never drive a knob
 * negative. A delta of {@code 0} (the default) is a no-op for that knob.
 *
 * <p>Pack JSON shape (all delta fields optional; absent = 0 = no change to that knob):
 * <pre>{@code
 * { "Name": "swarm", "NameKey": "kweebecnightmare.mutator.swarm.name",
 *   "HunterCountDelta": 1, "HunterSpeedMaxDelta": 0.0,
 *   "CaveShrineCountDelta": 0, "CorruptionPerSecondDelta": 0.0,
 *   "ShrineRelightSecondsDelta": 0.0, "RoundCapSecondsDelta": 0,
 *   "ShrineBaseDelta": 0 }
 * }</pre>
 */
public final class MutatorAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, MutatorAsset>> {

    /** Smallest a stacked speed band may fall to (a mutator can never freeze the hunter). */
    private static final double MIN_SPEED = 0.1;

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String nameKey;

    private int hunterCountDelta = 0;
    private double hunterSpeedMaxDelta = 0.0;
    private int caveShrineCountDelta = 0;
    private double corruptionPerSecondDelta = 0.0;
    private double shrineRelightSecondsDelta = 0.0;
    private int roundCapSecondsDelta = 0;
    private int shrineBaseDelta = 0;

    public static final AssetBuilderCodec<String, MutatorAsset> CODEC = AssetBuilderCodec.builder(
                    MutatorAsset.class,
                    MutatorAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Name is an optional human-readable echo of the asset key (the
            // authoritative key is the filename) - consumed by a no-op setter so it
            // doesn't trip the "Unused key(s)" warning, and emitted on encode.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("NameKey", Codec.STRING, false), (a, v) -> a.nameKey = v, a -> a.nameKey)
            .add()
            .append(new KeyedCodec<>("HunterCountDelta", Codec.INTEGER, false), (a, v) -> a.hunterCountDelta = v, a -> a.hunterCountDelta)
            .add()
            .append(new KeyedCodec<>("HunterSpeedMaxDelta", Codec.DOUBLE, false), (a, v) -> a.hunterSpeedMaxDelta = v, a -> a.hunterSpeedMaxDelta)
            .add()
            .append(new KeyedCodec<>("CaveShrineCountDelta", Codec.INTEGER, false), (a, v) -> a.caveShrineCountDelta = v, a -> a.caveShrineCountDelta)
            .add()
            .append(new KeyedCodec<>("CorruptionPerSecondDelta", Codec.DOUBLE, false), (a, v) -> a.corruptionPerSecondDelta = v, a -> a.corruptionPerSecondDelta)
            .add()
            .append(new KeyedCodec<>("ShrineRelightSecondsDelta", Codec.DOUBLE, false), (a, v) -> a.shrineRelightSecondsDelta = v, a -> a.shrineRelightSecondsDelta)
            .add()
            .append(new KeyedCodec<>("RoundCapSecondsDelta", Codec.INTEGER, false), (a, v) -> a.roundCapSecondsDelta = v, a -> a.roundCapSecondsDelta)
            .add()
            .append(new KeyedCodec<>("ShrineBaseDelta", Codec.INTEGER, false), (a, v) -> a.shrineBaseDelta = v, a -> a.shrineBaseDelta)
            .add()
            .build();

    public MutatorAsset() {
    }

    /**
     * Build a mutator in code (the jar's {@code defaults} floor), without going
     * through the JSON {@link #CODEC}. The shipped {@code *.json} mutators author the
     * same fields; {@link DefaultMutators} is the zero-pack source of truth.
     */
    @Nonnull
    static MutatorAsset of(@Nonnull String id, @Nullable String nameKey,
                           int hunterCountDelta, double hunterSpeedMaxDelta,
                           int caveShrineCountDelta, double corruptionPerSecondDelta,
                           double shrineRelightSecondsDelta, int roundCapSecondsDelta,
                           int shrineBaseDelta) {
        MutatorAsset a = new MutatorAsset();
        a.id = id;
        a.nameKey = nameKey;
        a.hunterCountDelta = hunterCountDelta;
        a.hunterSpeedMaxDelta = hunterSpeedMaxDelta;
        a.caveShrineCountDelta = caveShrineCountDelta;
        a.corruptionPerSecondDelta = corruptionPerSecondDelta;
        a.shrineRelightSecondsDelta = shrineRelightSecondsDelta;
        a.roundCapSecondsDelta = roundCapSecondsDelta;
        a.shrineBaseDelta = shrineBaseDelta;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Lang key for the mutator's display name. Falls back to the by-convention key
     * {@code kweebecnightmare.mutator.<id>.name} when no explicit {@code NameKey} is
     * authored.
     */
    @Nonnull
    public String nameKey(@Nonnull String mutatorId) {
        if (nameKey != null && !nameKey.isBlank()) {
            return nameKey;
        }
        return "kweebecnightmare.mutator." + mutatorId.toLowerCase() + ".name";
    }

    public int hunterCountDelta() {
        return hunterCountDelta;
    }

    public double hunterSpeedMaxDelta() {
        return hunterSpeedMaxDelta;
    }

    public int caveShrineCountDelta() {
        return caveShrineCountDelta;
    }

    public double corruptionPerSecondDelta() {
        return corruptionPerSecondDelta;
    }

    public double shrineRelightSecondsDelta() {
        return shrineRelightSecondsDelta;
    }

    public int roundCapSecondsDelta() {
        return roundCapSecondsDelta;
    }

    public int shrineBaseDelta() {
        return shrineBaseDelta;
    }

    /**
     * Apply this mutator's additive deltas onto a copy of {@code base} and return the
     * mutated {@link RuleSet}. Uses {@link RuleSet#toBuilder()} so EXISTING knobs are
     * summed; no new {@link RuleSet} knob is introduced. Counts are clamped to
     * {@code >= 0}, speed bands floored at a small positive, and the round cap to at
     * least 1 second, so a stack can never produce an unplayable rule-set. Commutative
     * + additive: order of stacked mutators does not matter.
     *
     * <p>{@code hunterSpeedMaxDelta} bumps ONLY the max band (the corruption-ramp
     * ceiling); the base band is left as authored so a mutator widens the ramp rather
     * than flat-shifting it. If a delta would push max below base, both move together
     * to keep {@code base <= max}.
     */
    @Nonnull
    public RuleSet apply(@Nonnull RuleSet base) {
        RuleSet.Builder b = base.toBuilder();

        if (hunterCountDelta != 0) {
            b.hunterCount(Math.max(0, base.hunterCount() + hunterCountDelta));
        }
        if (hunterSpeedMaxDelta != 0.0) {
            double newBase = base.hunterSpeedBase();
            double newMax = Math.max(MIN_SPEED, base.hunterSpeedMax() + hunterSpeedMaxDelta);
            if (newMax < newBase) {
                newBase = newMax;
            }
            b.hunterSpeed(newBase, newMax);
        }
        if (caveShrineCountDelta != 0) {
            b.caveShrineCount(Math.max(0, base.caveShrineCount() + caveShrineCountDelta));
        }
        if (corruptionPerSecondDelta != 0.0) {
            b.corruptionPerSecond(Math.max(0.0, base.corruptionPerSecond() + corruptionPerSecondDelta));
        }
        if (shrineRelightSecondsDelta != 0.0) {
            b.shrineRelightSeconds(Math.max(0.0, base.shrineRelightSeconds() + shrineRelightSecondsDelta));
        }
        if (roundCapSecondsDelta != 0) {
            b.roundCapSeconds(Math.max(1, base.roundCapSeconds() + roundCapSecondsDelta));
        }
        if (shrineBaseDelta != 0) {
            // shrines() is a paired setter; preserve the per-player slope, clamp base >= 0.
            b.shrines(Math.max(0, baseShrineBase(base) + shrineBaseDelta), shrinePerPlayer(base));
        }
        return b.build();
    }

    /**
     * Recover the authored {@code shrineBase} from a built {@link RuleSet}: it exposes
     * {@code shrineCount(partySize) = base + perPlayer*max(1,partySize)} but not the
     * raw base. {@code shrineCount(0)} clamps party size to 1 internally, so
     * {@code base = shrineCount(1) - perPlayer}.
     */
    private static int baseShrineBase(@Nonnull RuleSet rs) {
        return rs.shrineCount(1) - shrinePerPlayer(rs);
    }

    /** Recover the per-player shrine slope: {@code shrineCount(2) - shrineCount(1)}. */
    private static int shrinePerPlayer(@Nonnull RuleSet rs) {
        return rs.shrineCount(2) - rs.shrineCount(1);
    }
}
