package com.ziggfreed.kweebec.round;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.arena.ArenaDefinitionAsset;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Mode-agnostic per-player helpers shared by every {@link RoundMode} (the engine-thread primitives the
 * Chase loop pioneered, lifted so Clash / Domination reuse one copy instead of re-deriving them). All run
 * on the instance world thread (the caller guarantees it).
 */
public final class RoundModeSupport {

    private RoundModeSupport() {
    }

    /** The player's entity ref iff they are present in THIS instance world, else {@code null}. */
    @Nullable
    public static Ref<EntityStore> presentRef(@Nonnull UUID uuid, @Nonnull UUID worldUuid) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null || !worldUuid.equals(pr.getWorldUuid())) {
            return null;
        }
        return pr.getReference();
    }

    /** A present player's world position, or {@code null} if the ref is invalid / has no transform. */
    @Nullable
    public static Vector3d positionOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        return tc == null ? null : tc.getPosition();
    }

    /** Run an action for every non-left participant who is currently online (any world). */
    public static void forEachPresent(@Nonnull RoundInstance round, @Nonnull Consumer<PlayerRef> action) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr != null) {
                action.accept(pr);
            }
        }
    }

    /** Ticks for a respawn (DeathComponent removal) to settle before the team-spawn teleport. */
    private static final long RESPAWN_SETTLE_MS = 500L;

    /**
     * Teleport a PvP player to one of their team's spawn anchors (spread by their index within the team).
     * No-op when the round has no arena or the team has no authored spawns. World thread.
     */
    public static void teleportToTeamSpawn(@Nonnull RoundInstance round, @Nonnull Ref<EntityStore> ref,
                                           @Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        ArenaDefinitionAsset arena = round.arena();
        if (arena == null) {
            return;
        }
        int team = round.teamOf(uuid);
        if (team < 0) {
            return;
        }
        List<List<ArenaDefinitionAsset.Anchor>> spawns = arena.teamSpawns();
        if (team >= spawns.size()) {
            return;
        }
        List<ArenaDefinitionAsset.Anchor> teamSpawns = spawns.get(team);
        if (teamSpawns.isEmpty()) {
            return;
        }
        int idx = Math.max(0, round.membersOfTeam(team).indexOf(uuid)) % teamSpawns.size();
        ArenaDefinitionAsset.Anchor a = teamSpawns.get(idx);
        try {
            Teleport tp = Teleport.createForPlayer(
                    new Transform(a.x(), a.y(), a.z(), 0f, (float) a.yaw(), 0f));
            store.putComponent(ref, Teleport.getComponentType(), tp);
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] team-spawn teleport failed: " + t.getMessage());
        }
    }

    /**
     * Respawn a dead PvP player at their team spawn: clear the DeathComponent (heal) then, after a settle
     * window, teleport them to their team anchor. The swapped model persists through respawn (it is saved on
     * the entity), so it is not re-applied. World thread; best-effort.
     */
    public static void respawnAtTeamSpawn(@Nonnull RoundInstance round, @Nonnull World world,
                                          @Nonnull Store<EntityStore> store, @Nonnull UUID uuid,
                                          @Nonnull UUID worldUuid) {
        Ref<EntityStore> ref = presentRef(uuid, worldUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        try {
            DeathComponent.respawn(store, ref).whenComplete((v, e) ->
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> {
                        Ref<EntityStore> r2 = presentRef(uuid, worldUuid);
                        if (r2 != null && r2.isValid()) {
                            teleportToTeamSpawn(round, r2, world.getEntityStore().getStore(), uuid);
                        }
                    }), RESPAWN_SETTLE_MS, TimeUnit.MILLISECONDS));
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] respawn-at-team-spawn failed: " + t.getMessage());
        }
    }
}
