package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a thrown Moonbloom delivers its stun - a per-preset dial so a round (or an
 * installed MMO) can pick the robust asset-driven projectile OR the code-only cone
 * fallback.
 *
 * <ul>
 *   <li>{@link #PROJECTILE} - the asset-driven thrown projectile bursts on impact; the
 *       stun is applied in Java when the burst's attribution damage lands on a hunter.
 *       The default.</li>
 *   <li>{@link #CONE} - no projectile entity; a left-click stuns the nearest hunter in a
 *       short cone in front of the thrower and consumes one charge. The guaranteed
 *       fallback if the projectile feel needs an in-game spike.</li>
 * </ul>
 */
public enum ThrowMode {

    PROJECTILE,
    CONE;

    public static final ThrowMode DEFAULT = PROJECTILE;

    @Nonnull
    public static ThrowMode fromString(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        for (ThrowMode m : values()) {
            if (m.name().equalsIgnoreCase(s.trim())) {
                return m;
            }
        }
        return DEFAULT;
    }
}
