package com.ziggfreed.kweebec.mode.clash;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.WinCondition;

/**
 * Per-round CLASH gameplay state, hung opaquely on {@link RoundInstance#modeState()} (the engine never
 * imports this type). Mutated only on the instance world thread. The team -> player mapping itself lives
 * on {@link RoundInstance} (set once before ACTIVE); this holds the per-player combat counters + lifecycle
 * flags and derives per-team scores on demand from that map.
 */
public final class ClashState {

    /** Number of teams (always 2 for Clash: 1v1 / 2v2). */
    public static final int TEAM_COUNT = 2;

    /** One player's live Clash state. Mutated only on the world thread. */
    public static final class PlayerClash {
        public int hits;
        public int kills;
        public int deaths;
        public int livesUsed;
        public boolean eliminated;
        /** {@code > 0} = scheduled respawn time (ms); {@code 0} = alive or eliminated. */
        public long respawnAtMs;
        @Nullable
        public UUID lastAttacker;
        // First-arrival lifecycle flags (mirror Chase's PlayerRoundState arrival flags).
        public boolean gameModeApplied;
        public boolean modelApplied;
        public boolean spawnPlaced;
        public boolean hudInstalled;
    }

    private final Map<UUID, PlayerClash> players = new ConcurrentHashMap<>();
    /** Resolved winning team (set at resolve; {@code -1} = none/draw), read by the native event. */
    private volatile int winningTeam = -1;
    /** Mushroom pickup wave bookkeeping (count of waves already spawned). */
    private int mushroomWavesFired;

    @Nonnull
    public PlayerClash get(@Nonnull UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerClash());
    }

    public void recordHit(@Nonnull UUID attacker) {
        get(attacker).hits++;
    }

    public void recordKill(@Nonnull UUID killer) {
        get(killer).kills++;
    }

    public void setLastAttacker(@Nonnull UUID victim, @Nonnull UUID attacker) {
        get(victim).lastAttacker = attacker;
    }

    @Nullable
    public UUID lastAttacker(@Nonnull UUID victim) {
        PlayerClash pc = players.get(victim);
        return pc != null ? pc.lastAttacker : null;
    }

    public int winningTeam() {
        return winningTeam;
    }

    public void setWinningTeam(int team) {
        this.winningTeam = team;
    }

    public int mushroomWavesFired() {
        return mushroomWavesFired;
    }

    public void setMushroomWaves(int waves) {
        this.mushroomWavesFired = waves;
    }

    /** Per-team total of the stat the win condition is scored on (hits for hits/last-standing, else kills). */
    @Nonnull
    public int[] scoresFor(@Nonnull RoundInstance round, @Nonnull WinCondition wc) {
        boolean byHits = wc == WinCondition.LAST_TEAM_STANDING || wc == WinCondition.MOST_HITS_LANDED;
        int[] out = new int[TEAM_COUNT];
        for (int t = 0; t < TEAM_COUNT; t++) {
            int sum = 0;
            for (UUID member : round.membersOfTeam(t)) {
                PlayerClash pc = players.get(member);
                if (pc != null) {
                    sum += byHits ? pc.hits : pc.kills;
                }
            }
            out[t] = sum;
        }
        return out;
    }

    /** Per-team count of players still able to fight: present (not left) AND not eliminated. */
    @Nonnull
    public int[] eligibleCounts(@Nonnull RoundInstance round) {
        int[] out = new int[TEAM_COUNT];
        for (int t = 0; t < TEAM_COUNT; t++) {
            int n = 0;
            for (UUID member : round.membersOfTeam(t)) {
                if (isEligible(round, member)) {
                    n++;
                }
            }
            out[t] = n;
        }
        return out;
    }

    /** A player counts toward their team's strength while present and not eliminated (a respawning player still counts). */
    public boolean isEligible(@Nonnull RoundInstance round, @Nonnull UUID uuid) {
        var st = round.playerState(uuid);
        if (st == null || st.hasLeftRound()) {
            return false;
        }
        PlayerClash pc = players.get(uuid);
        return pc == null || !pc.eliminated;
    }
}
