package com.ziggfreed.kweebec.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.ziggfreed.common.npc.NpcSpawnService;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Keeps EXACTLY one "Grove Warden" guide NPC ({@code KweebecNightmare_Guide}) per
 * eligible world, reconciling against the LIVE world entity store on
 * {@link PlayerReadyEvent} (near the world spawn, via the lifted
 * {@link NpcSpawnService}).
 *
 * <p><b>The world entity store is the source of truth, not an external marker.</b> The
 * earlier design gated purely on the file-backed {@link KweebecGuidePlacementStore}
 * (a proxy for "has a guide already been placed here"); but that marker cannot SEE the
 * world, so it could neither clean up duplicates that an older build had already saved
 * into the world (the "one guide per restart" stacking the marker was meant to stop,
 * but only ever stopped going FORWARD) nor recover when the marker and the world drift
 * apart (the marker says spawned but the guide is gone, or vice versa). So instead we
 * scan once per world per boot: find every guide NPC by role, keep one, and despawn the
 * rest, spawning a fresh one only when none exists. The placement store is now just a
 * fast per-boot hint plus the per-world guide UUID the {@code /kweebec spawnguide} debug
 * hatch despawns before re-placing.
 *
 * <p>The guide belongs in the persistent world players ready into; round instance
 * worlds (and the creative hub) fire {@code PlayerReadyEvent} too, so they are kept
 * guide-free by simply NOT being in {@link KweebecGuideConfig}'s {@code worlds} list
 * (default {@code ["default"]}). All Store/Ref work hops to {@code world.execute};
 * every path is guarded so a throw never breaks player-ready.
 */
public final class KweebecGuideSpawn {

    /** The default Passive role the guide uses (overridable via {@link KweebecGuideConfig#getRole()}). */
    public static final String GUIDE_ROLE = "KweebecNightmare_Guide";

    /**
     * Worlds already reconciled this boot, so the (mildly costly) NPC scan runs ONCE per
     * world rather than on every player join. Reset every boot, by design - reconciliation
     * is keyed off the live world, which is itself rebuilt each boot.
     */
    private static final Set<String> reconciledThisBoot = ConcurrentHashMap.newKeySet();

    private KweebecGuideSpawn() {
    }

    /** PlayerReady hook (registered in plugin setup): reconcile the guide to exactly one per world. */
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
            world.execute(() -> reconcileGuide(world, player));
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] guide auto-spawn (ready) failed: " + t.getMessage());
        }
    }

    private static void reconcileGuide(@Nonnull World world, @Nonnull Player player) {
        String worldName = world.getName();
        KweebecGuideConfig cfg = KweebecGuideConfig.getInstance();
        // Config-driven (mirrors MMO Skill Tree's spawn-hub): only the worlds in guide.json (default
        // ["default"]) get the guide. Round instances + the creative hub fire PlayerReadyEvent too, but
        // are excluded by not being in the list, so the guide stays in the main overworld.
        if (!cfg.isEnabled() || !cfg.shouldSpawnInWorld(worldName)) {
            return;
        }
        // Scan once per world per boot. PlayerReady world.execute tasks run serialized on the world thread,
        // so this claim + the scan it guards need no separate atomic step.
        if (!reconciledThisBoot.add(worldName)) {
            return;
        }
        boolean reconciled = false;
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            reconciled = ensureExactlyOneGuide(world, store, ref, worldName, cfg);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] guide reconcile failed: " + t.getMessage());
        } finally {
            if (!reconciled) {
                // Couldn't resolve a spawn / the place failed: drop the claim so the next join retries.
                reconciledThisBoot.remove(worldName);
            }
        }
    }

    /**
     * Reconcile {@code world} to exactly one guide: keep the first existing guide (re-recording its UUID)
     * and despawn any extras, or spawn a fresh one near the world spawn if none exists. Returns true once
     * the world holds a guide (so the per-boot claim sticks), false on a spawn that couldn't proceed (so a
     * later join retries). World thread only.
     */
    private static boolean ensureExactlyOneGuide(@Nonnull World world, @Nonnull Store<EntityStore> store,
                                                 @Nonnull Ref<EntityStore> playerRef, @Nonnull String worldName,
                                                 @Nonnull KweebecGuideConfig cfg) {
        String role = cfg.getRole();
        List<Ref<EntityStore>> existing = findGuides(store, role);
        KweebecGuidePlacementStore placements = KweebecGuidePlacementStore.getInstance();

        if (!existing.isEmpty()) {
            Ref<EntityStore> keep = existing.get(0);
            recordUuid(store, worldName, keep, placements);
            int removed = 0;
            for (int i = 1; i < existing.size(); i++) {
                Ref<EntityStore> dup = existing.get(i);
                if (dup == null || !dup.isValid()) {
                    continue;
                }
                try {
                    store.removeEntity(dup, RemoveReason.REMOVE);
                    removed++;
                } catch (Throwable t) {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] failed to despawn a duplicate guide in '" + worldName + "': " + t.getMessage());
                }
            }
            placements.markSpawned(worldName);
            if (removed > 0) {
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] despawned " + removed + " duplicate Grove Warden guide(s) in world '" + worldName + "'.");
            }
            return true;
        }

        // None present: place a fresh one near the world spawn + the configured offset.
        Vector3dc base = NpcSpawnService.resolveSpawnPosition(world, store, playerRef);
        if (base == null) {
            return false;
        }
        Vector3d pos = new Vector3d(
                base.x() + cfg.getOffsetX(), base.y() + cfg.getOffsetY(), base.z() + cfg.getOffsetZ());
        if (place(world, store, worldName, pos, cfg.getYaw())) {
            placements.markSpawned(worldName);
            KweebecNightmarePlugin.LOGGER.atInfo().log(
                    "[Kweebec] spawned Grove Warden guide in world '" + worldName + "'.");
            return true;
        }
        return false;
    }

    /** Every live NPC ref in {@code store} whose role matches {@code role}. World thread only. */
    @Nonnull
    private static List<Ref<EntityStore>> findGuides(@Nonnull Store<EntityStore> store, @Nonnull String role) {
        List<Ref<EntityStore>> found = new ArrayList<>();
        try {
            store.forEachChunk(NPCEntity.getComponentType(), (chunk, cmd) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (npc != null && role.equals(npc.getRoleName())) {
                        found.add(chunk.getReferenceTo(i));
                    }
                }
            });
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] guide scan failed: " + t.getMessage());
        }
        return found;
    }

    /** Best-effort: record {@code guide}'s UUID for {@code worldName} (used by the debug reposition). */
    private static void recordUuid(@Nonnull Store<EntityStore> store, @Nonnull String worldName,
                                   @Nonnull Ref<EntityStore> guide, @Nonnull KweebecGuidePlacementStore placements) {
        try {
            UUIDComponent uc = store.getComponent(guide, UUIDComponent.getComponentType());
            if (uc != null && uc.getUuid() != null) {
                placements.recordGuide(worldName, uc.getUuid());
            }
        } catch (Throwable ignored) {
            // best-effort UUID record (only used by the debug reposition)
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
        placements.markSpawned(worldName);     // suppress the auto-spawn now that one is placed
        reconciledThisBoot.add(worldName);     // a later join's scan must not despawn this fresh placement
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
