package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.instance.play.PlayModePage;
import com.ziggfreed.common.instance.result.ResultsActions;
import com.ziggfreed.common.inventory.PlayerAccess;
import com.ziggfreed.kweebec.asset.PresetConfig;

/**
 * The results-screen footer handlers: "View Leaderboard" deep-links the generic
 * {@link LeaderboardPage} to the just-played bucket; "Play Again" re-opens the Play screen
 * (the Public / Party / Solo mode chooser) for the default preset.
 */
public final class KweebecResultsActions implements ResultsActions {

    @Override
    public void viewLeaderboard(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store, @Nullable String bucket) {
        try {
            // Inside the guard: a raw component read on a ref that no longer resolves throws, and
            // this whole surface is fail-soft.
            PlayerRef player = PlayerAccess.playerRef(store, ref);
            Player p = store.getComponent(ref, Player.getComponentType());
            if (player != null && p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new LeaderboardPage(player, KweebecExperience.leaderboardDeps(), bucket));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void playAgain(@Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store) {
        try {
            // Inside the guard: a raw component read on a ref that no longer resolves throws, and
            // this whole surface is fail-soft.
            PlayerRef player = PlayerAccess.playerRef(store, ref);
            Player p = store.getComponent(ref, Player.getComponentType());
            if (player != null && p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new PlayModePage(player, KweebecExperience.playModeDeps(), PresetConfig.DEFAULT));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean claimRewards(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store) {
        return KweebecExperience.claimPending(ref, store);
    }
}
