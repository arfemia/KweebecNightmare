package com.ziggfreed.kweebec.experience;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.ziggfreed.common.instance.reward.InstanceRewardGranter;
import com.ziggfreed.common.inventory.PlayerAccess;
import com.ziggfreed.common.util.CommandExecutor;

/**
 * Kweebec's executor for the non-item reward kinds. Standalone Kweebec has no currency
 * system (currency rewards no-op); item rewards go through the common granter's
 * inventory-guarded path and never reach this sink.
 *
 * <p>COMMAND rewards ARE honoured: they run as CONSOLE via the common {@link CommandExecutor}.
 * This is the soft-integration seam - a server owner authors an {@code xp}/command reward in a
 * loot table (e.g. an installed MMO Skill Tree ships a table contributing {@code /mmoawardxp}
 * entries) and it fires here as a plain console command, so Kweebec needs NO compile dependency on
 * the granting mod. When no such reward is authored (standalone), the sink is simply never called
 * with a command. The granter has already substituted {@code {amount}}; this substitutes
 * {@code {player}} from the online claimer's username (fresh at claim time in the overworld).
 */
public final class KweebecRewardSink implements InstanceRewardGranter.Sink {

    public static final KweebecRewardSink INSTANCE = new KweebecRewardSink();

    private KweebecRewardSink() {
    }

    @Override
    public boolean grantCurrency(@Nonnull String currencyId, int amount,
                                 @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return false; // no standalone currency system
    }

    @Override
    public boolean runCommand(@Nonnull String command,
                              @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PlayerRef player = PlayerAccess.playerRef(store, ref);
        if (player == null) {
            // No claimer to substitute, and a command carrying a raw {player} would run as garbage.
            return false;
        }
        String resolved = command.replace("{player}", player.getUsername());
        return CommandExecutor.executeAsConsole(resolved, player.getUsername());
    }
}
