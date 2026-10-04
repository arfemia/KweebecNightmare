package com.ziggfreed.kweebec.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * A force-load settles however it ends, so the read waiting on it always runs: once the columns are in
 * memory, once a column comes back with no chunk, once the load fails, and once it outlasts its timeout. A
 * read that never ran would leave a hunter unspawned or the Warden never risen; one that runs over a column
 * still cold answers its own fallback height, as the read did before the load existed. The settle says which
 * it was, so a read that keeps its answer (the cave shaft's stored height) keeps only one made over loaded
 * ground. The load itself needs a world, so these hand the settle step futures of the test's own, any object
 * standing in for a column's chunk reference. A read chained after the load that throws must reach the log
 * naming its site even when nothing waits on the chain, or a den roster that throws leaves the round with no
 * hunter and nothing in the log.
 */
class ColumnLoadsTest {

    @Test
    void aLoadThatCompletesSettlesAtOnceAsLoaded() throws Exception {
        CompletableFuture<Boolean> settled = ColumnLoads.settle(CompletableFuture.completedFuture(true), 5L);

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

    @Test
    void aColumnThatComesBackWithNoChunkSettlesAsNotLoaded() throws Exception {
        // The chunk store completes a column's reference normally, with null, when no chunk came of the load
        // (its store shut down, or neither the loader nor the generator produced one).
        CompletableFuture<Boolean> settled = ColumnLoads.settle(ColumnLoads.allReferenced(List.of(
                CompletableFuture.completedFuture(new Object()),
                CompletableFuture.completedFuture(null),
                CompletableFuture.completedFuture(new Object()))), 5L);

        assertFalse(settled.get(1, TimeUnit.SECONDS),
                "a column that came back with no chunk is not in memory, so the load reports its ground not loaded");
    }

    @Test
    void aLoadWhoseEveryColumnComesBackWithAChunkSettlesAsLoaded() throws Exception {
        CompletableFuture<Boolean> settled = ColumnLoads.settle(ColumnLoads.allReferenced(List.of(
                CompletableFuture.completedFuture(new Object()),
                CompletableFuture.completedFuture(new Object()))), 5L);

        assertTrue(settled.get(1, TimeUnit.SECONDS),
                "a load whose every column came back with a chunk reports its ground in memory");
    }

    @Test
    void aThrowFromTheReadAfterALoadReachesTheLogNamingItsSite() {
        List<String> lines = new ArrayList<>();
        Executor worldThread = Runnable::run; // stands in for the world: runs the hop at once

        ColumnLoads.settle(CompletableFuture.completedFuture(true), 5L)
                .thenRunAsync(() -> {
                    throw new IllegalStateException("the roster threw");
                }, worldThread)
                .whenComplete(ColumnLoads.logFailure("hunter den", lines::add));

        assertEquals(1, lines.size(), "a throw from a read nothing waits on reaches the log once: " + lines);
        String line = lines.get(0);
        assertTrue(line.contains("hunter den"), "the line names the site: " + line);
        assertTrue(line.contains("IllegalStateException") && line.contains("the roster threw"),
                "the line names the read's own throw: " + line);
        assertFalse(line.contains("CompletionException"),
                "the line names the read's throw, not the wrapper the chain put round it: " + line);
    }

    @Test
    void aReadThatCompletesLogsNothing() {
        List<String> lines = new ArrayList<>();
        Executor worldThread = Runnable::run;

        ColumnLoads.settle(CompletableFuture.completedFuture(true), 5L)
                .thenRunAsync(() -> { }, worldThread)
                .whenComplete(ColumnLoads.logFailure("hunter den", lines::add));

        assertTrue(lines.isEmpty(), "a read that completes logs nothing: " + lines);
    }
}
