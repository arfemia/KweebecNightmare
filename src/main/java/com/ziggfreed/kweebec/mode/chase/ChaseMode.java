package com.ziggfreed.kweebec.mode.chase;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.death.CocoonService;
import com.ziggfreed.kweebec.feedback.HeartbeatService;
import com.ziggfreed.kweebec.feedback.NightmareHud;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.hunter.HunterController;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * The Chase round loop ("Relight & Escape"), driven once per second from the
 * state machine on the instance world thread. PREP gives the party a breath, then
 * RITUAL spawns the hunter and the heartbeat while survivors channel the grove
 * shrines (corruption climbs with time + per shrine). Lighting the last shrine
 * opens the Heartwood Gate and locks the hunter onto the nearest survivor; anyone
 * reaching the exit wins, all-cocooned or the cap loses.
 *
 * <p>Stateless: all per-round state lives on {@link RoundInstance}/{@link ChaseState}.
 */
public final class ChaseMode {

    private static final int PREP_SECONDS = 15;
    private static final double RESCUE_SECONDS = 3.0;
    /** Ticks (1 Hz) a player who already entered can be absent before being dropped (disconnect). */
    private static final int DISCONNECT_GRACE_TICKS = 5;
    /** Ticks a player who never arrived (still teleporting) is given before being dropped. */
    private static final int ARRIVAL_GRACE_TICKS = 60;

    private ChaseMode() {
    }

    /** Build chase state for a freshly-spawned round. Pure state; no world work. */
    public static void onStart(@Nonnull RoundInstance round) {
        ChaseState chase = new ChaseState(round.ruleSet().shrineCount(round.partySize()));
        chase.setPhase(ChasePhase.PREP);
        chase.setPrepEndsAtMs(System.currentTimeMillis() + PREP_SECONDS * 1000L);
        round.setChaseState(chase);
    }

    /**
     * One tick (1 Hz) on the instance world thread. Returns the resolving outcome,
     * or {@code null} to keep the round running.
     */
    @Nullable
    public static RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round,
                                                   @Nonnull World world, @Nonnull Store<EntityStore> store) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return null;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        long now = System.currentTimeMillis();

        lazyPlayerSetup(round, world, store, worldUuid);

        // Abandoned round - everyone left.
        if (round.presentCount() == 0 && round.partySize() > 0
                && now - round.startedAtMs() > 3000L) {
            return RoundCompletedEvent.Outcome.ABORTED;
        }

        // Phase transition: PREP -> RITUAL.
        if (chase.phase() == ChasePhase.PREP && now >= chase.prepEndsAtMs()) {
            beginRitual(round, world, store);
        }

        if (chase.phase() != ChasePhase.PREP) {
            chase.addCorruption(round.ruleSet().corruptionPerSecond());
            handleShrines(round, world, store, chase);
            HunterController hunter = round.hunterController();
            if (hunter != null) {
                hunter.tick(round, world, store);
            }
        }

        handleRescues(round, world, store);

        // Gate / escape.
        if (chase.phase() == ChasePhase.RITUAL && chase.allShrinesLit() && !chase.isGateOpen()) {
            openGate(round, world, store, chase);
        }
        if (chase.phase() == ChasePhase.ESCAPE) {
            checkEscapes(round, world, store);
        }

        pushHuds(round, world, store, chase);

        return resolveOutcome(round);
    }

    // --- per-player lazy setup (players arrive asynchronously after teleport) ---

    private static void lazyPlayerSetup(@Nonnull RoundInstance round, @Nonnull World world,
                                        @Nonnull Store<EntityStore> store, @Nonnull UUID worldUuid) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            UUID uuid = st.playerId();
            Ref<EntityStore> ref = presentRef(uuid, worldUuid);
            if (ref == null || !ref.isValid()) {
                // The engine fires no disconnect event we hook, so detect a vanished
                // player by absence: a longer grace for one still teleporting in (HUD
                // not yet installed), a short grace for one who entered then dropped.
                boolean entered = round.hud(uuid) != null;
                int threshold = entered ? DISCONNECT_GRACE_TICKS : ARRIVAL_GRACE_TICKS;
                if (st.incrementMissedTicks() >= threshold) {
                    round.markLeft(uuid);
                    RoundService.getInstance().registry().unbindPlayer(uuid);
                    round.removeHud(uuid);
                }
                continue;
            }
            st.resetMissedTicks();
            // Keep survivors in Adventure so the hunter's marked target survives.
            try {
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null && p.getGameMode() != GameMode.Adventure) {
                    Player.setGameMode(ref, GameMode.Adventure, store);
                }
            } catch (Throwable ignored) {
                // best effort
            }
            // Install the HUD once.
            if (round.hud(uuid) == null) {
                NightmareHud hud = RoundFeedback.installHud(store, ref);
                if (hud != null) {
                    round.putHud(uuid, hud);
                }
            }
        }
    }

    private static void beginRitual(@Nonnull RoundInstance round, @Nonnull World world,
                                    @Nonnull Store<EntityStore> store) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        chase.setPhase(ChasePhase.RITUAL);
        HunterController hunter = round.hunterController();
        if (hunter != null) {
            hunter.spawn(round, world, store);
        }
        HeartbeatService.start(round);
        forEachPresent(round, pr -> RoundFeedback.title(pr,
                Lang.TITLE_HUNT_BEGINS, Lang.TITLE_HUNT_BEGINS_SUB, true));
    }

    // --- shrines ---

    private static void handleShrines(@Nonnull RoundInstance round, @Nonnull World world,
                                      @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        double perTick = 1.0 / Math.max(1.0, round.ruleSet().shrineRelightSeconds());
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (ShrineState shrine : chase.shrines()) {
            if (shrine.isLit()) {
                continue;
            }
            UUID channeller = activeSurvivorNear(round, store, worldUuid, shrine.anchor(), ArenaLayout.INTERACT_RADIUS_SQ);
            if (channeller != null) {
                shrine.setChanneller(channeller);
                shrine.setProgress(shrine.progress() + perTick);
                if (shrine.progress() >= 1.0) {
                    shrine.setLit(true);
                    chase.addCorruption(round.ruleSet().corruptionPerShrine());
                    forEachPresent(round, pr -> RoundFeedback.successToast(pr, Lang.TOAST_SHRINE_LIT));
                }
            } else {
                shrine.setChanneller(null);
                shrine.setProgress(shrine.progress() - perTick * 0.5);
            }
        }
    }

    // --- rescues ---

    private static void handleRescues(@Nonnull RoundInstance round, @Nonnull World world,
                                      @Nonnull Store<EntityStore> store) {
        double perTick = 1.0 / RESCUE_SECONDS;
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (PlayerRoundState st : round.playerStates()) {
            if (!CocoonService.canRescue(round, st)) {
                st.setRescueProgress(0.0);
                continue;
            }
            Ref<EntityStore> downedRef = presentRef(st.playerId(), worldUuid);
            Vector3d downedPos = positionOf(store, downedRef);
            if (downedPos == null) {
                st.setRescueProgress(0.0);
                continue;
            }
            UUID rescuer = activeSurvivorNear(round, store, worldUuid,
                    new Anchor(downedPos.x, downedPos.y, downedPos.z), ArenaLayout.INTERACT_RADIUS_SQ);
            if (rescuer != null && !rescuer.equals(st.playerId())) {
                st.addRescueProgress(perTick);
                if (st.rescueProgress() >= 1.0) {
                    st.setRescueProgress(0.0);
                    CocoonService.revive(round, st.playerId(), rescuer, world, store);
                }
            } else {
                st.setRescueProgress(0.0);
            }
        }
    }

    // --- gate + escape ---

    private static void openGate(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        chase.setGateOpen(true);
        chase.setPhase(ChasePhase.ESCAPE);
        chase.setAlertFired(true);
        chase.setCorruption(1.0);
        HunterController hunter = round.hunterController();
        if (hunter != null) {
            hunter.onAlert(round, world, store);
        }
        forEachPresent(round, pr -> {
            RoundFeedback.title(pr, Lang.TITLE_GATE_OPEN, Lang.TITLE_GATE_OPEN_SUB, true);
            RoundFeedback.dangerToast(pr, Lang.TOAST_HUNTER_LOCKED);
        });
    }

    private static void checkEscapes(@Nonnull RoundInstance round, @Nonnull World world,
                                     @Nonnull Store<EntityStore> store) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = presentRef(st.playerId(), worldUuid);
            Vector3d pos = positionOf(store, ref);
            if (pos == null) {
                continue;
            }
            if (ArenaLayout.ESCAPE.horizontalDistanceSq(pos.x, pos.z) <= ArenaLayout.ESCAPE_RADIUS_SQ) {
                st.setEscaped(true);
            }
        }
    }

    // --- HUD ---

    private static void pushHuds(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        int remaining = round.ruleSet().roundCapSeconds() - round.durationSeconds();
        int lit = chase.litShrines();
        int total = chase.totalShrines();
        int alive = round.activeCount();
        double corruption = chase.corruption();
        ChasePhase phase = chase.phase();
        for (PlayerRoundState st : round.playerStates()) {
            Object hud = round.hud(st.playerId());
            if (hud instanceof NightmareHud nh) {
                try {
                    nh.pushState(remaining, phase, lit, total, alive, corruption);
                } catch (Throwable ignored) {
                    // HUD is non-essential
                }
            }
        }
    }

    // --- win/lose ---

    @Nullable
    private static RoundCompletedEvent.Outcome resolveOutcome(@Nonnull RoundInstance round) {
        if (round.anyEscaped()) {
            return RoundCompletedEvent.Outcome.ESCAPED;
        }
        if (round.durationSeconds() >= round.ruleSet().roundCapSeconds()) {
            return RoundCompletedEvent.Outcome.TIMED_OUT;
        }
        // Everyone present is cocooned (and none can still be rescued is implied by no active).
        if (round.presentCount() > 0 && round.activeCount() == 0) {
            return RoundCompletedEvent.Outcome.CAUGHT;
        }
        return null;
    }

    // --- helpers ---

    private static void forEachPresent(@Nonnull RoundInstance round, @Nonnull java.util.function.Consumer<PlayerRef> action) {
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

    @Nullable
    private static UUID activeSurvivorNear(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store,
                                           @Nonnull UUID worldUuid, @Nonnull Anchor at, double radiusSq) {
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = presentRef(st.playerId(), worldUuid);
            Vector3d pos = positionOf(store, ref);
            if (pos == null) {
                continue;
            }
            if (at.horizontalDistanceSq(pos.x, pos.z) <= radiusSq) {
                return st.playerId();
            }
        }
        return null;
    }

    /** The player's entity ref if they are present in THIS instance world, else null. */
    @Nullable
    private static Ref<EntityStore> presentRef(@Nonnull UUID uuid, @Nonnull UUID worldUuid) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr == null || !worldUuid.equals(pr.getWorldUuid())) {
            return null;
        }
        return pr.getReference();
    }

    @Nullable
    private static Vector3d positionOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        return tc == null ? null : tc.getPosition();
    }
}
