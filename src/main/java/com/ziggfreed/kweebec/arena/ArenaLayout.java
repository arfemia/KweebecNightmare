package com.ziggfreed.kweebec.arena;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * The fixed coordinate layout of the Chase grove. Gameplay (shrine relight, gate
 * alert, escape) is driven entirely off these anchors, so the round is fully
 * playable even before any decorative prefab is pasted - the visual geometry is
 * a polish layer over this skeleton, never a dependency of the logic.
 *
 * <p>All anchors are arena-local world coordinates. The instance spawn provider
 * in {@code Server/Instances/KweebecNightmare_Chase/instance.bson} must agree
 * with {@link #SPAWN}. The floor is generated flat by the pack worldgen at
 * {@link #FLOOR_Y}.
 */
public final class ArenaLayout {

    /** Flat floor surface Y produced by the pack HytaleGenerator worldgen. */
    public static final double FLOOR_Y = 64.0;

    /** Standing Y (one block above the floor). */
    public static final double STAND_Y = FLOOR_Y + 1.0;

    /** Where players (and the lobby return fallback) spawn in. Matches instance.bson. */
    public static final Anchor SPAWN = new Anchor(0.5, STAND_Y, 0.5, 0f);

    /** The Heartwood Gate that opens when the final shrine is lit. */
    public static final Anchor GATE = new Anchor(0.5, STAND_Y, -44.0, (float) Math.PI);

    /** The exit just past the gate - reaching it (in ESCAPE) wins the round. */
    public static final Anchor ESCAPE = new Anchor(0.5, STAND_Y, -56.0, (float) Math.PI);

    /** The hunter's containment / entry point during PREP, away from the party. */
    public static final Anchor HUNTER_DEN = new Anchor(0.5, STAND_Y, 40.0, (float) Math.PI);

    /** Radius of the shrine ring around spawn. */
    private static final double SHRINE_RING_RADIUS = 26.0;

    /** Proximity radius (blocks) a player must be within to channel a shrine or reach the exit. */
    public static final double INTERACT_RADIUS = 3.0;
    public static final double INTERACT_RADIUS_SQ = INTERACT_RADIUS * INTERACT_RADIUS;

    /** Reaching within this of {@link #ESCAPE} counts as an escape. */
    public static final double ESCAPE_RADIUS = 4.0;
    public static final double ESCAPE_RADIUS_SQ = ESCAPE_RADIUS * ESCAPE_RADIUS;

    private ArenaLayout() {
    }

    /**
     * Evenly-spaced shrine anchors on a ring, deterministic for a given count so
     * every client agrees on the same layout. The ring biases the first shrine
     * toward the gate side so the party drifts toward the exit as they relight.
     *
     * @param count number of shrines ({@code RuleSet.shrineCount(partySize)})
     */
    @Nonnull
    public static List<Anchor> shrineAnchors(int count) {
        List<Anchor> out = new ArrayList<>(Math.max(0, count));
        if (count <= 0) {
            return out;
        }
        for (int i = 0; i < count; i++) {
            double theta = (2.0 * Math.PI * i) / count;
            double x = SPAWN.x() + SHRINE_RING_RADIUS * Math.sin(theta);
            double z = SPAWN.z() - SHRINE_RING_RADIUS * Math.cos(theta);
            // Face the shrine roughly back toward spawn.
            float yaw = (float) Math.atan2(SPAWN.x() - x, SPAWN.z() - z);
            out.add(new Anchor(x, STAND_Y, z, yaw));
        }
        return out;
    }
}
