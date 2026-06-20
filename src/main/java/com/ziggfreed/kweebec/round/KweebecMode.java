package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The two Kweebec Nightmare gameplay modes. Each is its own pack-authored
 * instance world. Only {@link #CHASE} is built in the MVP; {@link #SURVIVAL}
 * is reserved (the top-down camera mode lands after the chase proves the core).
 */
public enum KweebecMode {

    /** "Relight & Escape" - relight the grove shrines, then escape the Heartwood Gate. */
    CHASE("chase", "KweebecNightmare_Chase"),

    /** Team PvP brawl (1v1 / 2v2) in a grove arena; win condition + respawn are configurable. */
    CLASH("clash", "KweebecNightmare_Clash"),

    /** Team PvP control-point (King-of-the-Hill / N-point Domination) reusing the Clash scaffold. */
    DOMINATION("domination", "KweebecNightmare_Domination"),

    /** "Last Light Till Dawn" - defend a heart-sapling against waves until dawn (reserved). */
    SURVIVAL("survival", "KweebecNightmare_Survival");

    private final String id;
    private final String instanceName;

    KweebecMode(@Nonnull String id, @Nonnull String instanceName) {
        this.id = id;
        this.instanceName = instanceName;
    }

    /** Stable lowercase id used in native events + lang keys ({@code "chase"} / {@code "survival"}). */
    @Nonnull
    public String id() {
        return id;
    }

    /** The pack instance asset name under {@code Server/Instances/<name>/instance.bson}. */
    @Nonnull
    public String instanceName() {
        return instanceName;
    }

    /** Localization key for the human-readable mode name. */
    @Nonnull
    public String nameKey() {
        return "kweebecnightmare.mode." + id + ".name";
    }

    @Nullable
    public static KweebecMode fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (KweebecMode m : values()) {
            if (m.id.equalsIgnoreCase(id)) {
                return m;
            }
        }
        return null;
    }
}
