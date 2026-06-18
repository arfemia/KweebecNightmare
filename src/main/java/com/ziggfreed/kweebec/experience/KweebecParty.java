package com.ziggfreed.kweebec.experience;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.queue.QueuePage;
import com.ziggfreed.common.party.Party;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.lobby.KweebecLobby;

/**
 * Kweebec's party-page Queue handoff: when the owner presses Queue on the
 * {@link com.ziggfreed.common.party.page.PartyInvitePage}, queue the whole party as a
 * unit through the lobby's group-join (public queue, so a partial party backfills with
 * strangers), then open the queue screen so they stay on a closable view. A solo player
 * (no multi-member party) just queues normally.
 */
public final class KweebecParty {

    private KweebecParty() {
    }

    public static void queueParty(@Nonnull PlayerRef initiator, @Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store) {
        Party party = KweebecExperience.partyService().partyOf(initiator.getUuid());
        if (party != null && party.size() > 1) {
            // Private = launch this party alone (scope = party id); public = backfill with strangers.
            UUID privateScope = party.isPrivate() ? UUID.fromString(party.id()) : null;
            KweebecLobby.queueParty(party.orderedMembers(), party.owner(), PresetConfig.DEFAULT, privateScope);
        } else {
            KweebecLobby.join(initiator.getUuid(), PresetConfig.DEFAULT);
        }
        openQueue(initiator, ref, store);
    }

    /** Open the queue screen for {@code pr} (the still-closable post-queue view). */
    public static void openQueue(@Nonnull PlayerRef pr, @Nonnull Ref<EntityStore> ref,
                                 @Nonnull Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().openCustomPage(ref, store, new QueuePage(pr, KweebecExperience.queueDeps()));
            }
        } catch (Throwable ignored) {
            // a missing component / page-manager failure must not break the queue join
        }
    }
}
