package com.ziggfreed.kweebec.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic behind a wave request: how the party scales it, how the live ceiling binds it, and
 * how the authored ranges are kept sane. Every number here is the test's own, never a shipped rung's.
 */
class HunterWaveTest {

    private static HunterWave wave(int countMin, int countMax, boolean perPlayer, double radiusMin, double radiusMax) {
        return new HunterWave(null, countMin, countMax, perPlayer, radiusMin, radiusMax, false, false, false);
    }

    @Test
    void aPerPlayerWaveScalesWithTheParty() {
        HunterWave wave = wave(2, 2, true, 10.0, 10.0);

        assertEquals(6, wave.requested(3, 1L));
        assertEquals(2, wave.requested(0, 1L), "a party never counts below one");
    }

    @Test
    void aFlatWaveIgnoresTheParty() {
        HunterWave wave = wave(2, 2, false, 10.0, 10.0);

        assertEquals(2, wave.requested(4, 1L));
    }

    @Test
    void aCountRangeDrawsInsideItsBoundsAndRepeatsPerSeed() {
        HunterWave wave = wave(1, 4, false, 10.0, 10.0);

        for (long seed = 0; seed < 64; seed++) {
            int drawn = wave.requested(1, seed);
            assertTrue(drawn >= 1 && drawn <= 4, "seed " + seed + " drew " + drawn);
            assertEquals(drawn, wave.requested(1, seed), "the same seed draws the same count");
        }
    }

    @Test
    void theRoomUnderTheCeilingBindsTheWave() {
        assertEquals(3, HunterWave.room(5, 8, 10), "only the room left under the ceiling");
        assertEquals(3, HunterWave.room(2, 8, 3), "a wave that fits is untouched");
        assertEquals(0, HunterWave.room(8, 8, 2), "a full roster admits nobody");
        assertEquals(0, HunterWave.room(9, 8, 2), "an over-full roster never goes negative");
        assertEquals(0, HunterWave.room(0, 8, 0), "nothing asked, nothing allowed");
    }

    @Test
    void theBandNeverReachesTheAnchorAndNeverInverts() {
        HunterWave wave = wave(1, 1, true, 0.5, 0.1);

        assertEquals(HunterWave.MIN_RADIUS, wave.radiusMin());
        assertEquals(HunterWave.MIN_RADIUS, wave.radiusMax());
        assertEquals(HunterWave.MIN_RADIUS, wave.radius(3L));
    }

    @Test
    void aRadiusDrawStaysInsideTheBandAndRepeatsPerSeed() {
        HunterWave wave = wave(1, 1, true, 6.0, 12.0);

        for (long seed = 0; seed < 64; seed++) {
            double r = wave.radius(seed);
            assertTrue(r >= 6.0 && r <= 12.0, "seed " + seed + " drew " + r);
            assertEquals(r, wave.radius(seed), "the same seed draws the same radius");
        }
    }

    /**
     * A wave force-loads the ground within its reach of the anchor before it reads it, so every point the
     * band can land a hunter on, a ring or a scatter, at any angle and any anchor, must lie within that
     * many blocks of the anchor's own block on both axes. A point outside reads a column the load never
     * asked for.
     */
    @Test
    void theReachHoldsEveryPointTheBandCanLandOn() {
        double[] anchors = {0.0, 0.5, 0.99, -0.5, -31.9, 15.25};
        double[][] bands = {{2.0, 2.0}, {5.5, 9.3}, {7.0, 13.6}, {16.0, 16.0}};
        for (double[] band : bands) {
            HunterWave wave = wave(1, 1, false, band[0], band[1]);
            int reach = wave.reach();
            double[] radii = {wave.radiusMin(), (wave.radiusMin() + wave.radiusMax()) / 2.0, wave.radiusMax()};
            for (double ax : anchors) {
                for (double az : anchors) {
                    for (int degrees = 0; degrees < 360; degrees++) {
                        double angle = Math.toRadians(degrees);
                        for (double r : radii) {
                            int dx = (int) Math.floor(ax + Math.cos(angle) * r) - (int) Math.floor(ax);
                            int dz = (int) Math.floor(az + Math.sin(angle) * r) - (int) Math.floor(az);
                            assertTrue(Math.abs(dx) <= reach && Math.abs(dz) <= reach, "band [" + band[0] + ", "
                                    + band[1] + "], anchor (" + ax + ", " + az + "), " + degrees + " degrees at "
                                    + r + ": the point lands " + dx + ", " + dz + " blocks out, past the reach "
                                    + reach);
                        }
                    }
                }
            }
        }
    }

    @Test
    void anInvertedCountRangeIsRaisedToItsFloor() {
        HunterWave wave = wave(3, 1, true, 10.0, 10.0);

        assertEquals(3, wave.countMin());
        assertEquals(3, wave.countMax());
        assertEquals(3, wave.requested(1, 9L));
    }

    @Test
    void aBlankArchetypeIsTheRoundsOwnPick() {
        assertNull(new HunterWave("  ", 1, 1, true, 10.0, 10.0, false, false, false).archetype());
        assertEquals("Lunger", new HunterWave(" Lunger ", 1, 1, true, 10.0, 10.0, false, false, false).archetype(),
                "an authored id is kept, trimmed");
    }
}
