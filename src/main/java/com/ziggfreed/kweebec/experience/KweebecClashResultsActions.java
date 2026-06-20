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

/**
 * The PvP results-screen footer handlers: "View Leaderboard" deep-links the generic
 * {@link LeaderboardPage} to the just-played PvP bucket (the preset id) using the Clash board deps;
 * "Play Again" re-opens the Play screen for the default PvP preset. PvP rounds ship no exit spoils,
 * so {@code claimRewards} keeps the no-op default (all-claimed) - the page hides the Claim button
 * when there are no chips anyway.
 */
public final class KweebecClashResultsActions implements ResultsActions {

    /** The PvP preset the "Play Again" button re-opens the Play screen for (the 1v1 duel). */
    private static final String DEFAULT_PVP_PRESET = "clash_1v1";

    @Override
    public void viewLeaderboard(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store, @Nullable String bucket) {
        try {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new LeaderboardPage(player, KweebecClashExperience.leaderboardDeps(), bucket));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void playAgain(@Nonnull PlayerRef player, @Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store) {
        try {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.getPageManager().openCustomPage(ref, store,
                        new PlayModePage(player, KweebecExperience.playModeDeps(), DEFAULT_PVP_PRESET));
            }
        } catch (Throwable ignored) {
        }
    }
}
