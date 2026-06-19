package com.ziggfreed.kweebec.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Tracks Moonbloom GATHERED per player for the lifetime-stats leaderboard. Gather is asset-only
 * (the {@code Moonbloom_Plant} block's {@code Gathering.Harvest.DropList} drops the charge item),
 * so there is no Java seam at gather time; this observer supplies one by listening to the
 * {@link InteractivelyPickupItemEvent} the engine fires when a player picks the dropped charge
 * into their inventory. When an in-round player picks up {@link Moonbloom#CHARGE_ITEM}, credit the
 * picked-up quantity to their {@link PlayerRoundState#addMoonbloomCollected}.
 *
 * <p>Read-only: it never cancels the pickup or alters the stack, and it is inert for any player not
 * in a round (and for any other item). Runs on the world thread; the whole body is try-guarded.
 */
public final class MoonbloomCollectSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    public MoonbloomCollectSystem() {
        super(InteractivelyPickupItemEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull InteractivelyPickupItemEvent event) {
        try {
            if (event.isCancelled()) {
                return;
            }
            ItemStack stack = event.getItemStack();
            if (!Moonbloom.CHARGE_ITEM.equals(stack.getItemId())) {
                return;
            }
            int qty = stack.getQuantity();
            if (qty <= 0) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr == null) {
                return;
            }
            UUID uuid = pr.getUuid();
            if (uuid == null) {
                return;
            }
            RoundInstance round = RoundService.getInstance().registry().forPlayer(uuid);
            if (round == null) {
                return; // gathering outside a round - not tracked
            }
            PlayerRoundState st = round.playerState(uuid);
            if (st != null) {
                st.addMoonbloomCollected(qty);
            }
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] moonbloom-collect observe failed: " + t.getMessage());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
