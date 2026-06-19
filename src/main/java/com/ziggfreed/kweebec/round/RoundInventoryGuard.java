package com.ziggfreed.kweebec.round;

import java.nio.file.Path;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.inventory.InventorySnapshotStore;
import com.ziggfreed.common.inventory.InventoryStripPolicy;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Kweebec's consumer wiring over ziggfreed-common's {@link InventorySnapshotStore}: it owns the
 * persisted snapshot store and the round's preserve/restore policy, and exposes the lifecycle hooks
 * {@link RoundService} calls. Kweebec STRIPS THE WHOLE inventory on entry ({@link
 * InventoryStripPolicy#STRIP_ALL}) - survivors enter the nightmare with nothing and get their exact
 * overworld gear back on exit; any loot gained in-round is dropped (round rewards come through the
 * separate reward model, not the inventory). The common primitive supports partial keeps (e.g.
 * armor) and item whitelists/blacklists for a future variant, but kweebec does not use them.
 *
 * <p>Crash-safe: the snapshot is persisted to disk BEFORE the live inventory is touched, and a
 * pending snapshot is restored on the player's next {@link PlayerReadyEvent} - so a server crash,
 * disconnect, or restart mid-round never eats gear. All inventory work runs on the player's world thread.
 */
public final class RoundInventoryGuard {

    private static final InventorySnapshotStore STORE = new InventorySnapshotStore("inventory-snapshots");

    private RoundInventoryGuard() {
    }

    /** Resolve the persisted snapshot file under the plugin data dir + reload any leftovers. Call once at setup. */
    public static void init(@Nullable Path dataDir) {
        STORE.init(dataDir);
    }

    /**
     * On round entry: unless the mode is {@link InventoryMode#KEEP}, snapshot + persist + strip the
     * player's inventory. Run on the player's CURRENT world thread (store/ref valid), BEFORE the
     * teleport into the instance, so they arrive empty and a crash leaves the snapshot on disk.
     */
    public static void onEnter(@Nonnull UUID uuid, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull InventoryMode mode) {
        if (mode == InventoryMode.KEEP) {
            return; // bring your gear in and keep it: no snapshot, no strip, no restore
        }
        try {
            STORE.captureAndStrip(store, ref, uuid, InventoryStripPolicy.STRIP_ALL);
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] inventory capture/strip failed for " + uuid + ": " + t.getMessage());
        }
    }

    /**
     * Restore a held snapshot onto an already-resolved overworld ref (the normal-exit path, called
     * from {@code RoundService.scheduleOverworldResync} once the player is back in the overworld).
     * No-op if nothing is held. World thread only.
     */
    public static void restore(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull UUID uuid) {
        try {
            STORE.restoreAndClear(store, ref, uuid);
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] inventory restore failed for " + uuid + ": " + t.getMessage());
        }
    }

    /**
     * Restore a held snapshot for an online player by uuid (self-resolving) - the instance-spawn-
     * failure path and the {@link #onPlayerReady} crash/disconnect net. Hops to the player's current
     * world thread. No-op when nothing is held or the player is offline (the snapshot survives on disk
     * for the next login).
     */
    public static void restore(@Nonnull UUID uuid) {
        if (!STORE.has(uuid)) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null) {
            return; // offline: restore on next PlayerReady
        }
        World world = Universe.get().getWorld(pr.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = pr.getReference();
                if (ref != null && ref.isValid()) {
                    STORE.restoreAndClear(world.getEntityStore().getStore(), ref, uuid);
                }
            } catch (Throwable t) {
                SafeLog.warn("[Kweebec] inventory restore (by uuid) failed for " + uuid + ": " + t.getMessage());
            }
        });
    }

    /** Whether a snapshot is currently held for {@code uuid}. */
    public static boolean has(@Nonnull UUID uuid) {
        return STORE.has(uuid);
    }

    /**
     * Crash/disconnect net: on a player's login, if a snapshot is held AND they are not currently in
     * an active round, restore it. A player who entered a round and then crashed / disconnected / rode
     * a server restart (so the normal in-instance exit never restored them) gets their gear back here.
     * A player still bound to a live round is left alone (that round's exit will restore them).
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        if (!STORE.has(uuid)) {
            return;
        }
        // Still mid-round? Leave the snapshot for the round's own exit path.
        if (RoundService.getInstance().registry().forPlayer(uuid) != null) {
            return;
        }
        restore(uuid);
    }
}
