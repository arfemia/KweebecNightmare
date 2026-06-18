package com.ziggfreed.kweebec.interaction;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.inventory.InventoryUtil;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.mode.chase.ChaseMode;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.mode.chase.ShrineState;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * The interactable shrine FURNACE handler: a survivor presses F on a {@code KweebecNightmare_Shrine}
 * block to offer Moonbloom. Each press deposits up to the shrine's remaining need
 * ({@code RuleSet.cleanseCost() - submitted}) from the player's inventory; once the total is reached the
 * furnace lights with green fire (via {@link ChaseMode#lightShrine}). Registered as {@link #TYPE_NAME}
 * and referenced by the block's {@code KweebecNightmare_Shrine_Use} RootInteraction.
 *
 * <p>Runs on the instance world thread (the engine dispatches a block-Use there), the SAME thread the
 * 1 Hz round tick mutates shrine state on, so it mutates {@link ShrineState} directly with no hand-off.
 * Every exit path sets {@code ctx.getState().state} (the client otherwise hangs).
 */
public final class ShrineSubmitInteraction extends SimpleInstantInteraction {

    /** The codec type name referenced from the block's {@code KweebecNightmare_Shrine_Use} RootInteraction. */
    public static final String TYPE_NAME = "kweebec_shrine_submit";

    public static final BuilderCodec<ShrineSubmitInteraction> CODEC =
            BuilderCodec.builder(ShrineSubmitInteraction.class, ShrineSubmitInteraction::new,
                    SimpleInstantInteraction.CODEC)
                    .build();

    public static BuilderCodec<ShrineSubmitInteraction> getCODEC() {
        return CODEC;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType interactionType,
                            @Nonnull InteractionContext ctx,
                            @Nonnull CooldownHandler cooldownHandler) {
        try {
            var commandBuffer = ctx.getCommandBuffer();
            BlockPosition bp = ctx.getTargetBlock();
            if (commandBuffer == null || bp == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }
            Player player = commandBuffer.getComponent(ctx.getEntity(), Player.getComponentType());
            PlayerRef pr = commandBuffer.getComponent(ctx.getEntity(), PlayerRef.getComponentType());
            if (player == null || pr == null) {
                ctx.getState().state = InteractionState.Failed;
                return;
            }

            UUID uuid = pr.getUuid();
            RoundInstance round = RoundService.getInstance().registry().forPlayer(uuid);
            if (round == null) {
                // Pressed F on a shrine block outside an active round: nothing to do.
                ctx.getState().state = InteractionState.Finished;
                return;
            }
            World world = round.world();
            ChaseState chase = round.chaseState();
            if (world == null || chase == null) {
                ctx.getState().state = InteractionState.Finished;
                return;
            }
            // A player is only ever in their own round's instance world; reject a cross-world mismatch.
            if (!world.getWorldConfig().getUuid().equals(pr.getWorldUuid())) {
                ctx.getState().state = InteractionState.Finished;
                return;
            }
            // No cleansing during the PREP breather - the ritual (and the hunter) begin at RITUAL, matching
            // the pre-rework behavior where the proximity cleanse ran only in non-PREP phases.
            if (chase.phase() == ChasePhase.PREP) {
                RoundFeedback.warningToast(pr, Lang.TOAST_SHRINE_DORMANT);
                ctx.getState().state = InteractionState.Finished;
                return;
            }
            ShrineState shrine = chase.shrineAt(bp.x, bp.y, bp.z);
            if (shrine == null || shrine.isLit()) {
                // Not a tracked shrine furnace, or already cleansed: no-op (no Moonbloom consumed).
                ctx.getState().state = InteractionState.Finished;
                return;
            }

            int required = round.ruleSet().cleanseCost();
            if (required <= 0) {
                // Supply-free dial (cleanseCost 0): light on the first press.
                ChaseMode.lightShrine(round, world, chase, shrine);
                ctx.getState().state = InteractionState.Finished;
                return;
            }

            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();
            int remaining = required - shrine.submitted();
            int taken = remaining > 0 ? InventoryUtil.take(store, ref, Moonbloom.CHARGE_ITEM, remaining) : 0;
            if (taken <= 0) {
                // At the shrine but carrying no Moonbloom: nudge to gather more.
                RoundFeedback.warningToast(pr, Lang.TOAST_CLEANSE_NEED);
                ctx.getState().state = InteractionState.Finished;
                return;
            }
            shrine.addSubmitted(taken);
            if (shrine.submitted() >= required) {
                ChaseMode.lightShrine(round, world, chase, shrine);
            } else {
                RoundFeedback.infoToast(pr,
                        Lang.msg(Lang.TOAST_SHRINE_SUBMIT).param("0", shrine.submitted()).param("1", required));
            }
            ctx.getState().state = InteractionState.Finished;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] shrine submit failed: " + t.getMessage());
            ctx.getState().state = InteractionState.Failed;
        }
    }
}
