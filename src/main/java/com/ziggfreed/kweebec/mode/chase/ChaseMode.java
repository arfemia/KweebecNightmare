package com.ziggfreed.kweebec.mode.chase;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.inventory.InventoryUtil;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.atmosphere.MusicBedService;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.death.CocoonService;
import com.ziggfreed.kweebec.feedback.HeartbeatService;
import com.ziggfreed.kweebec.feedback.NightmareHud;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.feedback.ScareDirector;
import com.ziggfreed.kweebec.hunter.HunterController;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.PlayerResync;
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

    /**
     * Build chase state for a freshly-spawned round. Pure state; no world work. The shrine layout is
     * seeded off {@code round.worldSeed()} - the SAME per-round world seed the terrain and the
     * corrupted-structure ruins derive from - so the whole round is internally coherent and varies per
     * round. The parent guarantees {@code worldSeed} is set (in {@code RoundService.onInstanceReady},
     * read back from the instance world's {@code getWorldConfig().getSeed()}) BEFORE this runs.
     */
    public static void onStart(@Nonnull RoundInstance round) {
        ChaseState chase = new ChaseState(round.ruleSet().shrineCount(round.partySize()),
                round.ruleSet().caveShrineCount(), round.worldSeed());
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
            maybeRespawnMoonbloom(round, world, chase);
            HunterController hunter = round.hunterController();
            if (hunter != null) {
                hunter.tick(round, world, store);
            }
            // Per-survivor horror conductor: proximity vignette (hysteresis) + jumpscare +
            // whisper layer + tier music escalation. Best-effort; never throws into the loop.
            ScareDirector.tick(round, world, store);
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
            // Normalize each survivor to Adventure ONCE on first arrival so the
            // hunter's marked target can lock on (the engine only honors a marked
            // PLAYER target in Adventure mode). Applied once, never re-asserted each
            // tick, so an admin can switch to Creative to debug without it snapping
            // back a second later. Retried next tick if the component is not ready.
            Player p = null;
            try {
                p = store.getComponent(ref, Player.getComponentType());
            } catch (Throwable ignored) {
                // best effort - retried next tick if the component is not ready
            }
            if (p != null) {
                if (!st.isGameModeApplied()) {
                    try {
                        if (p.getGameMode() != GameMode.Adventure) {
                            Player.setGameMode(ref, GameMode.Adventure, store);
                        }
                        st.setGameModeApplied(true);
                    } catch (Throwable ignored) {
                        // best effort
                    }
                }
                // The engine never resyncs position on a game-mode switch (setGameMode
                // sends only a SetGameMode packet). Flying in Creative then dropping back
                // to Adventure therefore leaves the client drifted from the server - mobs
                // hit you while visually attacking empty air, and blocks read as too far to
                // break. Snap the client back the instant they return to Adventure. We do
                // NOT force the game mode (an admin may stay in Creative to debug); we only
                // heal the Creative -> Adventure edge.
                GameMode current = p.getGameMode();
                if (st.lastGameMode() == GameMode.Creative && current == GameMode.Adventure) {
                    PlayerResync.resync(ref, store);
                }
                st.setLastGameMode(current);
            }
            // Install the HUD once.
            if (round.hud(uuid) == null) {
                NightmareHud hud = RoundFeedback.installHud(store, ref);
                if (hud != null) {
                    round.putHud(uuid, hud);
                }
            }
            // Force the dread music bed once on first confirmed arrival. The engine
            // ForcedMusicTracker is ensured by now (the player has fully joined this
            // world), so the set is picked up by the audio tick; a pre-join one-shot
            // raced the async teleport and was clobbered.
            if (!st.isMusicApplied()) {
                if (MusicBedService.applyFor(store, ref)) {
                    st.setMusicApplied(true);
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

    /**
     * The shrine CLEANSE (1.4.0 Moonbloom loop, pure-swap). A shrine is no longer relit by a
     * hold-in-place channel timer; an active survivor standing within {@link ArenaLayout#INTERACT_RADIUS}
     * who holds at least {@code cleanseCost} gathered Moonbloom charges SPENDS them and the shrine
     * lights instantly. The cost is a configurable rule-set knob ({@link RuleSet#cleanseCost()}); a
     * configured cost of 0 cleanses on proximity alone (the degenerate, supply-free dial). A survivor
     * who is at the shrine but short on Moonbloom is nudged once per approach to go gather more.
     *
     * <p>{@code feedbackStage} is reused as a per-approach "already nudged" flag (reset when the
     * survivor steps away) so the "gather Moonbloom" cue does not spam every tick. The old channel
     * {@code progress}/{@code channeller} fields are vestigial under pure-swap; the hunter no longer
     * draws toward a channeller (there is none) and falls back to nearest-survivor pursuit.
     */
    private static void handleShrines(@Nonnull RoundInstance round, @Nonnull World world,
                                      @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        int cleanseCost = round.ruleSet().cleanseCost();
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (ShrineState shrine : chase.shrines()) {
            if (shrine.isLit()) {
                continue;
            }
            UUID cleanser = activeSurvivorNear(round, store, worldUuid, shrine.anchor(), ArenaLayout.INTERACT_RADIUS_SQ);
            if (cleanser == null) {
                shrine.setFeedbackStage(0); // stepped away; re-arm the "need Moonbloom" nudge
                continue;
            }
            Ref<EntityStore> ref = presentRef(cleanser, worldUuid);
            boolean cleansed = cleanseCost <= 0
                    || (ref != null && InventoryUtil.spend(store, ref, Moonbloom.CHARGE_ITEM, cleanseCost));
            if (cleansed) {
                shrine.setLit(true);
                chase.addCorruption(round.ruleSet().corruptionPerShrine());
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][win] shrine " + shrine.index() + " CLEANSED ("
                                + chase.litShrines() + "/" + chase.totalShrines() + ")");
                forEachPresent(round, pr -> RoundFeedback.successToast(pr, Lang.TOAST_CLEANSE_DONE));
            } else if (shrine.feedbackStage() < 1) {
                // At the shrine but short on Moonbloom: nudge to gather, once per approach.
                shrine.setFeedbackStage(1);
                toastTo(cleanser, pr -> RoundFeedback.warningToast(pr, Lang.TOAST_CLEANSE_NEED));
            }
        }
    }

    /**
     * Mid-match Moonbloom respawn: at each configured wave time the grove regrows a fresh batch (one
     * plant at every still-unlit shrine plus a scattered batch), so a charge-starved party can keep
     * cleansing. Fires every wave whose time has elapsed (catches up if ticks were skipped), tracked by
     * {@link ChaseState#moonbloomRespawnsFired()} so each wave fires once. World-thread only.
     */
    private static void maybeRespawnMoonbloom(@Nonnull RoundInstance round, @Nonnull World world,
                                              @Nonnull ChaseState chase) {
        int respawnCount = round.ruleSet().moonbloomRespawnCount();
        int[] times = round.ruleSet().moonbloomRespawnAtSeconds();
        if (respawnCount <= 0 || times.length == 0) {
            return;
        }
        int elapsed = round.durationSeconds();
        while (chase.moonbloomRespawnsFired() < times.length
                && elapsed >= times[chase.moonbloomRespawnsFired()]) {
            long wave = chase.moonbloomRespawnsFired() + 1L;
            ArenaBuilder.plantMoonbloom(round, world, 1, respawnCount, wave);
            chase.incrementMoonbloomRespawnsFired();
            forEachPresent(round, pr -> RoundFeedback.warningToast(pr, Lang.TOAST_MOONBLOOM_RESPAWN));
        }
    }

    /** Run an action for one player by UUID if they are online (used for channeller-only cues). */
    private static void toastTo(@Nonnull UUID uuid, @Nonnull java.util.function.Consumer<PlayerRef> action) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null) {
            action.accept(pr);
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
                    new Anchor(downedPos.x(), downedPos.y(), downedPos.z()), ArenaLayout.INTERACT_RADIUS_SQ);
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
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][win] all shrines lit (" + chase.litShrines() + "/" + chase.totalShrines()
                        + "); GATE OPEN, phase -> ESCAPE. Reach ESCAPE z=" + ArenaLayout.ESCAPE.z()
                        + " within " + ArenaLayout.ESCAPE_RADIUS + " to win.");
        // The Heartwood Gate did NOT exist during the round; reveal it now (the dramatic "the gate
        // opens" beat). The escape win is pure-anchor logic (checkEscapes crossing GATE.z), so this
        // prefab is purely cosmetic - the win works with or without it.
        ArenaBuilder.pasteGate(world);
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
        double nearestSq = Double.MAX_VALUE;
        UUID nearest = null;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = presentRef(st.playerId(), worldUuid);
            Vector3d pos = positionOf(store, ref);
            if (pos == null) {
                continue;
            }
            double distSq = ArenaLayout.ESCAPE.horizontalDistanceSq(pos.x(), pos.z());
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = st.playerId();
            }
            // Win on EITHER reaching the exit pad OR simply crossing north past the Heartwood Gate
            // (z below the gate). The second is the robust trigger: once a survivor is through the
            // open gate they have escaped, even if the distant exit pad is hard to reach in the dark.
            boolean pastGate = pos.z() <= ArenaLayout.GATE.z() - 2.0;
            if (distSq <= ArenaLayout.ESCAPE_RADIUS_SQ || pastGate) {
                st.setEscaped(true);
                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec][win] survivor " + shortId(st.playerId()) + " ESCAPED (distSq="
                                + fmt(distSq) + ", z=" + fmt(pos.z()) + ", pastGate=" + pastGate + ")");
            }
        }
        // Diagnostic: while the gate is open, log how close the nearest survivor is to the exit
        // so a "reached the exit but no win" report is decisively explained next playtest.
        if (nearest != null && nearestSq > ArenaLayout.ESCAPE_RADIUS_SQ) {
            KweebecNightmarePlugin.LOGGER.atInfo().log(
                    "[Kweebec][win] ESCAPE phase: nearest survivor " + shortId(nearest)
                            + " distSq=" + fmt(nearestSq) + " (need <= " + ArenaLayout.ESCAPE_RADIUS_SQ + ")");
        }
    }

    private static String shortId(@Nonnull UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
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

    /**
     * Vertical tolerance (blocks) for the proximity test against a SUB-SURFACE anchor (the cave
     * shrine). Surface anchors keep their original XZ-only behavior; an underground anchor also
     * requires the player to be within this band of the anchor's Y, so the cave shrine can only be
     * channelled from inside the chamber - never from the surface directly above it (the XZ-only
     * {@code horizontalDistanceSq} would otherwise relight it from the surface).
     */
    private static final double UNDERGROUND_Y_BAND = 3.0;

    @Nullable
    private static UUID activeSurvivorNear(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store,
                                           @Nonnull UUID worldUuid, @Nonnull Anchor at, double radiusSq) {
        // Only constrain Y for a sub-surface anchor; surface anchors (ring shrines, rescues) keep
        // their exact XZ-only proximity so this change cannot regress them.
        boolean underground = at.y() < ArenaLayout.STAND_Y - 1.0;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Ref<EntityStore> ref = presentRef(st.playerId(), worldUuid);
            Vector3d pos = positionOf(store, ref);
            if (pos == null) {
                continue;
            }
            if (at.horizontalDistanceSq(pos.x(), pos.z()) <= radiusSq
                    && (!underground || Math.abs(pos.y() - at.y()) <= UNDERGROUND_Y_BAND)) {
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
