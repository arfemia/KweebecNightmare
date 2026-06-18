package com.ziggfreed.kweebec.npc;

import java.util.UUID;

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
 * {@link NpcSpawnService}. Idempotency is the kweebec-owned, file-backed
 * {@link KweebecGuidePlacementStore} (NOT a lifted singleton placement store, which
 * would clobber across mods, and NOT an in-memory set, which resets every boot and
 * stacks a fresh guide beside the persisted one on each restart); a recorded per-world
 * UUID lets the {@code /kweebec spawnguide} debug hatch despawn the old one before
 * re-placing.
 *
 * <p>The guide belongs in the persistent world players ready into; round instance
 * worlds are entered by teleport (no {@code PlayerReadyEvent}), so it never spawns
 * inside a round. All Store/Ref work hops to {@code world.execute}; every path is
 * guarded so a throw never breaks player-ready.
 */
public final class KweebecGuideSpawn {

    /** The default Passive role the guide uses (overridable via {@link KweebecGuideConfig#getRole()}). */
    public static final String GUIDE_ROLE = "KweebecNightmare_Guide";

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
        KweebecGuideConfig cfg = KweebecGuideConfig.getInstance();
        // Config-driven (mirrors MMO Skill Tree's spawn-hub): only the worlds in guide.json (default
        // ["default"]) get the guide. This excludes round instances + the creative hub - they fire
        // PlayerReadyEvent too, but are not in the list - so the guide stays in the main overworld.
        if (!cfg.isEnabled() || !cfg.shouldSpawnInWorld(worldName)) {
            return;
        }
        // Persistent once-per-world gate: the guide NPC persists in the world's entity store, so the
        // marker MUST persist too or a fresh boot stacks another guide beside the saved one. Mark AFTER
        // a successful place (mirrors hyMMO's maybeAutoSpawnHub), so a failed spawn simply retries on
        // the next join. PlayerReady world.execute tasks run serialized on the world thread, so the
        // check-then-mark needs no separate atomic claim.
        KweebecGuidePlacementStore placements = KweebecGuidePlacementStore.getInstance();
        if (placements.hasSpawned(worldName)) {
            return;
        }
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            Vector3dc base = NpcSpawnService.resolveSpawnPosition(world, store, ref);
            if (base == null) {
                return;
            }
            Vector3d pos = new Vector3d(
                    base.x() + cfg.getOffsetX(), base.y() + cfg.getOffsetY(), base.z() + cfg.getOffsetZ());
            if (place(world, store, worldName, pos, cfg.getYaw())) {
                placements.markSpawned(worldName);
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] spawned Grove Warden guide in world '" + worldName + "'.");
            }
        } catch (Throwable t) {
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
        KweebecGuidePlacementStore placements = KweebecGuidePlacementStore.getInstance();
        UUID old = placements.getGuide(worldName);
        if (old != null) {
            NpcSpawnService.despawn(store, old);
        }
        placements.markSpawned(worldName); // suppress the auto-spawn now that one is placed
        return place(world, store, worldName, pos, yaw);
    }

    private static boolean place(@Nonnull World world, @Nonnull Store<EntityStore> store,
                                 @Nonnull String worldName, @Nonnull Vector3dc pos, float yaw) {
        return NpcSpawnService.spawnRole(world, store, KweebecGuideConfig.getInstance().getRole(), pos, yaw, (npc, npcRef, st) -> {
            try {
                UUIDComponent uc = st.getComponent(npcRef, UUIDComponent.getComponentType());
                if (uc != null && uc.getUuid() != null) {
                    KweebecGuidePlacementStore.getInstance().recordGuide(worldName, uc.getUuid());
                }
            } catch (Throwable ignored) {
                // best-effort UUID record (only used by the debug reposition)
            }
        });
    }
}
