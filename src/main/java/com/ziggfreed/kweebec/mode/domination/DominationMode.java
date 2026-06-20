package com.ziggfreed.kweebec.mode.domination;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.worldmap.WorldMapMarkers;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.feedback.DominationHud;
import com.ziggfreed.kweebec.feedback.DominationHudSnapshot;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.mode.clash.ClashModelSwapper;
import com.ziggfreed.kweebec.mode.clash.ClashTeamAssigner;
import com.ziggfreed.kweebec.round.DominationConfig;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundModeSupport;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The DOMINATION (control-point PvP) round loop, reusing the Clash team / model-swap / PvP-damage / respawn
 * scaffolding. Each tick it measures per-team occupancy of every capture point, feeds the non-latching
 * {@code ControlPointTracker}, accrues score to the controlling team, and resolves when a team reaches the
 * score cap (or the timer expires). Always-respawn (no elimination).
 */
public final class DominationMode {

    private static final int DISCONNECT_GRACE_TICKS = 5;
    private static final int ARRIVAL_GRACE_TICKS = 60;
    private static final String MARKER_PREFIX = "kn_dom_";
    private static final String MARKER_ICON = "Portal.png";
    private static final double MARKER_Y = 80.0;

    private DominationMode() {
    }

    public static void onStart(@Nonnull RoundInstance round, @Nonnull World world,
                               @Nonnull Store<EntityStore> store) {
        DominationState ds = new DominationState(round);
        round.setModeState(ds);
        ClashTeamAssigner.assign(round);
        try {
            world.getWorldConfig().setPvpEnabled(true);
            world.getWorldConfig().markChanged();
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] domination setPvpEnabled failed: " + t.getMessage());
        }
        // Place a worldmap POI per capture point (the compass must be on to render them).
        try {
            world.setCompassUpdating(true);
            for (DominationState.Point p : ds.points()) {
                WorldMapMarkers.place(world, MARKER_PREFIX + p.id, p.x, MARKER_Y, p.z,
                        MARKER_ICON, Lang.msg(Lang.DOM_MARKER_POINT));
                p.markerPlaced = true;
            }
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] domination markers failed: " + t.getMessage());
        }
        RoundModeSupport.forEachPresent(round, pr ->
                RoundFeedback.title(pr, Lang.CLASH_TITLE_START, Lang.CLASH_TITLE_START_SUB, true));
    }

    @Nullable
    public static RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                                   @Nonnull Store<EntityStore> store) {
        DominationState ds = (DominationState) round.modeState();
        if (ds == null) {
            return null;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        long now = System.currentTimeMillis();

        lazyPlayerSetup(round, world, store, ds, worldUuid);

        if (round.presentCount() == 0 && round.partySize() > 0 && now - round.startedAtMs() > 3000L) {
            return RoundCompletedEvent.Outcome.ABORTED;
        }

        handleRespawns(round, world, store, ds, now);
        ds.setMushroomWaves(com.ziggfreed.kweebec.mode.clash.ClashPickupSpawner.tick(
                round, world, ds.mushroomWavesFired()));
        updatePoints(round, store, ds, worldUuid, now);
        pushHuds(round, ds);

        return resolveOutcome(round, ds);
    }

    private static void lazyPlayerSetup(@Nonnull RoundInstance round, @Nonnull World world,
                                        @Nonnull Store<EntityStore> store, @Nonnull DominationState ds,
                                        @Nonnull UUID worldUuid) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            UUID uuid = st.playerId();
            DominationState.PlayerDom pd = ds.get(uuid);
            Ref<EntityStore> ref = RoundModeSupport.presentRef(uuid, worldUuid);
            if (ref == null || !ref.isValid()) {
                int threshold = pd.spawnPlaced ? DISCONNECT_GRACE_TICKS : ARRIVAL_GRACE_TICKS;
                if (st.incrementMissedTicks() >= threshold) {
                    round.markLeft(uuid);
                    com.ziggfreed.kweebec.round.RoundService.getInstance().registry().unbindPlayer(uuid);
                }
                continue;
            }
            st.resetMissedTicks();
            Player p = null;
            try {
                p = store.getComponent(ref, Player.getComponentType());
            } catch (Throwable ignored) {
                // retried next tick
            }
            if (p != null && !pd.gameModeApplied) {
                try {
                    if (p.getGameMode() != GameMode.Adventure) {
                        Player.setGameMode(ref, GameMode.Adventure, store);
                    }
                    pd.gameModeApplied = true;
                } catch (Throwable ignored) {
                    // best effort
                }
            }
            if (!pd.modelApplied) {
                ClashModelSwapper.apply(ref, store, uuid,
                        round.ruleSet().modelSwapId(), round.ruleSet().modelSwapScale());
                pd.modelApplied = true;
            }
            if (!pd.spawnPlaced) {
                RoundModeSupport.teleportToTeamSpawn(round, ref, store, uuid);
                pd.spawnPlaced = true;
            }
            // Install the Domination HUD once (strips the native HUD to the kept set); pushState each tick after.
            if (round.hud(uuid) == null) {
                DominationHud hud = RoundFeedback.installCustomHud(store, ref, DominationHud::new);
                if (hud != null) {
                    round.putHud(uuid, hud);
                }
            }
        }
    }

    // --- HUD ---

    /**
     * Build + push one {@link DominationHudSnapshot} per present player, from THEIR team's perspective (their
     * points first, in their team colour). The capture readout is the leading in-flight capture progress
     * across all points (the most contested point), shared by both teams. Best-effort; non-essential.
     */
    private static void pushHuds(@Nonnull RoundInstance round, @Nonnull DominationState ds) {
        int remaining = round.ruleSet().roundCapSeconds() - round.durationSeconds();
        int capturePct = leadingCapturePct(ds);
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound() || !(round.hud(st.playerId()) instanceof DominationHud hud)) {
                continue;
            }
            int team = round.teamOf(st.playerId());
            if (team < 0) {
                continue;
            }
            int enemy = team == 0 ? 1 : 0;
            DominationHudSnapshot snap = new DominationHudSnapshot(remaining, team,
                    ds.teamScore(team), ds.teamScore(enemy), capturePct);
            try {
                hud.pushState(snap);
            } catch (Throwable ignored) {
                // HUD is non-essential
            }
        }
    }

    /** The highest in-flight capture progress (0..100) across all points - the most contested point. */
    private static int leadingCapturePct(@Nonnull DominationState ds) {
        double best = 0.0;
        for (DominationState.Point p : ds.points()) {
            best = Math.max(best, p.tracker.captureProgress());
        }
        return (int) Math.round(Math.max(0.0, Math.min(1.0, best)) * 100.0);
    }

    /** A Domination player died: schedule an always-on respawn at their team spawn. */
    public static void onPlayerDeath(@Nonnull RoundInstance round, @Nonnull UUID uuid,
                                     @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> cb) {
        DominationState ds = (DominationState) round.modeState();
        if (ds == null) {
            return;
        }
        ds.get(uuid).respawnAtMs = System.currentTimeMillis()
                + round.ruleSet().domination().respawnDelaySeconds() * 1000L;
    }

    private static void handleRespawns(@Nonnull RoundInstance round, @Nonnull World world,
                                       @Nonnull Store<EntityStore> store, @Nonnull DominationState ds, long now) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        for (PlayerRoundState st : round.playerStates()) {
            DominationState.PlayerDom pd = ds.get(st.playerId());
            if (pd.respawnAtMs <= 0L || now < pd.respawnAtMs) {
                continue;
            }
            pd.respawnAtMs = 0L;
            RoundModeSupport.respawnAtTeamSpawn(round, world, store, st.playerId(), worldUuid);
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr != null) {
                RoundFeedback.warningToast(pr, Lang.CLASH_TOAST_RESPAWN);
            }
        }
    }

    /** Per-tick: measure per-team occupancy of each point, drive its tracker, accrue score to the controller. */
    private static void updatePoints(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store,
                                     @Nonnull DominationState ds, @Nonnull UUID worldUuid, long now) {
        DominationConfig dc = round.ruleSet().domination();
        for (DominationState.Point point : ds.points()) {
            Map<String, Integer> occ = new HashMap<>();
            for (PlayerRoundState st : round.playerStates()) {
                if (st.hasLeftRound()) {
                    continue;
                }
                int team = round.teamOf(st.playerId());
                if (team < 0) {
                    continue;
                }
                Ref<EntityStore> ref = RoundModeSupport.presentRef(st.playerId(), worldUuid);
                Vector3d pos = RoundModeSupport.positionOf(store, ref);
                if (pos == null) {
                    continue;
                }
                double dx = pos.x() - point.x;
                double dz = pos.z() - point.z;
                if (dx * dx + dz * dz <= point.radius * point.radius) {
                    occ.merge(String.valueOf(team), 1, Integer::sum);
                }
            }
            point.tracker.update(occ, dc.contestRule(), now);
            String controller = point.tracker.controllingTeam();
            // Control-change cue (best-effort).
            if (controller != null && !controller.equals(point.controllerTeam)) {
                int team = parseTeam(controller);
                notifyControlChange(round, team);
            }
            point.controllerTeam = controller;
            // Accrue score per held point per tick-second to the controlling team.
            if (controller != null) {
                ds.addTeamScore(parseTeam(controller), Math.max(1, dc.accrualPerSecond()));
            }
        }
    }

    @Nullable
    private static RoundCompletedEvent.Outcome resolveOutcome(@Nonnull RoundInstance round,
                                                              @Nonnull DominationState ds) {
        DominationConfig dc = round.ruleSet().domination();
        int cap = Math.max(1, dc.scoreToWin());
        for (int t = 0; t < DominationState.TEAM_COUNT; t++) {
            if (ds.teamScore(t) >= cap) {
                ds.setWinningTeam(t);
                return RoundCompletedEvent.Outcome.TEAM_ELIMINATED;
            }
        }
        if (round.durationSeconds() >= round.ruleSet().roundCapSeconds()) {
            int s0 = ds.teamScore(0);
            int s1 = ds.teamScore(1);
            if (s0 == s1) {
                ds.setWinningTeam(-1);
                return RoundCompletedEvent.Outcome.DRAW;
            }
            ds.setWinningTeam(s0 > s1 ? 0 : 1);
            return RoundCompletedEvent.Outcome.TEAM_ELIMINATED;
        }
        return null;
    }

    private static int parseTeam(@Nonnull String teamId) {
        try {
            return Integer.parseInt(teamId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void notifyControlChange(@Nonnull RoundInstance round, int capturingTeam) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr == null) {
                continue;
            }
            boolean mine = round.teamOf(st.playerId()) == capturingTeam;
            RoundFeedback.warningToast(pr, mine ? Lang.CLASH_TOAST_POINT_CAPTURED : Lang.CLASH_TOAST_POINT_LOST);
        }
    }
}
