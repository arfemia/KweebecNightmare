package com.ziggfreed.kweebec.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * A force-load settles however it ends, so the read waiting on it always runs: once the columns are in
 * memory, once the load fails, and once it outlasts its timeout. A read that never ran would leave a hunter
 * unspawned or the Warden never risen; one that runs over a column still cold answers its own fallback
 * height, as the read did before the load existed. The settle says which it was, so a read that keeps its
 * answer (the cave shaft's stored height) keeps only one made over loaded ground. The load itself needs a
 * world, so these hand the settle step a future of the test's own.
 */
class ColumnLoadsTest {

    @Test
    void aLoadThatCompletesSettlesAtOnceAsLoaded() throws Exception {
        CompletableFuture<Boolean> settled = ColumnLoads.settle(CompletableFuture.completedFuture(null), 5L);

        assertTrue(settled.isDone(), "a load already complete settles at once");
        assertTrue(settled.get(), "a load that completed reports its ground in memory");
    }

    @Test
    void aLoadThatFailsStillSettlesAsNotLoaded() throws Exception {
        CompletableFuture<Boolean> settled = ColumnLoads.settle(
                CompletableFuture.failedFuture(new IllegalStateException("the chunk store refused")), 5L);

        assertFalse(settled.get(1, TimeUnit.SECONDS),
                "a failed load settles normally, so the read still runs, and reports its ground not loaded");
    }

    @Test
    void aLoadThatHangsSettlesAtItsTimeoutAsNotLoaded() throws Exception {
        CompletableFuture<Boolean> settled = ColumnLoads.settle(new CompletableFuture<>(), 1L);

        assertFalse(settled.get(10, TimeUnit.SECONDS),
                "a hung load settles normally once its timeout passes, and reports its ground not loaded");
    }
}
