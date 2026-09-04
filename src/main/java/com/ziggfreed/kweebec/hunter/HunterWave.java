package com.ziggfreed.kweebec.hunter;

import java.util.Random;

import javax.annotation.Nullable;

/**
 * One wave of hunters as the hunter encounter script asks for it (the {@code KweebecHunterWave}
 * action's knobs, read once when the script is built): which archetype rises, or blank for the
 * round's own weighted pick; how many, as a range, per survivor or flat; how far from the anchor
 * they land, as a band; whether the points are spaced evenly around the anchor or scattered; whether
 * the anchor is one survivor picked at random or the party's centre; and whether the party is told.
 * Pure data with its arithmetic beside it; {@link AiHunterController#spawnWave} turns it into bodies
 * on the ground.
 *
 * @param archetype       a hunter archetype id to raise, or null for the round's own pick
 * @param countMin        the fewest hunters the wave asks for
 * @param countMax        the most
 * @param perPlayer       whether the count is per party member
 * @param radiusMin       the nearest a hunter lands to the anchor, in blocks
 * @param radiusMax       the farthest
 * @param even            spaced evenly around the anchor (a ring) rather than scattered
 * @param aroundOnePlayer anchored on one survivor picked at random rather than the party's centre
 * @param announce        whether the survivors are told when a hunter actually appears
 */
public record HunterWave(@Nullable String archetype, int countMin, int countMax, boolean perPlayer,
                         double radiusMin, double radiusMax, boolean even, boolean aroundOnePlayer,
                         boolean announce) {

    /** The nearest a wave hunter may land to its anchor, so one never rises on top of a survivor. */
    public static final double MIN_RADIUS = 2.0;

    public HunterWave {
        archetype = archetype == null || archetype.isBlank() ? null : archetype.trim();
        countMin = Math.max(0, countMin);
        countMax = Math.max(countMin, countMax);
        radiusMin = Math.max(MIN_RADIUS, radiusMin);
        radiusMax = Math.max(radiusMin, radiusMax);
    }

    /**
     * How many hunters this wave asks for: a seeded draw inside {@code [countMin, countMax]}, times
     * the party size when {@link #perPlayer} (a party never counts below one). The room under the
     * round's live ceiling, {@link #room}, is applied after.
     */
    public int requested(int partySize, long seed) {
        int base = countMin == countMax
                ? countMin
                : countMin + new Random(seed).nextInt(countMax - countMin + 1);
        return perPlayer ? base * Math.max(1, partySize) : base;
    }

    /** One radius drawn from the band, for a ring that keeps a single distance all the way round. */
    public double radius(long seed) {
        return radiusMin == radiusMax
                ? radiusMin
                : radiusMin + new Random(seed).nextDouble() * (radiusMax - radiusMin);
    }

    /**
     * How many of {@code requested} fit under {@code ceiling} with {@code live} hunters already out.
     * Never negative; a full roster, or nothing asked for, is zero.
     */
    public static int room(int live, int ceiling, int requested) {
        int room = ceiling - live;
        return room <= 0 || requested <= 0 ? 0 : Math.min(requested, room);
    }
}
