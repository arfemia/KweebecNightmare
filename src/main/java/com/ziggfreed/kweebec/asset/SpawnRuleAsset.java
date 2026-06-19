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
 * A pack-authorable EXTRA-SPAWN RULE, loaded from a pack's
 * {@code Server/KweebecNightmare/SpawnRules/*.json}. A spawn rule fires extra hunters
 * during a round in response to a gameplay {@link Trigger} (a shrine being lit, a
 * corruption tier crossing, time elapsing, a survivor nearing the gate, or the round
 * starting), placing them via a {@link Placement} relative to the survivors so the
 * reinforcement appears NEAR the party rather than only at the den. This is the
 * asset-driven escalation seam: a pack (or the runtime API) tunes WHEN, WHERE, and HOW
 * MANY extra hunters join a round purely as DATA, no code change.
 *
 * <p><b>Pattern A - full structured asset</b> (mirrors {@link MutatorAsset} /
 * {@link HunterArchetypeAsset} field-for-field). The engine decodes a rule DIRECTLY into
 * typed fields via {@link #CODEC} - the codec IS the single schema authority on the pack
 * layer and the in-jar {@link DefaultSpawnRules} floor.
 *
 * <p>Every {@code KeyedCodec} field name is PascalCase (the constructor rejects a
 * lower-case first letter at static init, throwing at server start).
 *
 * <p>Pack JSON shape (all knobs optional; absent = the documented default):
 * <pre>{@code
 * { "Name": "shrine_reinforce", "Trigger": "SHRINE_LIT",
 *   "Placement": "NEAR_RANDOM_PLAYER", "ArchetypeId": "",
 *   "Count": 1, "Weight": 1.0, "Cap": 6, "CooldownSeconds": 8.0,
 *   "MinCorruptionTier": 0, "MaxPerRound": 3, "RingRadius": 14.0,
 *   "AtSeconds": 0, "AtTier": 0 }
 * }</pre>
 */
public final class SpawnRuleAsset
        implements JsonAssetWithMap<String, DefaultAssetMap<String, SpawnRuleAsset>> {

    /** The gameplay moment that fires a rule. Authored as the {@code Trigger} string. */
    public enum Trigger {
        /** Fires once when the hunt (ritual) begins. */
        ROUND_START,
        /** Fires each time a survivor cleanses (lights) a shrine. */
        SHRINE_LIT,
        /** Fires when the corruption tier crosses up to a new tier (0 -> 1, 1 -> 2). */
        CORRUPTION_TIER,
        /** Fires once each tick the round-elapsed time reaches the rule's {@code AtSeconds}. */
        TIME_ELAPSED,
        /** Fires when a survivor is near the gate corridor (the closing-in beat). */
        PLAYER_PROXIMITY;

        /** The trigger chosen when none is authored. */
        public static final Trigger DEFAULT = ROUND_START;

        @Nonnull
        public static Trigger fromString(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return DEFAULT;
            }
            for (Trigger t : values()) {
                if (t.name().equalsIgnoreCase(s.trim())) {
                    return t;
                }
            }
            return DEFAULT;
        }
    }

    /** Where a rule's extra hunters appear relative to the survivors. Authored as {@code Placement}. */
    public enum Placement {
        /** At the fixed hunter den (the historical spawn point). */
        DEN,
        /** In a seeded ring band around ONE randomly chosen active survivor. */
        NEAR_RANDOM_PLAYER,
        /** On a ring around the survivors' centroid (a surrounding wave). */
        RING_AROUND_PLAYERS,
        /** Scattered around the survivors' centroid at varied distances. */
        SCATTER;

        /** The placement chosen when none is authored. */
        public static final Placement DEFAULT = NEAR_RANDOM_PLAYER;

        @Nonnull
        public static Placement fromString(@Nullable String s) {
            if (s == null || s.isBlank()) {
                return DEFAULT;
            }
            for (Placement p : values()) {
                if (p.name().equalsIgnoreCase(s.trim())) {
                    return p;
                }
            }
            return DEFAULT;
        }
    }

    private String id;
    private AssetExtraInfo.Data data;

    @Nullable private String trigger;
    @Nullable private String placement;
    @Nullable private String archetypeId;
    private int count = 1;
    private double weight = 1.0;
    private int cap = 0;
    private double cooldownSeconds = 0.0;
    private int minCorruptionTier = 0;
    private int maxPerRound = 0;
    private double ringRadius = 12.0;
    private int atSeconds = 0;
    private int atTier = 0;

    public static final AssetBuilderCodec<String, SpawnRuleAsset> CODEC = AssetBuilderCodec.builder(
                    SpawnRuleAsset.class,
                    SpawnRuleAsset::new,
                    Codec.STRING,
                    (a, id) -> a.id = id,
                    a -> a.id,
                    (a, extra) -> a.data = extra,
                    a -> a.data)
            // Name is an optional human-readable echo of the asset key (the authoritative
            // key is the filename) - consumed by a no-op setter so it doesn't trip the
            // "Unused key(s)" warning, and emitted on encode.
            .append(new KeyedCodec<>("Name", Codec.STRING, false),
                    (a, name) -> { /* no-op - id already comes from the filename */ },
                    a -> a.id)
            .add()
            .append(new KeyedCodec<>("Trigger", Codec.STRING, false), (a, v) -> a.trigger = v, a -> a.trigger)
            .add()
            .append(new KeyedCodec<>("Placement", Codec.STRING, false), (a, v) -> a.placement = v, a -> a.placement)
            .add()
            .append(new KeyedCodec<>("ArchetypeId", Codec.STRING, false), (a, v) -> a.archetypeId = v, a -> a.archetypeId)
            .add()
            .append(new KeyedCodec<>("Count", Codec.INTEGER, false), (a, v) -> a.count = v, a -> a.count)
            .add()
            .append(new KeyedCodec<>("Weight", Codec.DOUBLE, false), (a, v) -> a.weight = v, a -> a.weight)
            .add()
            .append(new KeyedCodec<>("Cap", Codec.INTEGER, false), (a, v) -> a.cap = v, a -> a.cap)
            .add()
            .append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE, false), (a, v) -> a.cooldownSeconds = v, a -> a.cooldownSeconds)
            .add()
            .append(new KeyedCodec<>("MinCorruptionTier", Codec.INTEGER, false), (a, v) -> a.minCorruptionTier = v, a -> a.minCorruptionTier)
            .add()
            .append(new KeyedCodec<>("MaxPerRound", Codec.INTEGER, false), (a, v) -> a.maxPerRound = v, a -> a.maxPerRound)
            .add()
            .append(new KeyedCodec<>("RingRadius", Codec.DOUBLE, false), (a, v) -> a.ringRadius = v, a -> a.ringRadius)
            .add()
            .append(new KeyedCodec<>("AtSeconds", Codec.INTEGER, false), (a, v) -> a.atSeconds = v, a -> a.atSeconds)
            .add()
            .append(new KeyedCodec<>("AtTier", Codec.INTEGER, false), (a, v) -> a.atTier = v, a -> a.atTier)
            .add()
            .build();

    public SpawnRuleAsset() {
    }

    /**
     * Build a spawn rule in code (the jar's {@code defaults} floor), without going through
     * the JSON {@link #CODEC}. The shipped {@code *.json} rules author the same fields;
     * {@link DefaultSpawnRules} is the zero-pack source of truth.
     *
     * <p>The args are, in order: {@code id, trigger, placement, archetypeId, count, weight,
     * cap, cooldownSeconds, minCorruptionTier, maxPerRound, ringRadius, atSeconds, atTier}.
     */
    @Nonnull
    static SpawnRuleAsset of(@Nonnull String id, @Nullable String trigger, @Nullable String placement,
                            @Nullable String archetypeId, int count, double weight, int cap,
                            double cooldownSeconds, int minCorruptionTier, int maxPerRound,
                            double ringRadius, int atSeconds, int atTier) {
        SpawnRuleAsset a = new SpawnRuleAsset();
        a.id = id;
        a.trigger = trigger;
        a.placement = placement;
        a.archetypeId = archetypeId;
        a.count = count;
        a.weight = weight;
        a.cap = cap;
        a.cooldownSeconds = cooldownSeconds;
        a.minCorruptionTier = minCorruptionTier;
        a.maxPerRound = maxPerRound;
        a.ringRadius = ringRadius;
        a.atSeconds = atSeconds;
        a.atTier = atTier;
        return a;
    }

    @Override
    public String getId() {
        return id;
    }

    /** The gameplay moment that fires this rule (defaults to {@link Trigger#DEFAULT}). */
    @Nonnull
    public Trigger trigger() {
        return Trigger.fromString(trigger);
    }

    /** Where this rule's extra hunters appear (defaults to {@link Placement#DEFAULT}). */
    @Nonnull
    public Placement placement() {
        return Placement.fromString(placement);
    }

    /**
     * The hunter archetype id this rule spawns, PREPENDED to the eligible roster as the
     * primary pick, or {@code null}/blank to draw purely from the corruption-eligible roster.
     */
    @Nullable
    public String archetypeId() {
        return archetypeId;
    }

    /** How many extra hunters one fire of this rule requests (clamped to at least 1 by the consumer). */
    public int count() {
        return count;
    }

    /** Weighted-selection weight when this rule's archetype competes in the roster pick. */
    public double weight() {
        return weight;
    }

    /**
     * Per-rule live-hunter ceiling; {@code 0} = defer to the global MAX_HUNTERS cap. The consumer
     * always also honors the global cap, so a rule {@code Cap} only LOWERS the ceiling for this rule.
     */
    public int cap() {
        return cap;
    }

    /** Minimum seconds between two fires of this rule; {@code 0} = no cooldown. */
    public double cooldownSeconds() {
        return cooldownSeconds;
    }

    /** Corruption tier at/after which this rule is eligible to fire (0 = always). */
    public int minCorruptionTier() {
        return minCorruptionTier;
    }

    /** Maximum fires per round; {@code 0} = unlimited (still bounded by the cap + cooldown). */
    public int maxPerRound() {
        return maxPerRound;
    }

    /** Ring/scatter radius (blocks) for the player-relative placements. */
    public double ringRadius() {
        return ringRadius;
    }

    /** For {@link Trigger#TIME_ELAPSED}: round-elapsed seconds at/after which the rule may fire. */
    public int atSeconds() {
        return atSeconds;
    }

    /** For {@link Trigger#CORRUPTION_TIER}: the specific tier whose crossing fires the rule (0 = any crossing). */
    public int atTier() {
        return atTier;
    }
}
