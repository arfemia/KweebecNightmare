package com.ziggfreed.kweebec.hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * AI-driven hunter: spawns the pack's hostile Blighted-Kweebec role and keeps it
 * locked onto a survivor by re-asserting the marked target every tick (the engine
 * clears a marked player target each frame unless the target is an Adventure-mode
 * survivor - we keep survivors in Adventure and re-assert). During RITUAL each
 * hunter chases the nearest active survivor; the gate alert hard-locks every
 * hunter onto the single nearest survivor.
 *
 * <p>The live walk-speed ramp has no runtime setter in 0.5.3 (speed is baked on
 * the role's Walk controller), so corruption-scaled speed is left as an
 * asset/post-jam concern; the chase escalation here is darkness + heartbeat tier
 * + the alert lock-on. See the handoff checklist.
 */
public final class AiHunterController implements HunterController {

    /** Target-slot the role asset must declare (default), or setMarkedTarget silently no-ops. */
    private static final String TARGET_SLOT = MarkedEntitySupport.DEFAULT_TARGET_SLOT; // "LockedTarget"

    /** The pack hostile Kweebec role id. */
    private final String roleName;

    private final List<Ref<EntityStore>> hunters = new ArrayList<>();
    @Nullable
    private volatile UUID alertTarget;

    public AiHunterController(@Nonnull String roleName) {
        this.roleName = roleName;
    }

    @Override
    public void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] NPCPlugin unavailable; no hunter.");
            return;
        }
        int roleIndex = npc.getIndex(roleName);
        if (roleIndex < 0) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] hunter role '" + roleName + "' not registered; no hunter will spawn.");
            return;
        }

        int count = Math.max(1, round.ruleSet().hunterCount());
        Anchor den = ArenaLayout.HUNTER_DEN;
        for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0) * 2.0;
            Vector3d pos = new Vector3d(den.x() + offset, den.y(), den.z());
            Rotation3f rot = new Rotation3f(0f, den.yaw(), 0f);
            try {
                var spawned = npc.spawnEntity(store, roleIndex, pos, rot, null,
                        (npcEntity, npcRef, st) -> lockNearest(round, world, st, npcRef, npcEntity));
                if (spawned != null) {
                    hunters.add(spawned.first());
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] hunter spawn failed: " + t.getMessage());
            }
        }
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] spawned " + hunters.size() + " hunter(s) in " + round.roundId());
    }

    @Override
    public void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (hunters.isEmpty()) {
            return;
        }
        UUID forced = alertTarget;
        hunters.removeIf(ref -> ref == null || !ref.isValid());
        for (Ref<EntityStore> hunterRef : hunters) {
            NPCEntity npc = store.getComponent(hunterRef, NPCEntity.getComponentType());
            if (npc == null) {
                continue;
            }
            Ref<EntityStore> targetRef = forced != null
                    ? survivorRef(forced)
                    : nearestSurvivorRef(round, world, store, hunterRef);
            if (targetRef == null || !targetRef.isValid()) {
                continue;
            }
            ensureAdventure(targetRef, store);
            Role role = npc.getRole();
            if (role != null) {
                role.setMarkedTarget(TARGET_SLOT, targetRef);
            }
        }
    }

    @Override
    public void onAlert(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        // Pick the single nearest active survivor to the gate and hard-lock everyone on them.
        UUID nearest = nearestSurvivorToGate(round, store);
        this.alertTarget = nearest;
        if (nearest != null) {
            tick(round, world, store);
        }
    }

    @Override
    public void despawnAll(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        for (Ref<EntityStore> ref : hunters) {
            if (ref != null && ref.isValid()) {
                try {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        hunters.clear();
        alertTarget = null;
    }

    // --- targeting helpers ---

    private void lockNearest(@Nonnull RoundInstance round, @Nonnull World world,
                             @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef,
                             @Nonnull NPCEntity npcEntity) {
        Ref<EntityStore> target = nearestSurvivorRef(round, world, store, npcRef);
        Role role = npcEntity.getRole();
        if (role != null && target != null && target.isValid()) {
            ensureAdventure(target, store);
            role.setMarkedTarget(TARGET_SLOT, target);
        }
    }

    @Nullable
    private Ref<EntityStore> nearestSurvivorRef(@Nonnull RoundInstance round, @Nonnull World world,
                                                @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> fromRef) {
        Vector3d from = positionOf(store, fromRef);
        if (from == null) {
            return anySurvivorRef(round);
        }
        Ref<EntityStore> best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = survivorRef(st.playerId());
            Vector3d p = positionOf(store, ref);
            if (ref == null || p == null) {
                continue;
            }
            double dx = p.x() - from.x();
            double dz = p.z() - from.z();
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = ref;
            }
        }
        return best;
    }

    @Nullable
    private UUID nearestSurvivorToGate(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        Anchor gate = ArenaLayout.GATE;
        UUID best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = survivorRef(st.playerId());
            Vector3d p = positionOf(store, ref);
            if (p == null) {
                continue;
            }
            double sq = gate.horizontalDistanceSq(p.x(), p.z());
            if (sq < bestSq) {
                bestSq = sq;
                best = st.playerId();
            }
        }
        return best;
    }

    @Nullable
    private Ref<EntityStore> anySurvivorRef(@Nonnull RoundInstance round) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.isActive()) {
                Ref<EntityStore> ref = survivorRef(st.playerId());
                if (ref != null && ref.isValid()) {
                    return ref;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Ref<EntityStore> survivorRef(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        return pr == null ? null : pr.getReference();
    }

    @Nullable
    private static Vector3d positionOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        return tc == null ? null : tc.getPosition();
    }

    private static void ensureAdventure(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null && p.getGameMode() != GameMode.Adventure) {
                Player.setGameMode(ref, GameMode.Adventure, store);
            }
        } catch (Throwable ignored) {
            // best effort - a Creative tester silently drops the lock
        }
    }

    /** Unused parameter kept for the chase-state seam (corruption-scaled difficulty hook). */
    @SuppressWarnings("unused")
    private static double difficultyOf(@Nonnull ChaseState chase, @Nonnull RoundInstance round) {
        return chase.hunterSpeed(round.ruleSet());
    }
}
