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
import com.ziggfreed.common.health.HealthUtil;
import com.ziggfreed.kweebec.moonbloom.GlowThrowables;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.round.RuleSet;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The in-round GLOW-MUSHROOM pickup observer. Gather is asset-only (the {@code *_Plant} block's
 * {@code Gathering.Harvest.DropList} drops the charge item), so there is no Java seam at gather time;
 * this observer supplies one by listening to the {@link InteractivelyPickupItemEvent} the engine fires
 * when a player picks a dropped charge into their inventory. For an in-round player picking up a grove
 * shroom ({@link Moonbloom#CHARGE_ITEM} or a {@link GlowThrowables} variant) it does two things:
 *
 * <ul>
 *   <li><b>Stat credit</b> - {@link Moonbloom#CHARGE_ITEM} pickups add the picked-up quantity to
 *       {@link PlayerRoundState#addMoonbloomCollected} (the lifetime moonbloom-collected leaderboard
 *       input; Moonbloom is the only stat-tracked gather).</li>
 *   <li><b>Gather heal</b> - if the active preset authors a {@link RuleSet#gatherHealthRestore(String)}
 *       for the picked item, the gatherer is topped off by {@code perShroom * quantity} HP via
 *       ziggfreed-common {@link HealthUtil#heal} (so a stack of N heals N times). This is the
 *       per-difficulty "scavenging heals you" dial - generous on Amateur, a trickle on Hardcore.</li>
 * </ul>
 *
 * <p>Read-only on the pickup itself: it never cancels or alters the stack, and it is inert for any
 * player not in a round (and for any non-shroom item, via the cheap {@link #isGatherShroom} pre-filter).
 * Runs on the world thread; the whole body is try-guarded.
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
            String itemId = stack.getItemId();
            if (!isGatherShroom(itemId)) {
                return; // not a grove glow-mushroom - nothing to track or heal
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
                return; // gathering outside a round - not tracked / no heal
            }
            // Stat credit: Moonbloom is the only gather counted toward the lifetime leaderboard.
            if (Moonbloom.CHARGE_ITEM.equals(itemId)) {
                PlayerRoundState st = round.playerState(uuid);
                if (st != null) {
                    st.addMoonbloomCollected(qty);
                }
            }
            // Gather heal: top the gatherer off by the preset's authored HP-per-shroom for this item
            // (absent = no heal). A stack of N shrooms heals N times the per-shroom amount.
            RuleSet rules = round.ruleSet();
            Double perShroom = rules.gatherHealthRestore(itemId);
            if (perShroom != null && perShroom > 0.0) {
                HealthUtil.heal(store, ref, (float) (perShroom * qty));
            }
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] moonbloom-collect observe failed: " + t.getMessage());
        }
    }

    /**
     * Whether {@code itemId} is one of the grove's gatherable glow-mushrooms ({@link Moonbloom#CHARGE_ITEM}
     * or a {@link GlowThrowables} variant) - the cheap string pre-filter that keeps this observer inert for
     * the overwhelmingly common non-shroom pickup BEFORE the per-player round lookup.
     */
    private static boolean isGatherShroom(@Nullable String itemId) {
        if (itemId == null) {
            return false;
        }
        return Moonbloom.CHARGE_ITEM.equals(itemId)
                || GlowThrowables.EMBER_ITEM.equals(itemId)
                || GlowThrowables.GUST_ITEM.equals(itemId)
                || GlowThrowables.MIRE_ITEM.equals(itemId);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
