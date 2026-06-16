package com.ziggfreed.kweebec.death;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.event.RoundEvents;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Cocoon + revive logic. When a survivor dies inside a round, the custom
 * {@link CocoonOnDeathSystem} suppresses the vanilla respawn menu and routes here:
 * the player is held dead-in-place (the engine never auto-respawns and corpse
 * culling excludes players), marked cocooned, and made rescuable by a teammate
 * until their bleed-out deadline. Revive uses {@code DeathComponent.respawn} (the
 * world's RespawnController = the instance spawn provider), which removes the
 * {@link DeathComponent} on completion.
 *
 * <p>All methods run on the instance world thread.
 */
public final class CocoonService {

    private static final String COCOON_EFFECT_ID = "KweebecNightmare_Cocoon";

    private CocoonService() {
    }

    /** A survivor died: cocoon them. Runs in the death system pass (world thread). */
    public static void onDeath(@Nonnull RoundInstance round, @Nonnull UUID uuid,
                               @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> cb) {
        PlayerRoundState st = round.playerState(uuid);
        if (st == null || st.isCocooned()) {
            return;
        }

        st.incrementDowns();
        st.setCocooned(true);

        int bleedOut = round.ruleSet().bleedOutSeconds();
        long deadline = bleedOut < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + bleedOut * 1000L;
        st.setBleedOutDeadlineMs(deadline);

        applyCocoonEffect(ref, cb);

        RoundEvents.firePlayerCocooned(round.roundId(), round.mode().id(), uuid);

        // Toasts: the victim + the rest of the party.
        PlayerRef victim = store.getComponent(ref, PlayerRef.getComponentType());
        if (victim != null) {
            RoundFeedback.dangerToast(victim, Lang.TOAST_COCOONED);
        }
        notifyParty(round, uuid, Lang.TOAST_TEAMMATE_DOWN);

        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] " + uuid + " cocooned (down " + st.downsUsed() + ") in " + round.roundId());
    }

    /**
     * A teammate freed the cocoon: revive the player at the instance spawn and
     * clear their cocoon state. Runs on the world thread.
     */
    public static void revive(@Nonnull RoundInstance round, @Nonnull UUID uuid, @Nullable UUID rescuer,
                              @Nonnull World world, @Nonnull Store<EntityStore> store) {
        PlayerRoundState st = round.playerState(uuid);
        if (st == null || !st.isCocooned()) {
            return;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        // Clear effects, then respawn via DeathComponent.respawn, which runs the
        // world's RespawnController (the instance spawn provider) and removes the
        // DeathComponent on completion.
        clearCocoonEffect(ref, store);
        try {
            DeathComponent.respawn(store, ref).whenComplete((v, err) -> {
                if (err != null) {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] revive respawn failed for " + uuid + ": " + err.getMessage());
                    return;
                }
                world.execute(() -> {
                    st.setCocooned(false);
                    st.setBleedOutDeadlineMs(0L);
                    RoundEvents.firePlayerRescued(round.roundId(), round.mode().id(), uuid, rescuer);
                    PlayerRef revived = Universe.get().getPlayer(uuid);
                    if (revived != null) {
                        RoundFeedback.successToast(revived, Lang.TOAST_RESCUED);
                    }
                });
            });
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] revive failed for " + uuid + ": " + t.getMessage());
        }
    }

    /** Whether a cocooned player can still be rescued under the rule-set + bleed-out. */
    public static boolean canRescue(@Nonnull RoundInstance round, @Nonnull PlayerRoundState st) {
        return st.isCocooned()
                && st.isReviveAllowed(round.ruleSet())
                && System.currentTimeMillis() < st.bleedOutDeadlineMs();
    }

    private static void notifyParty(@Nonnull RoundInstance round, @Nonnull UUID downed, @Nonnull String key) {
        for (PlayerRoundState other : round.playerStates()) {
            if (other.playerId().equals(downed) || !other.isActive()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(other.playerId());
            if (pr != null) {
                RoundFeedback.warningToast(pr, key);
            }
        }
    }

    private static void applyCocoonEffect(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> cb) {
        try {
            int idx = EntityEffect.getAssetMap().getIndex(COCOON_EFFECT_ID);
            if (idx == Integer.MIN_VALUE) {
                return;
            }
            EntityEffect cocoon = EntityEffect.getAssetMap().getAsset(idx);
            EffectControllerComponent effects = cb.getComponent(ref, EffectControllerComponent.getComponentType());
            if (cocoon != null && effects != null) {
                effects.addEffect(ref, cocoon, cb);
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] cocoon effect failed: " + t.getMessage());
        }
    }

    private static void clearCocoonEffect(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effects != null) {
                effects.clearEffects(ref, store);
            }
        } catch (Throwable ignored) {
            // best effort
        }
    }
}
