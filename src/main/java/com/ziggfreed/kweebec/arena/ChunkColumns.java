package com.ziggfreed.kweebec.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;

/**
 * A rectangle of chunk columns, every bound inclusive: the ground a force-load asks the chunk store for.
 * A Hytale chunk is {@code ChunkUtil.SIZE} (32) blocks wide, and the engine files a block under the chunk
 * {@code ChunkUtil.chunkCoordinate} names, an arithmetic shift, so a negative coordinate floors (blocks -1
 * and -32 are in chunk -1, block -33 in chunk -2). Every bound here comes from that same function, so a
 * span holds exactly the columns the engine keeps its blocks in. The arithmetic needs no world, so a unit
 * test reaches it; only {@link #forceLoad} touches one.
 *
 * @param minX the lowest chunk x in the span
 * @param maxX the highest chunk x in the span
 * @param minZ the lowest chunk z in the span
 * @param maxZ the highest chunk z in the span
 */
record ChunkColumns(int minX, int maxX, int minZ, int maxZ) {

    /**
     * The column that holds the block at world {@code (x, z)}, plus {@code chunkRadius} columns either side
     * of it on both axes: {@code (2r+1)^2} columns.
     */
    @Nonnull
    static ChunkColumns around(double x, double z, int chunkRadius) {
        int cx = ChunkUtil.chunkCoordinate((int) Math.floor(x));
        int cz = ChunkUtil.chunkCoordinate((int) Math.floor(z));
        return new ChunkColumns(cx - chunkRadius, cx + chunkRadius, cz - chunkRadius, cz + chunkRadius);
    }

    /**
     * Every column that holds a block within {@code blockRadius} blocks, on either axis, of the block at
     * world {@code (centreX, centreZ)}.
     */
    @Nonnull
    static ChunkColumns covering(double centreX, double centreZ, int blockRadius) {
        int bx = (int) Math.floor(centreX);
        int bz = (int) Math.floor(centreZ);
        return new ChunkColumns(
                ChunkUtil.chunkCoordinate(bx - blockRadius), ChunkUtil.chunkCoordinate(bx + blockRadius),
                ChunkUtil.chunkCoordinate(bz - blockRadius), ChunkUtil.chunkCoordinate(bz + blockRadius));
    }

    /** Whether the column {@code (chunkX, chunkZ)} lies in this span. */
    boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
    }

    /** Every column of the span as {@code {chunkX, chunkZ}}, each once, x-major. */
    @Nonnull
    List<int[]> columns() {
        List<int[]> out = new ArrayList<>();
        for (int chX = minX; chX <= maxX; chX++) {
            for (int chZ = minZ; chZ <= maxZ; chZ++) {
                out.add(new int[]{chX, chZ});
            }
        }
        return out;
    }

    /** Force-load (generate if missing) every column of the span, set ticking; settles when all are loaded. */
    @Nonnull
    CompletableFuture<Void> forceLoad(@Nonnull World world) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int[] column : columns()) {
            futures.add(world.getChunkStore().getChunkReferenceAsync(
                    ChunkUtil.indexChunk(column[0], column[1]), GetChunkFlags.SET_TICKING));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
}
