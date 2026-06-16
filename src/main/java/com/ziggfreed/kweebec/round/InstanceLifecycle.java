package com.ziggfreed.kweebec.round;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Path A engine-instance lifecycle (the verified seam). Each round is its own
 * pack-authored instance loaded from {@code Server/Instances/<name>/instance.bson}
 * (loaded as JSON), spawned via {@code InstancesPlugin.spawnInstance}, entered via
 * {@code teleportPlayerToLoadingInstance}, left via {@code exitInstance}, and
 * auto-removed by the engine RemovalSystem per the instance's authored
 * {@code RemovalConditions} + {@code DeleteOnRemove}. {@link #removeWorldOffThread}
 * is the belt-and-suspenders eviction path used by the cleanup ticker.
 *
 * <p>THREADING: {@code spawnInstance} is callable off-thread (consume the future
 * with {@code whenComplete}, never {@code .join()} on a world thread).
 * {@code teleportIn}/{@code exit} MUST run on the player's current world thread.
 * {@code removeWorld} MUST NOT run on the target world's own thread (it self-joins
 * and would deadlock) - hence the dedicated daemon executor here.
 */
public final class InstanceLifecycle {

    /** Off-world-thread executor for {@code Universe.removeWorld} (never call it on a world tick thread). */
    private static final ScheduledExecutorService REMOVER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kweebec-world-remover");
                t.setDaemon(true);
                return t;
            });

    private InstanceLifecycle() {
    }

    /**
     * Spawn a fresh, isolated instance world from the pack instance asset.
     *
     * @param instanceName asset name under {@code Server/Instances}
     * @param forWorld     the world a player returns to on exit
     * @param returnPoint  the return location
     */
    @Nonnull
    public static CompletableFuture<World> spawnInstance(@Nonnull String instanceName,
                                                         @Nonnull World forWorld,
                                                         @Nonnull Transform returnPoint) {
        InstancesPlugin plugin = InstancesPlugin.get();
        if (plugin == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("InstancesPlugin not available"));
        }
        return plugin.spawnInstance(instanceName, forWorld, returnPoint);
    }

    /**
     * Teleport a player into a still-loading instance (concurrent with the spawn).
     * Run on the player's CURRENT world thread. {@code overrideReturn} is stored as
     * that player's personal exit return point (pass each member their own captured
     * transform so non-initiators return to where THEY were, not the initiator's spot).
     */
    public static void teleportIn(@Nonnull Ref<EntityStore> entityRef,
                                  @Nonnull ComponentAccessor<EntityStore> accessor,
                                  @Nonnull CompletableFuture<World> worldFuture,
                                  @Nullable Transform overrideReturn) {
        InstancesPlugin.teleportPlayerToLoadingInstance(entityRef, accessor, worldFuture, overrideReturn);
    }

    /**
     * Send a player back out of the instance to their captured return point.
     * Run on the instance world thread.
     */
    public static void exit(@Nonnull Ref<EntityStore> entityRef,
                            @Nonnull ComponentAccessor<EntityStore> accessor) {
        InstancesPlugin.exitInstance(entityRef, accessor);
    }

    /**
     * Flag the instance for removal-when-empty (the sanctioned Path A escape hatch).
     * The actual world removal happens later via the engine RemovalSystem tick.
     */
    public static void safeRemove(@Nullable World instanceWorld) {
        try {
            InstancesPlugin.safeRemoveInstance(instanceWorld);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] safeRemoveInstance failed: " + t.getMessage());
        }
    }

    /**
     * Force-remove a world from a dedicated off-thread executor (never on the
     * world's own tick thread). Used by the cleanup ticker as a safety net when
     * the authored RemovalConditions have not fired.
     */
    public static void removeWorldOffThread(@Nonnull World world) {
        final String name = world.getName();
        REMOVER.execute(() -> {
            try {
                // No-op if the engine RemovalSystem already removed it (avoid a double-remove NPE).
                if (Universe.get().getWorld(name) != null) {
                    Universe.get().removeWorld(name);
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] removeWorld failed for " + name + ": " + t.getMessage());
            }
        });
    }

    public static void shutdown() {
        REMOVER.shutdownNow();
    }
}
