package com.ziggfreed.kweebec.hunter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ziggfreed.kweebec.arena.ArenaLayout;

/**
 * The den roster stands side by side along x, centred on the den anchor, so a roster of more than one
 * reaches ground the anchor's own block does not, and the den anchor sits half a block from a chunk edge:
 * the first hunter west of it already stands in the next column over. The den force-loads the ground
 * within its reach of the anchor before the surface probe reads it, so every hunter's block must lie within
 * that many blocks of the anchor's own.
 */
class AiHunterControllerDenTest {

    @Test
    void theDenReachHoldsEveryRosterHunter() {
        double[] anchors = {ArenaLayout.HUNTER_DEN.x(), -0.5, 31.75, -32.0};
        for (double ax : anchors) {
            for (int total = 1; total <= 12; total++) {
                int reach = AiHunterController.denReach(total);
                for (int index = 0; index < total; index++) {
                    double hx = ax + AiHunterController.denOffset(index, total);
                    int dx = (int) Math.floor(hx) - (int) Math.floor(ax);
                    assertTrue(Math.abs(dx) <= reach, "anchor x " + ax + ", hunter " + index + " of " + total
                            + " stands at x " + hx + ", " + dx + " blocks out, past the den's reach " + reach);
                }
            }
        }
    }
}
