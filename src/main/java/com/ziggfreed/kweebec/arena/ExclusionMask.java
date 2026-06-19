package com.ziggfreed.kweebec.arena;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The ONE shared radial keep-clear mask the cycle-3 plan calls for (Track B "B3").
 * It is engine-free pure Java (mirrors {@link Anchor} / {@link ArenaLayout}'s
 * test-friendly stance) so it stays unit-reachable: it holds the GAMEPLAY discs a
 * decorative structure must never land on (spawn courtyard, the shrine ring band,
 * the gate -> escape corridor, the hunter den, the underground cave anchors) and
 * answers a single question, {@link #isClear(double, double, double)}: does an
 * (x,z) candidate plus its footprint radius clear ALL of them.
 *
 * <p>A "disc" is a circular keep-clear region {@code (cx, cz, radius)}; the
 * gate -> escape CORRIDOR is approximated as a string of overlapping discs along
 * the -z axis (cheap, no segment math, and a structure landing in the run lane is
 * exactly what we forbid). The shrine RING is a single big disc covering the whole
 * ring band (ring radius + the interact reach + a buffer) - structures live OUTSIDE
 * the ring in the mid grove, so one disc that swallows the inner ring is correct
 * and simpler than N per-shrine discs.
 *
 * <p>Built from a {@link #defaultMask()} factory off the static {@link ArenaLayout}
 * constants for this cycle; Track B will add a constructor/append path that consumes
 * the per-round SEEDED anchors (rotated shrine ring, picked cave anchors) so the mask
 * tracks the actual layout. Kept deliberately simple + documented for that handoff.
 */
public final class ExclusionMask {

    /** One circular keep-clear region in arena-local world XZ. */
    private record Disc(double cx, double cz, double radius) {
        /** True iff a point with the given footprint radius stays entirely OUTSIDE this disc. */
        boolean clears(double x, double z, double footprintRadius) {
            double dx = x - cx;
            double dz = z - cz;
            double keepOut = radius + footprintRadius;
            return (dx * dx + dz * dz) > (keepOut * keepOut);
        }
    }

    private final List<Disc> discs;

    private ExclusionMask(@Nonnull List<Disc> discs) {
        this.discs = discs;
    }

    /**
     * Build the mask from the fixed {@link ArenaLayout} gameplay anchors. Used to keep DECORATIVE
     * structures off the gameplay beats, so it includes the whole surface-shrine ring band.
     */
    @Nonnull
    public static ExclusionMask defaultMask() {
        return build(true);
    }

    /**
     * The keep-clear mask for placing SHRINES themselves (the runtime top-up of missing surface
     * shrines - see {@code ShrinePlacement}): identical to {@link #defaultMask()} but WITHOUT the
     * surface-shrine ring band disc. A shrine is SUPPOSED to live in that band, so excluding it would
     * reject every ring candidate; this variant still keeps top-up shrines off the spawn courtyard,
     * the gate -> escape corridor, the hunter den, and the cave-shaft entrances.
     */
    @Nonnull
    public static ExclusionMask shrinePlacementMask() {
        return build(false);
    }

    @Nonnull
    private static ExclusionMask build(boolean includeShrineRingBand) {
        List<Disc> out = new ArrayList<>();

        // Spawn courtyard: the r6 flat spawn clearing plus a comfortable walk-out buffer,
        // so a structure never boxes a freshly-spawned party in. The centerpiece (well) already
        // sits just south of spawn; structures keep clear of the whole courtyard.
        out.add(new Disc(ArenaLayout.SPAWN.x(), ArenaLayout.SPAWN.z(), SPAWN_COURTYARD_RADIUS));

        // The whole surface shrine ring band: ring radius + the channel reach + a buffer, as ONE
        // disc centered on spawn. Decorative structures live in the mid grove OUTSIDE this, so
        // swallowing the inner ring with a single disc is correct; shrine TOP-UP placement omits it
        // (the ring is exactly where a shrine belongs).
        if (includeShrineRingBand) {
            out.add(new Disc(ArenaLayout.SPAWN.x(), ArenaLayout.SPAWN.z(),
                    SHRINE_RING_RADIUS + ArenaLayout.INTERACT_RADIUS + SHRINE_RING_BUFFER));
        }

        // The gate -> escape corridor on the -z axis: a string of overlapping discs from the gate
        // (and a little south of spawn) out past the escape pad, so nothing blocks the climactic run.
        addCorridor(out, ArenaLayout.SPAWN.z() - CORRIDOR_NEAR_Z_INSET,
                ArenaLayout.ESCAPE.z() - CORRIDOR_FAR_Z_INSET, ArenaLayout.SPAWN.x(), CORRIDOR_HALF_WIDTH);

        // The hunter den (PREP containment / entry).
        out.add(new Disc(ArenaLayout.HUNTER_DEN.x(), ArenaLayout.HUNTER_DEN.z(), HUNTER_DEN_RADIUS));

        // Every underground cave anchor (each carves a descent shaft + chamber; a structure on top
        // would clobber the shaft entrance). Use the full predefined set so the mask is correct for
        // any caveShrineCount this round chose.
        for (Anchor cave : ArenaLayout.caveShrineAnchors(MAX_CAVE_ANCHORS)) {
            out.add(new Disc(cave.x(), cave.z(), CAVE_RADIUS));
        }

        return new ExclusionMask(out);
    }

    /**
     * True iff the candidate (x,z), padded by {@code footprintRadius}, clears EVERY gameplay disc.
     * A structure is safe to paste only when this returns true.
     */
    public boolean isClear(double x, double z, double footprintRadius) {
        for (Disc d : discs) {
            if (!d.clears(x, z, footprintRadius)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pick the FIRST candidate from {@code candidates} whose footprint {@link #isClear}; returns
     * {@code null} when none clear. Order-preserving, so the caller controls priority (e.g. a
     * seed-shuffled list yields a seed-stable first clear pick).
     *
     * @param candidates ordered placement candidates
     * @param footprintRadius the conservative footprint to keep clear
     */
    @Nullable
    public StructureCatalog.Placement firstClear(@Nonnull List<StructureCatalog.Placement> candidates,
                                                 double footprintRadius) {
        for (StructureCatalog.Placement p : candidates) {
            if (isClear(p.x(), p.z(), footprintRadius)) {
                return p;
            }
        }
        return null;
    }

    /** Append a string of overlapping discs along the -z corridor from {@code nearZ} to {@code farZ}. */
    private static void addCorridor(@Nonnull List<Disc> out, double nearZ, double farZ,
                                    double x, double halfWidth) {
        // Walk from the nearer Z to the farther (more-negative) Z in half-width steps so adjacent
        // discs overlap and the lane is continuously covered.
        double step = halfWidth; // overlap: each disc has radius=halfWidth, centers a half-width apart.
        for (double z = nearZ; z >= farZ; z -= step) {
            out.add(new Disc(x, z, halfWidth));
        }
        // Always cap the far end exactly at farZ so a non-multiple span still covers the escape pad.
        out.add(new Disc(x, farZ, halfWidth));
    }

    // --- tunable keep-clear radii (documented so Track B / a balance pass can adjust) ---

    /** Spawn flat-clearing (worldgen r6) plus a walk-out buffer. */
    private static final double SPAWN_COURTYARD_RADIUS = 12.0;
    /** Mirrors {@code ArenaLayout.SHRINE_RING_RADIUS} (private there); the surface ring radius. */
    private static final double SHRINE_RING_RADIUS = 26.0;
    /** Extra band beyond the ring + interact reach so structures sit clearly OUTSIDE the ring. */
    private static final double SHRINE_RING_BUFFER = 4.0;
    /** Half-width of the gate -> escape run lane kept clear (the corridor disc radius). */
    private static final double CORRIDOR_HALF_WIDTH = 9.0;
    /** Start the corridor a little south of spawn so the lane is covered from inside the courtyard out. */
    private static final double CORRIDOR_NEAR_Z_INSET = 6.0;
    /** Extend the corridor a little past the escape pad. */
    private static final double CORRIDOR_FAR_Z_INSET = 4.0;
    /** Keep-clear around the hunter den. */
    private static final double HUNTER_DEN_RADIUS = 10.0;
    /** Keep-clear around each cave shaft entrance. */
    private static final double CAVE_RADIUS = 8.0;
    /** The full predefined cave-anchor count (ArenaLayout caps its set at 4). */
    private static final int MAX_CAVE_ANCHORS = 4;
}
