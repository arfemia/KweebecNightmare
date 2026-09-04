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
