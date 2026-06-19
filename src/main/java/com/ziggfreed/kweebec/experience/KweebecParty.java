package com.ziggfreed.kweebec.experience;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.play.PlayModePage;
import com.ziggfreed.common.party.Party;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.lobby.KweebecLobby;

/**
 * Kweebec's party-page Queue handoff: when the owner presses Queue on the
 * {@link com.ziggfreed.common.party.page.PartyInvitePage}, queue the whole party as a
 * unit through the lobby's group-join (public, so a partial party backfills with
 * strangers; private when the owner sealed the party), then open the Play screen so they
 * stay on a closable live roster. The chosen difficulty ({@code presetId}) is threaded
 * from the Play screen's Party card; a {@code null} preset (a standalone {@code /party})
 * falls back to the default. A solo player (no multi-member party) just queues normally.
 */
public final class KweebecParty {

    private KweebecParty() {
    }

    public static void queueParty(@Nonnull PlayerRef initiator, @Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store, @Nullable String presetId) {
        String preset = (presetId == null || presetId.isBlank()) ? PresetConfig.DEFAULT : presetId;
        Party party = KweebecExperience.partyService().partyOf(initiator.getUuid());
        if (party != null && party.size() > 1) {
            // Private = launch this party alone (scope = party id); public = backfill with strangers.
            UUID privateScope = party.isPrivate() ? UUID.fromString(party.id()) : null;
            KweebecLobby.queueParty(party.orderedMembers(), party.owner(), preset, privateScope);
        } else {
            KweebecLobby.join(initiator.getUuid(), preset);
        }
        openQueue(initiator, ref, store);
    }

    /** Open the Play screen for {@code pr} (the live roster state, since the player is now queued). */
    public static void openQueue(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                 @Nonnull Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store,
                        new PlayModePage(pr, KweebecExperience.playModeDeps(), null));
            }
        } catch (Throwable ignored) {
            // a missing component / page-manager failure must not break the queue join
        }
    }
}
