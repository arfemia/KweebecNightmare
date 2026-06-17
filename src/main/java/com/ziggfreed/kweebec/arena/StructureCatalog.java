package com.ziggfreed.kweebec.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.asset.StructureConfig;
import com.ziggfreed.kweebec.asset.StructurePlacementAsset;

/**
 * The seeded selection for the GAMEPLAY-anchored corrupted structures pasted by
 * {@link ArenaBuilder} (cycle-3 Track A "A3"). The candidate table is now
 * ASSET-DRIVEN: it reads {@link StructureConfig#all()} (the {@code defaults < pack}
 * fold), so a pack adds, removes, or relocates the per-round ruin candidates purely as
 * DATA (drop a file under {@code Server/KweebecNightmare/Structures/*.json}), never a
 * code edit. This class keeps ONLY the pure, engine-free selection logic (mirrors
 * {@link ArenaLayout}'s test-friendly stance).
 *
 * <p>Each {@link Placement} is a corrupted-structure prefab key, a {@link Role} label
 * (the design intent of the ruin: cover / chokepoint / beacon / landmark), and a
 * candidate (x,z) in the MID grove (roughly r35..r60 in varied directions) - clear of
 * the inner gameplay disc so the {@link ExclusionMask} virtually always accepts it, but
 * masked anyway in case a future layout change crowds one. The jar baseline (the same
 * 10 placements this class used to hardcode) lives in {@code DefaultStructures}.
 *
 * <p>{@link #select(long, ExclusionMask)} deterministically shuffles the effective
 * placements by a seed, keeps only those whose footprint {@link ExclusionMask#isClear},
 * and returns the first {@link #MAX_SELECTED} (down to {@link #MIN_SELECTED}) - so the
 * CHOSEN set and the order vary per round while staying reproducible for the same seed.
 * The yaw a structure faces is left to {@link ArenaBuilder} (it varies facing off the
 * same seed).
 */
public final class StructureCatalog {

    /** The narrative/gameplay intent of a placed ruin (drives nothing here yet; for logging + future weighting). */
    public enum Role {
        /** A wall/house a survivor can break line-of-sight behind. */
        COVER,
        /** A bridge/cluster that narrows movement (the hunter funnels through). */
        CHOKEPOINT,
        /** A lamppost ruin: a lit landmark that draws the eye (relight-beacon flavor). */
        BEACON,
        /** A big, far structure that orients the player (the grandfather elder / tower). */
        LANDMARK,
    }

    /**
     * One candidate corrupted-structure placement.
     *
     * @param prefabKey the prefab resolution key (e.g. {@code "KweebecNightmare/Corrupted_Shop"})
     * @param role      design intent label
     * @param x         candidate world X in the mid grove
     * @param z         candidate world Z in the mid grove
     */
    public record Placement(@Nonnull String prefabKey, @Nonnull Role role, double x, double z) {
    }

    /** Conservative keep-clear footprint for a corrupted structure (covers the biggest committed set). */
    public static final double FOOTPRINT_RADIUS = 6.0;

    /** The minimum number of structures a round places (when enough candidates clear the mask). */
    private static final int MIN_SELECTED = 3;
    /** The maximum number of structures a round places. */
    private static final int MAX_SELECTED = 5;

    private StructureCatalog() {
    }

    /**
     * Deterministically select a per-round subset of placements: read the effective
     * (asset-driven) candidate table from {@link StructureConfig#all()}, shuffle it by {@code seed},
     * drop any whose {@link #FOOTPRINT_RADIUS} footprint is NOT {@link ExclusionMask#isClear}, and
     * keep the first {@link #MAX_SELECTED}. The same seed + mask (and the same loaded packs) always
     * yields the same set + order; different seeds shuffle to a different chosen subset.
     *
     * <p>Returns at most {@link #MAX_SELECTED} and as few as 0 (if the mask rejects everything, or a
     * replace-pack authored no clear placements - the caller treats the list best-effort). When fewer
     * than {@link #MIN_SELECTED} clear, every clear candidate is returned (we never pad past what is
     * actually safe).
     *
     * @param seed per-round seed (e.g. the world seed; today {@code roundId.hashCode()})
     * @param mask the shared gameplay keep-clear mask
     * @return the chosen placements, in the seed's shuffled order
     */
    @Nonnull
    public static List<Placement> select(long seed, @Nonnull ExclusionMask mask) {
        Random rng = new Random(seed);
        List<Placement> shuffled = new ArrayList<>();
        for (StructurePlacementAsset a : StructureConfig.getInstance().all()) {
            String prefabKey = a.prefabKey();
            if (prefabKey == null || prefabKey.isBlank()) {
                // A placement with no prefab key can never paste; skip it best-effort.
                continue;
            }
            shuffled.add(new Placement(prefabKey, a.role(), a.x(), a.z()));
        }
        Collections.shuffle(shuffled, rng);

        List<Placement> chosen = new ArrayList<>(MAX_SELECTED);
        for (Placement p : shuffled) {
            if (chosen.size() >= MAX_SELECTED) {
                break;
            }
            if (mask.isClear(p.x(), p.z(), FOOTPRINT_RADIUS)) {
                chosen.add(p);
            }
        }
        // chosen may be < MIN_SELECTED only if the mask rejected an unusual number of candidates;
        // we return what is genuinely clear rather than forcing an unsafe placement. MIN_SELECTED is
        // documented as the design target, enforced softly by having far more candidates than the cap.
        return chosen;
    }
}
