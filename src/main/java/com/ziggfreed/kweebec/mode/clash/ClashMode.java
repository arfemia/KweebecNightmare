package com.ziggfreed.kweebec.mode.clash;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.match.MatchRules;
import com.ziggfreed.common.instance.match.MatchSnapshot;
import com.ziggfreed.common.instance.match.MatchVerdict;
import com.ziggfreed.common.instance.match.WinConditionResolver;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.feedback.ClashHud;
import com.ziggfreed.kweebec.feedback.ClashHudSnapshot;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.ClashConfig;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RespawnPolicy;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundModeSupport;
import com.ziggfreed.kweebec.round.WinCondition;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The CLASH (team PvP brawl) round loop, driven 1 Hz on the instance world thread (stateless; all state on
 * {@link RoundInstance#modeState()} = {@link ClashState}). On entry each player is normalized to Adventure,
 * reskinned (model swap), and placed at their team spawn. Deaths credit a kill to the last enemy attacker
 * and either eliminate (no respawn) or schedule a respawn at the team spawn. Each tick the configured
 * {@link WinCondition} is evaluated through the generic {@link WinConditionResolver}.
 */
public final class ClashMode {

    private static final int DISCONNECT_GRACE_TICKS = 5;
    private static final int ARRIVAL_GRACE_TICKS = 60;
    /** Ticks for a respawn (DeathComponent removal) to settle before the team-spawn teleport. */
    private static final long RESPAWN_SETTLE_MS = 500L;

    private ClashMode() {
    }

    public static void onStart(@Nonnull RoundInstance round, @Nonnull World world,
                               @Nonnull Store<EntityStore> store) {
        ClashState cs = new ClashState();
        round.setModeState(cs);
        ClashTeamAssigner.assign(round);
        // Enable player-vs-player damage in this instance at runtime (the default world flag is off).
        try {
            world.getWorldConfig().setPvpEnabled(true);
            world.getWorldConfig().markChanged();
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] clash setPvpEnabled failed: " + t.getMessage());
        }
        RoundModeSupport.forEachPresent(round, pr ->
                RoundFeedback.title(pr, Lang.CLASH_TITLE_START, Lang.CLASH_TITLE_START_SUB, true));
    }

    @Nullable
    public static RoundCompletedEvent.Outcome tick(@Nonnull RoundInstance round, @Nonnull World world,
                                                   @Nonnull Store<EntityStore> store) {
        ClashState cs = (ClashState) round.modeState();
        if (cs == null) {
            return null;
        }
        UUID worldUuid = world.getWorldConfig().getUuid();
        long now = System.currentTimeMillis();

        lazyPlayerSetup(round, world, store, cs, worldUuid);

        // Abandoned round - everyone left.
        if (round.presentCount() == 0 && round.partySize() > 0 && now - round.startedAtMs() > 3000L) {
            return RoundCompletedEvent.Outcome.ABORTED;
        }

        handleRespawns(round, world, store, cs, worldUuid, now);
        cs.setMushroomWaves(ClashPickupSpawner.tick(round, world, cs.mushroomWavesFired()));
        pushHuds(round, cs);

        return resolveOutcome(round, cs);
    }

    // --- per-player arrival (async after teleport) ---

    private static void lazyPlayerSetup(@Nonnull RoundInstance round, @Nonnull World world,
                                        @Nonnull Store<EntityStore> store, @Nonnull ClashState cs,
                                        @Nonnull UUID worldUuid) {
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            UUID uuid = st.playerId();
            ClashState.PlayerClash pc = cs.get(uuid);
            Ref<EntityStore> ref = RoundModeSupport.presentRef(uuid, worldUuid);
            if (ref == null || !ref.isValid()) {
                int threshold = pc.spawnPlaced ? DISCONNECT_GRACE_TICKS : ARRIVAL_GRACE_TICKS;
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
            if (p != null && !pc.gameModeApplied) {
                try {
                    if (p.getGameMode() != GameMode.Adventure) {
                        Player.setGameMode(ref, GameMode.Adventure, store);
                    }
                    pc.gameModeApplied = true;
                } catch (Throwable ignored) {
                    // best effort
                }
            }
            if (!pc.modelApplied) {
                ClashModelSwapper.apply(ref, store, uuid,
                        round.ruleSet().modelSwapId(), round.ruleSet().modelSwapScale());
                pc.modelApplied = true;
            }
            if (!pc.spawnPlaced) {
                RoundModeSupport.teleportToTeamSpawn(round, ref, store, uuid);
                pc.spawnPlaced = true;
            }
            // Install the Clash HUD once (strips the native HUD to the kept set); pushState each tick after.
            if (round.hud(uuid) == null) {
                ClashHud hud = RoundFeedback.installCustomHud(store, ref, ClashHud::new);
                if (hud != null) {
                    round.putHud(uuid, hud);
                }
            }
        }
    }

    // --- HUD ---

    /**
     * Build + push one {@link ClashHudSnapshot} per present player. Each player sees the score from THEIR
     * team's perspective (their score first, in their team colour), so the snapshot is computed per viewer.
     * Best-effort; the HUD is non-essential.
     */
    private static void pushHuds(@Nonnull RoundInstance round, @Nonnull ClashState cs) {
        WinCondition wc = round.ruleSet().clash().winCondition();
        boolean byKills = wc == WinCondition.MOST_KILLS || wc == WinCondition.TDM_SCORE_TO_KILLS;
        int[] scores = cs.scoresFor(round, wc);
        int[] alive = cs.eligibleCounts(round);
        int remaining = round.ruleSet().roundCapSeconds() - round.durationSeconds();
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound() || !(round.hud(st.playerId()) instanceof ClashHud hud)) {
                continue;
            }
            UUID uuid = st.playerId();
            int team = round.teamOf(uuid);
            if (team < 0) {
                continue;
            }
            int enemy = team == 0 ? 1 : 0;
            ClashState.PlayerClash pc = cs.get(uuid);
            int tally = byKills ? pc.kills : pc.hits;
            ClashHudSnapshot snap = new ClashHudSnapshot(remaining, team,
                    scores[team], scores[enemy], tally, byKills, alive[team], alive[enemy]);
            try {
                hud.pushState(snap);
            } catch (Throwable ignored) {
                // HUD is non-essential
            }
        }
    }

    // --- death + respawn ---

    /**
     * A Clash player died (called from {@code CocoonOnDeathSystem} with the death menu already suppressed).
     * Credit a kill to the last enemy attacker, then either eliminate (no respawn / lives exhausted) or
     * schedule a respawn at the team spawn. The body stays dead-in-place (no cocoon) until then.
     */
    public static void onPlayerDeath(@Nonnull RoundInstance round, @Nonnull UUID uuid,
                                     @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> cb) {
        ClashState cs = (ClashState) round.modeState();
        if (cs == null) {
            return;
        }
        ClashState.PlayerClash pc = cs.get(uuid);
        pc.deaths++;
        UUID killer = cs.lastAttacker(uuid);
        if (killer != null && !killer.equals(uuid) && round.teamOf(killer) != round.teamOf(uuid)) {
            cs.recordKill(killer);
            PlayerRef victimRef = Universe.get().getPlayer(uuid);
            PlayerRef killerRef = Universe.get().getPlayer(killer);
            if (killerRef != null) {
                String victimName = victimRef != null ? victimRef.getUsername() : "?";
                RoundFeedback.infoToast(killerRef, Lang.msg(Lang.CLASH_TOAST_KO).param("0", victimName));
            }
        }
        ClashConfig c = round.ruleSet().clash();
        long now = System.currentTimeMillis();
        switch (c.respawnPolicy()) {
            case NONE -> eliminate(round, uuid, pc);
            case LIMITED -> {
                pc.livesUsed++;
                if (pc.livesUsed >= c.maxLives()) {
                    eliminate(round, uuid, pc);
                } else {
                    pc.respawnAtMs = now + c.respawnDelaySeconds() * 1000L;
                }
            }
            case INFINITE -> pc.respawnAtMs = now + c.respawnDelaySeconds() * 1000L;
        }
        // Teammate-down cue (best-effort).
        notifyTeam(round, uuid, Lang.CLASH_TOAST_TEAMMATE_DOWN);
    }

    private static void eliminate(@Nonnull RoundInstance round, @Nonnull UUID uuid,
                                  @Nonnull ClashState.PlayerClash pc) {
        pc.eliminated = true;
        pc.respawnAtMs = 0L;
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (pr != null) {
            RoundFeedback.dangerToast(pr, Lang.CLASH_TOAST_ELIMINATED);
        }
    }

    private static void handleRespawns(@Nonnull RoundInstance round, @Nonnull World world,
                                       @Nonnull Store<EntityStore> store, @Nonnull ClashState cs,
                                       @Nonnull UUID worldUuid, long now) {
        for (PlayerRoundState st : round.playerStates()) {
            UUID uuid = st.playerId();
            ClashState.PlayerClash pc = cs.get(uuid);
            if (pc.eliminated || pc.respawnAtMs <= 0L || now < pc.respawnAtMs) {
                continue;
            }
            pc.respawnAtMs = 0L; // claim
            RoundModeSupport.respawnAtTeamSpawn(round, world, store, uuid, worldUuid);
            PlayerRef pr = Universe.get().getPlayer(uuid);
            if (pr != null) {
                RoundFeedback.warningToast(pr, Lang.CLASH_TOAST_RESPAWN);
            }
        }
    }

    // --- win/lose ---

    @Nullable
    private static RoundCompletedEvent.Outcome resolveOutcome(@Nonnull RoundInstance round,
                                                              @Nonnull ClashState cs) {
        ClashConfig c = round.ruleSet().clash();
        WinCondition wc = c.winCondition();
        int[] eligible = cs.eligibleCounts(round);
        int[] scores = cs.scoresFor(round, wc);
        int elapsed = round.durationSeconds();
        // Elimination is armed whenever a death can be permanent (NONE / LIMITED), never under INFINITE.
        boolean elimination = c.respawnPolicy() != RespawnPolicy.INFINITE;
        int cap = (wc == WinCondition.MOST_KILLS || wc == WinCondition.TDM_SCORE_TO_KILLS)
                ? c.scoreToWin() : 0;
        MatchRules rules = new MatchRules(elimination, cap,
                round.ruleSet().roundCapSeconds(), c.suddenDeathSeconds());
        MatchVerdict v = WinConditionResolver.resolve(new MatchSnapshot(eligible, scores, elapsed), rules);
        switch (v.status()) {
            case WIN:
                cs.setWinningTeam(v.winningTeam());
                return RoundCompletedEvent.Outcome.TEAM_ELIMINATED;
            case DRAW:
                cs.setWinningTeam(-1);
                return RoundCompletedEvent.Outcome.DRAW;
            case CONTINUE:
            default:
                return null;
        }
    }

    // --- helpers ---

    /** Toast every present teammate of {@code excluded} (used for the teammate-down cue). */
    private static void notifyTeam(@Nonnull RoundInstance round, @Nonnull UUID excluded, @Nonnull String key) {
        int team = round.teamOf(excluded);
        if (team < 0) {
            return;
        }
        for (UUID member : round.membersOfTeam(team)) {
            if (member.equals(excluded)) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(member);
            if (pr != null) {
                RoundFeedback.warningToast(pr, key);
            }
        }
    }
}
