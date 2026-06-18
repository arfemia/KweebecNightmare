package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.reward.InstanceRewardGranter;

/**
 * Kweebec's executor for the non-item reward kinds. Standalone Kweebec has no currency
 * system (currency rewards no-op) and authors no command rewards in v1; item rewards go
 * through the common granter's inventory-guarded path and never reach this sink. An
 * installed MMO consuming the outbound events grants its richer rewards there instead.
 */
public final class KweebecRewardSink implements InstanceRewardGranter.Sink {

    public static final KweebecRewardSink INSTANCE = new KweebecRewardSink();

    private KweebecRewardSink() {
    }

    @Override
    public boolean grantCurrency(@Nonnull String currencyId, int amount, @Nonnull PlayerRef player,
                                 @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return false; // no standalone currency system
    }

    @Override
    public boolean runCommand(@Nonnull String command, @Nonnull PlayerRef player,
                              @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return false; // no command rewards authored in v1
    }
}
