package com.ziggfreed.kweebec.hunter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.asset.HunterArchetypeAsset;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The resolved on-hit punishment bundle for ONE hunter: the slow effect + duration, the
 * outgoing-damage scaling, the proximity-stack window/cap, and the desperation-enrage knobs.
 * Built by folding a hunter's {@link HunterArchetypeAsset} over the round's {@link RuleSet}
 * baseline - a non-null/non-zero archetype field WINS, an absent (zero / blank) field defers
 * to the rule-set default (see {@link HunterController#resolveOnHitConfigFor}).
 *
 * <p>Immutable + engine-light (only primitives + a couple of effect/sound ids), so it is safe
 * to build per damage event on the world thread. The enrage damage multiplier is applied on
 * top of {@link #damageMult} ONLY while a unit is enraged, via the {@code enraged} flag the
 * controller passes when it resolves the config.
 */
public final class OnHitConfig {

    @Nullable private final String slowEffectId;
    private final double slowSeconds;
    private final double damageMult;
    private final double damageFlat;
    private final int stackCap;
    private final double stackWindowSeconds;
    private final double enrageAfterSeconds;
    private final double enrageSpeedMult;
    private final double enrageDamageMult;
    private final double enrageDurationSeconds;
    @Nullable private final String enrageSoundId;

    OnHitConfig(@Nullable String slowEffectId, double slowSeconds, double damageMult, double damageFlat,
                int stackCap, double stackWindowSeconds, double enrageAfterSeconds, double enrageSpeedMult,
                double enrageDamageMult, double enrageDurationSeconds, @Nullable String enrageSoundId) {
        this.slowEffectId = slowEffectId;
        this.slowSeconds = slowSeconds;
        this.damageMult = damageMult;
        this.damageFlat = damageFlat;
        this.stackCap = stackCap;
        this.stackWindowSeconds = stackWindowSeconds;
        this.enrageAfterSeconds = enrageAfterSeconds;
        this.enrageSpeedMult = enrageSpeedMult;
        this.enrageDamageMult = enrageDamageMult;
        this.enrageDurationSeconds = enrageDurationSeconds;
        this.enrageSoundId = enrageSoundId;
    }

    /**
     * Fold an archetype's on-hit overrides over the round's rule-set baseline, with an
     * {@code enraged} flag deciding whether the enrage damage multiplier is baked into the
     * effective {@link #damageMult}. A non-null/non-zero archetype field wins; absent fields
     * defer to the baseline. Counts/durations are clamped to safe minimums.
     */
    @Nonnull
    static OnHitConfig resolve(@Nonnull RuleSet rules, @Nullable HunterArchetypeAsset archetype, boolean enraged) {
        String slowId = pickString(archetype == null ? null : archetype.onHitSlowEffectId(), rules.onHitSlowEffectId());
        double slowSec = pickDouble(archetype == null ? 0.0 : archetype.onHitSlowSeconds(), rules.onHitSlowSeconds());
        double dmgMult = pickDouble(archetype == null ? 0.0 : archetype.onHitDamageMult(), rules.onHitDamageMult());
        if (dmgMult <= 0.0) {
            dmgMult = 1.0; // a 0 baseline means "no override"; never zero out the hit
        }
        double dmgFlat = pickDouble(archetype == null ? 0.0 : archetype.onHitDamageFlat(), rules.onHitDamageFlat());
        int cap = (int) pickDouble(archetype == null ? 0 : archetype.onHitStackCap(), rules.onHitStackCap());
        double window = pickDouble(archetype == null ? 0.0 : archetype.onHitStackWindowSeconds(), rules.onHitStackWindowSeconds());
        double enrageAfter = pickDouble(archetype == null ? 0.0 : archetype.enrageAfterSeconds(), rules.enrageAfterSeconds());
        double enrageSpeed = pickDouble(archetype == null ? 0.0 : archetype.enrageSpeedMult(), rules.enrageSpeedMult());
        double enrageDmg = pickDouble(archetype == null ? 0.0 : archetype.enrageDamageMult(), rules.enrageDamageMult());
        double enrageDur = pickDouble(archetype == null ? 0.0 : archetype.enrageDurationSeconds(), rules.enrageDurationSeconds());
        String enrageSound = pickString(archetype == null ? null : archetype.enrageSoundId(), rules.enrageSoundId());

        if (enraged && enrageDmg > 0.0) {
            dmgMult *= enrageDmg;
        }
        return new OnHitConfig(slowId, Math.max(0.0, slowSec), dmgMult, dmgFlat,
                Math.max(1, cap), Math.max(0.0, window), Math.max(0.0, enrageAfter),
                enrageSpeed, enrageDmg, Math.max(0.0, enrageDur), enrageSound);
    }

    private static double pickDouble(double override, double baseline) {
        return override != 0.0 ? override : baseline;
    }

    @Nullable
    private static String pickString(@Nullable String override, @Nullable String baseline) {
        return (override != null && !override.isBlank()) ? override : baseline;
    }

    /** Base slow effect id (tier 1); the stacking consumer escalates to {@code <base>_N} suffixes. */
    @Nullable
    public String slowEffectId() {
        return slowEffectId;
    }

    public double slowSeconds() {
        return slowSeconds;
    }

    /** Effective outgoing-damage multiplier (already includes the enrage bonus when resolved enraged). */
    public double damageMult() {
        return damageMult;
    }

    public double damageFlat() {
        return damageFlat;
    }

    /** Highest proximity-stack tier the slow escalates to (>= 1). */
    public int stackCap() {
        return stackCap;
    }

    public double stackWindowSeconds() {
        return stackWindowSeconds;
    }

    /** Seconds without a landed hit before enrage triggers; {@code 0} = enrage disabled. */
    public double enrageAfterSeconds() {
        return enrageAfterSeconds;
    }

    public double enrageSpeedMult() {
        return enrageSpeedMult;
    }

    public double enrageDamageMult() {
        return enrageDamageMult;
    }

    public double enrageDurationSeconds() {
        return enrageDurationSeconds;
    }

    @Nullable
    public String enrageSoundId() {
        return enrageSoundId;
    }
}
