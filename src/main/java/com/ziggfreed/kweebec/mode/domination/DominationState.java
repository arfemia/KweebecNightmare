package com.ziggfreed.kweebec.mode.domination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.instance.arena.ArenaDefinitionAsset;
import com.ziggfreed.common.instance.zone.ControlPointTracker;
import com.ziggfreed.kweebec.round.DominationConfig;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Per-round DOMINATION state, hung opaquely on {@link RoundInstance#modeState()}. Holds the capture points
 * (each wrapping a {@code ziggfreed-common} {@link ControlPointTracker}), per-team accrued score, and the
 * per-player respawn/arrival bookkeeping (Domination is always-respawn). Mutated only on the world thread.
 * The point LOCATIONS come from the arena's objective anchors, so a King-of-the-Hill (one point) vs a
 * 3-point layout is pure arena data.
 */
public final class DominationState {

    public static final int TEAM_COUNT = 2;

    /** Per-player arrival + respawn flags (Domination has no elimination; respawn is always on). */
    public static final class PlayerDom {
        public boolean gameModeApplied;
        public boolean modelApplied;
        public boolean spawnPlaced;
        public long respawnAtMs;
    }

    /** One capture point: its center+radius, the non-latching control tracker, and the current controller. */
    public static final class Point {
        public final String id;
        public final double x;
        public final double z;
        public final double radius;
        public final ControlPointTracker tracker;
        @Nullable
        public String controllerTeam; // "0" / "1" / null = neutral
        public boolean markerPlaced;

        Point(@Nonnull String id, double x, double z, double radius, double holdSeconds) {
            this.id = id;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.tracker = new ControlPointTracker(holdSeconds);
        }
    }

    private final Map<UUID, PlayerDom> players = new ConcurrentHashMap<>();
    private final List<Point> points = new ArrayList<>();
    private final int[] teamScore = new int[TEAM_COUNT];
    private volatile int winningTeam = -1;
    private int mushroomWaves;

    /** Build the capture points from the round's arena objective anchors, falling back to one centre point. */
    public DominationState(@Nonnull RoundInstance round) {
        DominationConfig dc = round.ruleSet().domination();
        ArenaDefinitionAsset arena = round.arena();
        if (arena != null && !arena.objectiveAnchors().isEmpty()) {
            for (ArenaDefinitionAsset.ObjectiveAnchor a : arena.objectiveAnchors()) {
                double r = a.radius() > 0 ? a.radius() : dc.pointRadius();
                points.add(new Point(a.id(), a.x(), a.z(), r, dc.pointHoldSeconds()));
            }
        } else {
            // King-of-the-Hill fallback: one point at the arena origin.
            points.add(new Point("hill", 0.0, 0.0, dc.pointRadius(), dc.pointHoldSeconds()));
        }
    }

    @Nonnull
    public PlayerDom get(@Nonnull UUID uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerDom());
    }

    @Nonnull
    public List<Point> points() {
        return points;
    }

    public int teamScore(int team) {
        return team >= 0 && team < TEAM_COUNT ? teamScore[team] : 0;
    }

    public void addTeamScore(int team, int delta) {
        if (team >= 0 && team < TEAM_COUNT) {
            teamScore[team] += delta;
        }
    }

    public int winningTeam() {
        return winningTeam;
    }

    public void setWinningTeam(int team) {
        this.winningTeam = team;
    }

    public int mushroomWavesFired() {
        return mushroomWaves;
    }

    public void setMushroomWaves(int waves) {
        this.mushroomWaves = waves;
    }
}
