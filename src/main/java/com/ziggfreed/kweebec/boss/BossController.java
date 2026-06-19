package com.ziggfreed.kweebec.boss;

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
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.world.SpawnPlacement;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.asset.BossAsset;
import com.ziggfreed.kweebec.feedback.BossHud;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.integration.KweebecNightmareAPI;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The multi-phase boss capstone driver: spawns the corrupted-Kweebec Warden at the escape climax (all
 * shrines lit, the Heartwood Gate open), tracks its HP every round tick, BACKSTOPS the phase swaps when its
 * HP crosses the asset-authored thresholds, summons Blight adds on each phase, drives the per-survivor
 * {@link BossHud}, and tears the boss + adds + HUD down on round end so nothing leaks.
 *
 * <p><b>Why Java-driven phase swaps.</b> The native Goblin_Duke phase system self-drives through ActionRole
 * + TriggerSpawnBeacon over arena prefab-path markers (Throne / Arena). This Warden does NOT replicate that
 * arena instruction tree (that asset surface is owned by the worldgen/arena session), so the swap is a Java
 * backstop: when the live phase entity's HP fraction drops at/below {@link BossAsset#phase2ThresholdFraction()}
 * / {@link BossAsset#phase3ThresholdFraction()}, the controller DESPAWNS the current phase entity and SPAWNS
 * the next phase role at the same position (re-rolling HP to the next phase's MaxHealth), plays the roar cue,
 * and summons that phase's adds. The HUD shows the per-phase HP fraction + a "Phase X/Y" indicator.
 *
 * <p>World-thread only (the round tick owns it). Best-effort throughout: a missing role/asset is logged and
 * skipped, never thrown into the round loop.
 *
 * <p>SPIKE - HP read: HP is read via {@link EntityStatMap}{@code .get("Health")} ({@link EntityStatValue#get()}
 * / {@link EntityStatValue#getMax()}); verify the Warden entity carries an EntityStatMap with a "Health" stat
 * at runtime (NPCs balanced via BalancingInitialisationSystem should). If absent, {@link #readHealth} returns
 * a null snapshot and the controller holds Phase 1 (no crash) - then the HP read needs an alternate source.
 *
 * <p>SPIKE - phase swap as despawn+respawn: swapping by despawn/respawn (vs the native in-place
 * {@code Type:Role} action) re-rolls aggro/target; the Warden role's huge sight ranges re-acquire the nearest
 * survivor within a tick, but verify the brief retarget reads acceptably in-game. If a seamless swap is
 * wanted, drive an in-place role change instead (needs an engine seam to set an entity's role from Java).
 */
public final class BossController {

    /** Total phase count (for the HUD "Phase X/Y" indicator). The Warden ships three phases. */
    private static final int TOTAL_PHASES = 3;
    /** Add-spawn ring radius (blocks) around the boss for the per-phase Blight summons. */
    private static final double ADD_RING_RADIUS = 6.0;

    private final BossAsset boss;

    /** The live boss entity (current phase), or {@code null} before spawn / after death. World-thread only. */
    @Nullable private Ref<EntityStore> bossRef;
    /** Live Blight adds the boss summoned (despawned at teardown). World-thread only. */
    private final List<Ref<EntityStore>> adds = new ArrayList<>();
    /** Per-survivor boss HUD handles, by player id (installed on spawn, stripped at teardown). */
    private final List<UUID> hudPlayers = new ArrayList<>();

    /** Current phase (1..3). 0 = not spawned. World-thread only. */
    private int phase = 0;
    /** True once the boss has been defeated (so we resolve/teardown exactly once). World-thread only. */
    private boolean defeated = false;

    private BossController(@Nonnull BossAsset boss) {
        this.boss = boss;
    }

    /**
     * Resolve the round's boss (rule-set {@code bossId} over the {@link KweebecNightmareAPI} fold) and build a
     * controller for it, or {@code null} when the round has no boss enabled / none is registered. Call from
     * {@code ChaseMode.openGate} only when {@code round.ruleSet().bossEnabled()}.
     */
    @Nullable
    public static BossController forRound(@Nonnull RoundInstance round) {
        BossAsset asset = KweebecNightmareAPI.resolveBoss(round.ruleSet().bossId());
        if (asset == null || asset.phase1Role() == null || asset.phase1Role().isBlank()) {
            SafeLog.warn("[Kweebec][boss] no usable boss resolved for round " + round.roundId()
                    + "; capstone skipped.");
            return null;
        }
        return new BossController(asset);
    }

    /**
     * Spawn the Phase-1 Warden at the gate (barring the escape), install the boss HUD for every survivor,
     * play the spawn cue, and summon Phase-1 adds. World-thread only; best-effort.
     */
    public void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            SafeLog.warn("[Kweebec][boss] NPCPlugin unavailable; no boss.");
            return;
        }
        Vector3d pos = bossSpawnPos(world);
        Ref<EntityStore> spawned = spawnRole(npc, store, boss.phase1Role(), pos, ArenaLayout.GATE.yaw());
        if (spawned == null) {
            SafeLog.warn("[Kweebec][boss] phase-1 spawn failed; capstone aborted.");
            return;
        }
        this.bossRef = spawned;
        this.phase = 1;
        installHuds(round, store);
        String spawnSound = boss.spawnSoundId();
        if (spawnSound != null && !spawnSound.isBlank()) {
            Sound3D.playAt(spawnSound, SoundCategory.SFX, spawned, store, "BOSS_SPAWN", false);
        }
        summonAdds(npc, store, world, Math.max(0, boss.phase1AddCount()));
        forEachSurvivor(round, pr -> RoundFeedback.title(pr,
                Lang.BOSS_TITLE_AWAKENS, Lang.BOSS_TITLE_AWAKENS_SUB, true));
        SafeLog.info("[Kweebec][boss] Warden spawned (phase 1) in round " + round.roundId());
    }

    /**
     * One round tick: drop dead adds, read the boss HP, backstop a phase swap if the HP fraction crossed a
     * threshold, push the HUD, and resolve a defeat (boss dead) exactly once. World-thread only; best-effort.
     * Returns {@code true} once the boss is defeated (so the caller can cue the victory beat), else {@code false}.
     */
    public boolean tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (defeated) {
            return true;
        }
        adds.removeIf(r -> r == null || !r.isValid());
        if (bossRef == null) {
            return false;
        }
        if (!bossRef.isValid()) {
            // The boss entity is gone (killed) - that is the win.
            onDefeated(round);
            return true;
        }
        HealthSnapshot hp = readHealth(store, bossRef);
        if (hp == null) {
            return false; // cannot read HP this tick; hold the current phase
        }
        maybeSwapPhase(round, world, store, hp);
        pushHuds(round, hp);
        return false;
    }

    /**
     * Backstop phase swap: when the live entity's HP fraction has dropped at/below the next phase's
     * threshold and that phase's role is authored, swap to it (despawn + respawn at the same pos, re-rolling
     * HP), play the roar, summon that phase's adds, and bump {@link #phase}. World-thread only.
     */
    private void maybeSwapPhase(@Nonnull RoundInstance round, @Nonnull World world,
                                @Nonnull Store<EntityStore> store, @Nonnull HealthSnapshot hp) {
        double frac = hp.fraction();
        if (phase == 1 && frac <= boss.phase2ThresholdFraction()
                && boss.phase2Role() != null && !boss.phase2Role().isBlank()) {
            swapTo(round, world, store, 2, boss.phase2Role(), Math.max(0, boss.phase2AddCount()));
        } else if (phase == 2 && frac <= boss.phase3ThresholdFraction()
                && boss.phase3Role() != null && !boss.phase3Role().isBlank()) {
            swapTo(round, world, store, 3, boss.phase3Role(), Math.max(0, boss.phase3AddCount()));
        }
    }

    /** Despawn the current phase entity, spawn the next phase role at the same pos, cue + summon adds. */
    private void swapTo(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store,
                        int newPhase, @Nonnull String roleName, int addCount) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        Vector3d pos = positionOf(store, bossRef);
        if (pos == null) {
            pos = bossSpawnPos(world);
        }
        // Despawn the old phase entity.
        if (bossRef != null && bossRef.isValid()) {
            try {
                store.removeEntity(bossRef, RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // best effort
            }
        }
        Ref<EntityStore> spawned = spawnRole(npc, store, roleName, pos, ArenaLayout.GATE.yaw());
        if (spawned == null) {
            SafeLog.warn("[Kweebec][boss] phase-" + newPhase + " swap spawn failed; boss lost.");
            this.bossRef = null;
            onDefeated(round); // a failed swap should not soft-lock the escape; treat as defeated
            return;
        }
        this.bossRef = spawned;
        this.phase = newPhase;
        String roar = boss.phaseSwapSoundId();
        if (roar != null && !roar.isBlank()) {
            Sound3D.playAt(roar, SoundCategory.SFX, spawned, store, "BOSS_PHASE", false);
        }
        summonAdds(npc, store, world, addCount);
        forEachSurvivor(round, pr -> RoundFeedback.dangerToast(pr, Lang.BOSS_TOAST_PHASE));
        SafeLog.info("[Kweebec][boss] Warden -> phase " + newPhase + " in round " + round.roundId());
    }

    /** Mark the boss defeated, cue the survivors, and tear the HUD + adds down. Idempotent. */
    private void onDefeated(@Nonnull RoundInstance round) {
        if (defeated) {
            return;
        }
        defeated = true;
        World world = round.world();
        if (world != null) {
            despawnAll(world, world.getEntityStore().getStore());
        }
        forEachSurvivor(round, pr -> RoundFeedback.successToast(pr, Lang.BOSS_TOAST_DEFEATED));
        SafeLog.info("[Kweebec][boss] Warden DEFEATED in round " + round.roundId());
    }

    /** True once the boss has been defeated this round. */
    public boolean isDefeated() {
        return defeated;
    }

    // --- adds ---

    /** Summon up to {@code count} Blight adds in a ring around the boss, honoring the boss add-cap. */
    private void summonAdds(@Nonnull NPCPlugin npc, @Nonnull Store<EntityStore> store,
                            @Nonnull World world, int count) {
        String addRole = boss.addRole();
        int cap = boss.addCap();
        if (count <= 0 || addRole == null || addRole.isBlank() || cap <= 0) {
            return;
        }
        Vector3d center = positionOf(store, bossRef);
        if (center == null) {
            center = bossSpawnPos(world);
        }
        int room = Math.max(0, cap - adds.size());
        int toSpawn = Math.min(count, room);
        if (toSpawn <= 0) {
            return;
        }
        int fallbackY = (int) ArenaLayout.STAND_Y;
        List<Vector3d> ring = SpawnPlacement.ringAround(world, center.x(), center.z(),
                ADD_RING_RADIUS, toSpawn, fallbackY);
        for (Vector3d p : ring) {
            Ref<EntityStore> add = spawnRole(npc, store, addRole, p, ArenaLayout.GATE.yaw());
            if (add != null) {
                adds.add(add);
            }
        }
    }

    // --- teardown ---

    /** Despawn the boss + every add and strip the boss HUD from every survivor. Best-effort, idempotent. */
    public void despawnAll(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        if (bossRef != null && bossRef.isValid()) {
            try {
                store.removeEntity(bossRef, RemoveReason.REMOVE);
            } catch (Throwable ignored) {
                // best effort
            }
        }
        bossRef = null;
        for (Ref<EntityStore> add : adds) {
            if (add != null && add.isValid()) {
                try {
                    store.removeEntity(add, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        adds.clear();
        stripHuds(store);
    }

    // --- HUD lifecycle ---

    /** Install the boss HUD for every present survivor (over the round HUD). World-thread only. */
    private void installHuds(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            Ref<EntityStore> ref = survivorRef(st.playerId());
            if (ref == null || !ref.isValid()) {
                continue;
            }
            try {
                Player player = store.getComponent(ref, Player.getComponentType());
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (player == null || playerRef == null) {
                    continue;
                }
                HudManager hud = player.getHudManager();
                hud.addCustomHud(playerRef, new BossHud(playerRef));
                hudPlayers.add(st.playerId());
            } catch (Throwable t) {
                SafeLog.fine("[Kweebec][boss] installHud failed: " + t.getMessage());
            }
        }
    }

    /** Push the boss HP snapshot to every survivor's boss HUD. World-thread only. */
    private void pushHuds(@Nonnull RoundInstance round, @Nonnull HealthSnapshot hp) {
        for (UUID id : hudPlayers) {
            PlayerRef pr = Universe.get().getPlayer(id);
            if (pr == null) {
                continue;
            }
            BossHud bossHud = resolveBossHud(pr);
            if (bossHud != null) {
                try {
                    bossHud.pushHealth(hp.current(), hp.max(), phase, TOTAL_PHASES);
                } catch (Throwable ignored) {
                    // HUD is non-essential
                }
            }
        }
    }

    /** The player's live {@link BossHud} from their HUD manager, or {@code null}. World-thread only. */
    @Nullable
    private BossHud resolveBossHud(@Nonnull PlayerRef pr) {
        try {
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) {
                return null;
            }
            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return null;
            }
            return player.getHudManager().getCustomHud(BossHud.HUD_KEY) instanceof BossHud bh ? bh : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Remove the boss HUD from every survivor it was installed on. Best-effort. World-thread only. */
    private void stripHuds(@Nonnull Store<EntityStore> store) {
        for (UUID id : hudPlayers) {
            PlayerRef pr = Universe.get().getPlayer(id);
            if (pr == null) {
                continue;
            }
            try {
                Ref<EntityStore> ref = pr.getReference();
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    continue;
                }
                player.getHudManager().removeCustomHud(pr, BossHud.HUD_KEY);
            } catch (Throwable t) {
                SafeLog.fine("[Kweebec][boss] stripHud failed: " + t.getMessage());
            }
        }
        hudPlayers.clear();
    }

    // --- helpers ---

    /**
     * Spawn one NPC role at {@code pos} facing {@code yaw}, returning its ref or {@code null} on failure
     * (unregistered role / spawn throw). Mirrors {@code AiHunterController.spawnArchetypeAtPos}. World-thread.
     */
    @Nullable
    private static Ref<EntityStore> spawnRole(@Nonnull NPCPlugin npc, @Nonnull Store<EntityStore> store,
                                              @Nullable String roleName, @Nonnull Vector3d pos, float yaw) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        int roleIndex = npc.getIndex(roleName);
        if (roleIndex < 0) {
            SafeLog.warn("[Kweebec][boss] role '" + roleName + "' not registered; skipped.");
            return null;
        }
        Rotation3f rot = new Rotation3f(0f, yaw, 0f);
        try {
            var spawned = npc.spawnEntity(store, roleIndex, pos, rot, null, (npcEntity, npcRef, st) -> { });
            return spawned != null ? spawned.first() : null;
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][boss] spawn failed (role '" + roleName + "'): " + t.getMessage());
            return null;
        }
    }

    /** Floor-snapped boss spawn position at the Heartwood Gate (barring the escape). World-thread only. */
    @Nonnull
    private static Vector3d bossSpawnPos(@Nonnull World world) {
        Anchor gate = ArenaLayout.GATE;
        return SpawnPlacement.snapToSurface(world, gate.x(), gate.z(), (int) ArenaLayout.STAND_Y);
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

    /**
     * Read the boss entity's Health stat (current + max) via the engine {@link EntityStatMap}, or
     * {@code null} when the entity has no stat map / no "Health" stat (the controller then holds the phase).
     * World-thread only.
     */
    @Nullable
    private static HealthSnapshot readHealth(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        try {
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats == null) {
                return null;
            }
            // Indexed Health lookup (the non-deprecated path): DefaultEntityStatTypes.getHealth() is the
            // engine-assigned "Health" stat index, resolved at asset load.
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health == null) {
                return null;
            }
            return new HealthSnapshot(health.get(), health.getMax());
        } catch (Throwable t) {
            return null;
        }
    }

    private void forEachSurvivor(@Nonnull RoundInstance round, @Nonnull java.util.function.Consumer<PlayerRef> action) {
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

    /** A boss HP snapshot (current + max), with a clamped 0..1 fraction. */
    private record HealthSnapshot(float current, float max) {
        double fraction() {
            return max > 0f ? Math.max(0.0, Math.min(1.0, current / max)) : 0.0;
        }
    }
}
