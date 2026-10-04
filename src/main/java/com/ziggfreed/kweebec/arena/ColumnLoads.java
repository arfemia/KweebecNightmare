package com.ziggfreed.kweebec.arena;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.world.World;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The one way this mod reads ground no player need be near: force-load the chunk columns the read touches,
 * then act once the load settles. Ziggfreed Common 2.2.0's {@code SurfaceProbe} and {@code SpawnPlacement}
 * read only chunk sections already in memory, so over a column that is not they answer the caller's fallback
 * height (2.1.0 loaded the chunk on the spot); a paste at a fixed anchor, a spawn at the den or the gate, and
 * a wave around the party's centre therefore load first, and hold on both.
 *
 * <p>The future answered here never fails. A load that completes, fails, outlasts its timeout or cannot even
 * start (logged) settles it all the same, answering {@code true} only when every column came back with a
 * chunk, and the read after it answers its own fallback over anything still cold, exactly as it did before the
 * load existed. A load can complete with a column still cold: the chunk store answers a column it could not
 * bring into memory (its store shut down, or neither its loader nor its generator produced a chunk) with a
 * null reference rather than a failure, and that settles {@code false} as a failure does.
 * Chain the read on the world thread, where the probes must run, and end a chain whose failure nothing else
 * logs with {@link #logFailure(String)}:
 * {@code settled(...).thenRunAsync(() -> { probe; act }, world).whenComplete(logFailure(what))}.
 */
public final class ColumnLoads {

    private ColumnLoads() {
    }

    /**
     * Force-load (generating if missing) every chunk column that holds a block within {@code blockRadius}
     * blocks, on either axis, of world {@code (x, z)}, set ticking; settles {@code true} once every one came
     * back with a chunk, or {@code false} once one came back with none, the load failed, or it outlasted
     * {@code timeoutSeconds}. A radius of 0 is the column of {@code (x, z)} alone. {@code what} names the read
     * in the line a failed kickoff logs.
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
            return settle(allReferenced(columns.forceLoad(world)), timeoutSeconds);
        } catch (Throwable t) {
            // Could not even start the load: act on whatever is already in memory.
            SafeLog.warn("[Kweebec] " + what + " force-load kickoff failed: " + t.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Whether every column came back with a chunk, once each of {@code references} (one column's chunk
     * reference each) has completed: {@code true} when every one holds a reference, {@code false} when one
     * completed with null, the chunk store's answer for a column no chunk came of. Fails when one of them
     * failed. Package-private for the test.
     */
    @Nonnull
    static CompletableFuture<Boolean> allReferenced(@Nonnull List<? extends CompletableFuture<?>> references) {
        return CompletableFuture.allOf(references.toArray(new CompletableFuture<?>[0]))
                .thenApply(allCompleted -> references.stream().allMatch(reference -> reference.join() != null));
    }

    /**
     * {@code load}'s answer, settled normally however it ends or once {@code timeoutSeconds} pass:
     * {@code true} when it answered {@code true} (every column came back with a chunk), {@code false} when it
     * answered {@code false} or nothing, failed or timed out. Package-private for the test.
     */
    @Nonnull
    static CompletableFuture<Boolean> settle(@Nonnull CompletableFuture<Boolean> load, long timeoutSeconds) {
        return load.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .handle((loaded, error) -> error == null && Boolean.TRUE.equals(loaded));
    }

    /**
     * The last stage of a chain started on {@link #settled}: logs a throw from the read chained after the load
     * as a WARN naming {@code what}. A throw inside a chained stage only fails the future that stage returns,
     * so a chain nobody observes, or one whose observer logs only some failures, drops it without a line.
     * Chained with {@code whenComplete}, it leaves the chain's answer or failure as it was.
     */
    @Nonnull
    public static BiConsumer<Object, Throwable> logFailure(@Nonnull String what) {
        return logFailure(what, line -> KweebecNightmarePlugin.LOGGER.atWarning().log(line));
    }

    /**
     * {@link #logFailure(String)} into {@code log}, naming the read's own throw rather than the
     * {@link CompletionException} the chain wraps it in. Package-private for the test.
     */
    @Nonnull
    static BiConsumer<Object, Throwable> logFailure(@Nonnull String what, @Nonnull Consumer<String> log) {
        return (answer, error) -> {
            if (error != null) {
                Throwable thrown = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                log.accept("[Kweebec] " + what + " read after its force-load failed: " + thrown);
            }
        };
    }
}
