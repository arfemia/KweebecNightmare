package com.ziggfreed.kweebec.arena;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.world.World;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The one way this mod reads ground no player need be near: force-load the chunk columns the read touches,
 * then act once the load settles. Ziggfreed Common 2.2.0's {@code SurfaceProbe} and {@code SpawnPlacement}
 * read only chunk sections already in memory, so over a column that is not they answer the caller's fallback
 * height (2.1.0 loaded the chunk on the spot); a paste at a fixed anchor, a spawn at the den or the gate, and
 * a wave around the party's centre therefore load first, and hold on both.
 *
 * <p>The future answered here never fails. A load that completes, fails, outlasts its timeout or cannot even
 * start (logged) settles it all the same, answering {@code true} only when every column is in memory, and the
 * read after it answers its own fallback over anything still cold, exactly as it did before the load existed.
 * Chain the read on the world thread, where the probes must run:
 * {@code settled(...).thenRunAsync(() -> { probe; act }, world)}.
 */
public final class ColumnLoads {

    private ColumnLoads() {
    }

    /**
     * Force-load (generating if missing) every chunk column that holds a block within {@code blockRadius}
     * blocks, on either axis, of world {@code (x, z)}, set ticking; settles once all are in memory
     * ({@code true}) or the load fails or outlasts {@code timeoutSeconds} ({@code false}). A radius of 0 is
     * the column of {@code (x, z)} alone. {@code what} names the read in the line a failed kickoff logs.
     */
    @Nonnull
    public static CompletableFuture<Boolean> settled(@Nonnull World world, @Nonnull String what,
                                                     double x, double z, int blockRadius, long timeoutSeconds) {
        return settled(world, what, ChunkColumns.covering(x, z, blockRadius), timeoutSeconds);
    }

    /** {@link #settled(World, String, double, double, int, long)} over a span of columns the caller names. */
    @Nonnull
    static CompletableFuture<Boolean> settled(@Nonnull World world, @Nonnull String what,
                                              @Nonnull ChunkColumns columns, long timeoutSeconds) {
        try {
            return settle(columns.forceLoad(world), timeoutSeconds);
        } catch (Throwable t) {
            // Could not even start the load: act on whatever is already in memory.
            SafeLog.warn("[Kweebec] " + what + " force-load kickoff failed: " + t.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * {@code load}, settled normally however it ends or once {@code timeoutSeconds} pass: {@code true} when it
     * completed, {@code false} when it failed or timed out. Package-private for the test.
     */
    @Nonnull
    static CompletableFuture<Boolean> settle(@Nonnull CompletableFuture<?> load, long timeoutSeconds) {
        return load.orTimeout(timeoutSeconds, TimeUnit.SECONDS).handle((loaded, error) -> error == null);
    }
}
