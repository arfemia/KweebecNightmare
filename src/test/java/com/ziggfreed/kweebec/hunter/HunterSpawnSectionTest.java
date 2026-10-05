package com.ziggfreed.kweebec.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * A hunter goes down only where it will stay: on Update 7 one added into a chunk section that is not
 * ticking is parked on the spot, untracked, and turns up later outside the round's control, and nothing
 * guarantees a survivor's hot sphere covers the den (or a wave hunter's spot) when it goes down. The live
 * answer is ziggfreed-common's {@code TickingSections.ensureTicking}; this drives the guard around the
 * spawn.
 */
class HunterSpawnSectionTest {

    @Test
    void aHunterIsSpawnedIntoATickingSection() {
        int[] asleep = {0};

        String spawned = AiHunterController.spawnWhenTicking(() -> true, () -> "hunter", () -> asleep[0]++);

        assertEquals("hunter", spawned);
        assertEquals(0, asleep[0]);
    }

    @Test
    void aSectionThatCannotTickSpawnsNothingAndSaysSo() {
        int[] spawns = {0};
        int[] asleep = {0};

        String spawned = AiHunterController.spawnWhenTicking(() -> false, () -> {
            spawns[0]++;
            return "hunter";
        }, () -> asleep[0]++);

        assertNull(spawned);
        assertEquals(0, spawns[0], "never parked into a sleeping section");
        assertEquals(1, asleep[0]);
    }
}
