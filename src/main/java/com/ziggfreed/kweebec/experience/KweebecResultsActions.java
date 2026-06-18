package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.instance.result.ResultsActions;
import com.ziggfreed.kweebec.asset.PresetConfig;
import com.ziggfreed.kweebec.lobby.KweebecLobby;

/**
 * The results-screen footer handlers: "View Leaderboard" deep-links the generic
 * {@link LeaderboardPage} to the just-played bucket; "Play Again" re-queues the default
 * preset and shows the queue screen.
 */
public final class KweebecResultsActions implements ResultsActions {

    @Override
    public void viewLeaderboard(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store, @Nullable String bucket) {
        try {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new LeaderboardPage(player, KweebecExperience.leaderboardDeps(), bucket));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void playAgain(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store) {
        KweebecLobby.join(player.getUuid(), PresetConfig.DEFAULT);
        KweebecParty.openQueue(player, ref, store);
    }
}
