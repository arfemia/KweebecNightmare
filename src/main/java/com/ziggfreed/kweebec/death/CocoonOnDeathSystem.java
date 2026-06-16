package com.ziggfreed.kweebec.death;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * Intercepts player death: when a player who is in a Kweebec round dies, suppress
 * the vanilla respawn menu and cocoon them in place (rescuable by a teammate).
 * Players NOT in a round fall through untouched, so this mod never alters normal
 * death anywhere else on the server.
 *
 * <p>Ordered {@code BEFORE} {@code PlayerDeathScreen} so {@code setShowDeathMenu(false)}
 * is observed before the respawn page would open. Matched to players only.
 */
public final class CocoonOnDeathSystem extends DeathSystems.OnDeathSystem {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES =
            Set.of(new SystemDependency<>(Order.BEFORE, DeathSystems.PlayerDeathScreen.class));

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent death,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUID uuid = pr.getUuid();
        RoundInstance round = RoundService.getInstance().registry().forPlayer(uuid);
        if (round == null) {
            return; // not in a Kweebec round - let the vanilla death flow run
        }
        death.setShowDeathMenu(false);
        CocoonService.onDeath(round, uuid, ref, store, commandBuffer);
    }
}
