package com.ziggfreed.kweebec.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.math.util.ChunkUtil;

/**
 * Which chunk columns a force-load asks the chunk store for. The engine files a block under the chunk
 * {@code ChunkUtil.chunkCoordinate} names: a chunk is {@code ChunkUtil.SIZE} (32) blocks wide, and a negative
 * coordinate floors (blocks -1 and -32 are in chunk -1, block -33 in chunk -2). A span keyed any other way
 * loads ground beside the ground it means; since Ziggfreed Common 2.2.0 reads only ground that is already
 * loaded, the exit platform would then land at its fallback height, or not at all. Every expected column
 * here comes from {@code Math.floorDiv(block, ChunkUtil.SIZE)}, never from the code under test.
 */
class ChunkColumnsTest {

    /** Blocks on both sides of the chunk edges at 0, 32 and -32. */
    private static final int[] EDGE_BLOCKS = {0, 31, 32, -1, -32, -33};

    @Test
    void theExitForceLoadCoversTheEscapesOwnColumn() {
        ChunkColumns span = ChunkColumns.around(ArenaLayout.ESCAPE.x(), ArenaLayout.ESCAPE.z(), 1);

        int ownX = column((int) Math.floor(ArenaLayout.ESCAPE.x()));
        int ownZ = column((int) Math.floor(ArenaLayout.ESCAPE.z()));
        assertTrue(span.contains(ownX, ownZ), "the exit's force-load " + span
                + " misses the chunk column the escape stands in (" + ownX + ", " + ownZ + ")");
    }

    @Test
    void aRadiusSpansTheBlocksOwnColumnAndThatManyEitherSide() {
        for (int bx : EDGE_BLOCKS) {
            for (int bz : EDGE_BLOCKS) {
                for (int r = 0; r <= 1; r++) {
                    int cx = column(bx);
                    int cz = column(bz);
                    assertEquals(new ChunkColumns(cx - r, cx + r, cz - r, cz + r),
                            ChunkColumns.around(bx + 0.5, bz + 0.5, r),
                            "block (" + bx + ", " + bz + "), radius " + r);
                }
            }
        }
    }

    @Test
    void anAreaCoversEveryColumnItsBlocksLieIn() {
        double[][] centres = {{16.5, 40.25}, {-40.5, -71.25}, {-32.0, 31.0}};
        int[] radii = {0, 31, 40};
        for (double[] centre : centres) {
            int bx = (int) Math.floor(centre[0]);
            int bz = (int) Math.floor(centre[1]);
            for (int radius : radii) {
                ChunkColumns span = ChunkColumns.covering(centre[0], centre[1], radius);
                String where = "centre (" + centre[0] + ", " + centre[1] + "), radius " + radius;
                assertEquals(new ChunkColumns(column(bx - radius), column(bx + radius),
                        column(bz - radius), column(bz + radius)), span, where);
                for (int x = bx - radius; x <= bx + radius; x++) {
                    for (int z = bz - radius; z <= bz + radius; z++) {
                        if (!span.contains(column(x), column(z))) {
                            fail(where + ": block (" + x + ", " + z + ") lies outside " + span);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aSpanNamesEachOfItsColumnsOnce() {
        ChunkColumns span = new ChunkColumns(-2, 1, -3, -1);
        List<int[]> columns = span.columns();
        Set<String> seen = new HashSet<>();
        for (int[] c : columns) {
            assertTrue(span.contains(c[0], c[1]), "column (" + c[0] + ", " + c[1] + ") lies outside " + span);
            assertTrue(seen.add(c[0] + "," + c[1]), "column (" + c[0] + ", " + c[1] + ") is named twice");
        }
        assertEquals((span.maxX() - span.minX() + 1) * (span.maxZ() - span.minZ() + 1), columns.size());
        assertEquals(1, ChunkColumns.around(-0.5, 0.5, 0).columns().size(), "a radius of 0 is one column");
    }

    /** The column the engine files a block under, worked out apart from the code under test. */
    private static int column(int block) {
        return Math.floorDiv(block, ChunkUtil.SIZE);
    }
}
