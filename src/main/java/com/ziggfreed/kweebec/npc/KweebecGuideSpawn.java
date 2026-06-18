package com.ziggfreed.kweebec.npc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.npc.NpcSpawnService;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Spawns the "Grove Warden" guide NPC ({@code KweebecNightmare_Guide}) once per world
 * on {@link PlayerReadyEvent}, near the world spawn, via the lifted
 * {@link NpcSpawnService}. Idempotency is a kweebec-side per-world set (NOT a lifted
 * singleton placement store, which would clobber across mods); a recorded per-world
 * UUID lets the {@code /kweebec spawnguide} debug hatch despawn the old one before
 * re-placing.
 *
 * <p>The guide belongs in the persistent world players ready into; round instance
 * worlds are entered by teleport (no {@code PlayerReadyEvent}), so it never spawns
 * inside a round. All Store/Ref work hops to {@code world.execute}; every path is
 * guarded so a throw never breaks player-ready.
 */
public final class KweebecGuideSpawn {

    /** The pack-authored Passive role the guide uses. */
    public static final String GUIDE_ROLE = "KweebecNightmare_Guide";

    // Placed off the spawn point so the guide is not standing on the joining player, and
    // deliberately on the OPPOSITE side of spawn from the MMO Skill Tree hub NPC (its
    // default offset is +2.5 X) so the two never overlap when both mods are installed.
    private static final double OFFSET_X = -3.0;
    private static final double OFFSET_Z = 0.0;
    /** Faces roughly toward the spawn point (a player arriving at spawn sees its front). */
    private static final float YAW = 90.0f;

    private static final Set<String> spawnedWorlds = ConcurrentHashMap.newKeySet();
    private static final Map<String, UUID> guideByWorld = new ConcurrentHashMap<>();

    private KweebecGuideSpawn() {
    }

    /** PlayerReady hook (registered in plugin setup): spawn the guide once per world. */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            World world = player.getWorld();
            if (world == null) {
                return;
            }
            world.execute(() -> spawnIfAbsent(world, player));
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] guide auto-spawn (ready) failed: " + t.getMessage());
        }
    }

    private static void spawnIfAbsent(@Nonnull World world, @Nonnull Player player) {
        String worldName = world.getName();
        if (!spawnedWorlds.add(worldName)) {
            return; // already spawned for this world (atomic claim)
        }
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null) {
                spawnedWorlds.remove(worldName);
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Vector3dc base = NpcSpawnService.resolveSpawnPosition(world, store, ref);
            if (base == null) {
                spawnedWorlds.remove(worldName);
                return;
            }
            Vector3d pos = new Vector3d(base.x() + OFFSET_X, base.y(), base.z() + OFFSET_Z);
            if (!place(world, store, worldName, pos, YAW)) {
                spawnedWorlds.remove(worldName); // let a later join retry
            } else {
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] spawned Grove Warden guide in world '" + worldName + "'.");
            }
        } catch (Throwable t) {
            spawnedWorlds.remove(worldName);
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] guide spawn failed: " + t.getMessage());
        }
    }

    /**
     * Debug escape hatch ({@code /kweebec spawnguide}): despawn the recorded guide for
     * this world (if any) and place a fresh one at {@code pos}. World thread only.
     */
    public static boolean reposition(@Nonnull World world, @Nonnull Store<EntityStore> store,
                                     @Nonnull Vector3dc pos, float yaw) {
        String worldName = world.getName();
        UUID old = guideByWorld.get(worldName);
        if (old != null) {
            NpcSpawnService.despawn(store, old);
        }
        spawnedWorlds.add(worldName); // suppress the auto-spawn now that one is placed
        return place(world, store, worldName, pos, yaw);
    }

    private static boolean place(@Nonnull World world, @Nonnull Store<EntityStore> store,
                                 @Nonnull String worldName, @Nonnull Vector3dc pos, float yaw) {
        return NpcSpawnService.spawnRole(world, store, GUIDE_ROLE, pos, yaw, (npc, npcRef, st) -> {
            try {
                UUIDComponent uc = st.getComponent(npcRef, UUIDComponent.getComponentType());
                if (uc != null && uc.getUuid() != null) {
                    guideByWorld.put(worldName, uc.getUuid());
                }
            } catch (Throwable ignored) {
                // best-effort UUID record (only used by the debug reposition)
            }
        });
    }
}
