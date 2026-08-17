package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.play.PlayModeHandler;
import com.ziggfreed.common.inventory.PlayerAccess;
import com.ziggfreed.common.lobby.GroupJoinResult;
import com.ziggfreed.common.lobby.JoinResult;
import com.ziggfreed.common.party.page.PartyInvitePage;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Kweebec's {@link PlayModeHandler}: maps the Play screen's three cards onto the three
 * lobby paths. Public joins the shared queue (the page then morphs to the live roster);
 * Solo launches immediately and alone; Party opens the party manager carrying the chosen
 * difficulty (so the owner's Queue button queues at that preset, not the default).
 */
public final class KweebecPlayMode implements PlayModeHandler {

    @Override
    public void queuePublic(@Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store, @Nullable String presetId) {
        PlayerRef player = playerOrNull(store, ref, "queue");
        if (player == null) {
            return;
        }
        KweebecLobby.join(player.getUuid(), presetId);
        // The PlayModePage reopens itself after this call, morphing to the live roster.
    }

    @Override
    public void launchSolo(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Store<EntityStore> store, @Nullable String presetId) {
        PlayerRef player = playerOrNull(store, ref, "solo launch");
        if (player == null) {
            return;
        }
        GroupJoinResult result = KweebecLobby.launchSolo(player.getUuid(), presetId);
        // On success the round launches immediately and the PlayModePage closes itself. On a clean
        // block (already in a round / already queued elsewhere) the group-join sends no toast, so
        // surface the reason here - the page has closed, so this lands in the world feed.
        if (!result.ok()) {
            player.sendMessage(Lang.msg(reasonKey(result.reason())));
        }
    }

    @Nonnull
    private static String reasonKey(@Nonnull JoinResult reason) {
        return switch (reason) {
            case ALREADY_ENGAGED -> Lang.CMD_ALREADY_IN_ROUND;
            case ALREADY_QUEUED -> Lang.CMD_ALREADY_QUEUED;
            default -> Lang.CMD_START_FAILED;
        };
    }

    @Override
    public void openParty(@Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store, @Nullable String presetId) {
        PlayerRef player = playerOrNull(store, ref, "party manager");
        if (player == null) {
            return;
        }
        try {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new PartyInvitePage(player, KweebecExperience.partyDeps(), "party", null, presetId));
            }
        } catch (Throwable ignored) {
            // a page-open failure must not break the Play screen interaction
        }
    }

    /**
     * The player behind this entity, or null having said so: every card on the Play screen is
     * pressed by somebody, so an entity with no {@link PlayerRef} means the press outlived them.
     */
    @Nullable
    private static PlayerRef playerOrNull(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull String what) {
        PlayerRef player = PlayerAccess.playerRef(store, ref);
        if (player == null) {
            SafeLog.fine("[play] no player behind the entity that asked for a " + what);
        }
        return player;
    }
}
