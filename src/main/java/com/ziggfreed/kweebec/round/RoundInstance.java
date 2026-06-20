package com.ziggfreed.kweebec.round;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.universe.world.World;
import com.ziggfreed.common.instance.arena.ArenaDefinitionAsset;
import com.ziggfreed.common.worldmap.MapDiscovery;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.boss.BossController;
import com.ziggfreed.kweebec.hunter.HunterController;
import com.ziggfreed.kweebec.mode.chase.ChaseState;

/**
 * Mutable runtime state of one live round: its instance world, the party, each
 * player's round state, and the mode-specific gameplay state. The round engine
 * owns party/role/lifecycle state because the engine has no player-party API.
 *
 * <p>Lifecycle fields are {@code volatile} for cross-thread reads (the off-thread
 * state-machine scheduler reads {@code state}/{@code world} before hopping onto
 * the world thread); per-player gameplay state is mutated only on the instance
 * world thread.
 */
public final class RoundInstance {

    private final String roundId;
    private final KweebecMode mode;
    private final RuleSet ruleSet;
    private final long startedAtMs;

    /** Every player who ever joined this round (for the RoundCompleted participant list). */
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, PlayerRoundState> players = new ConcurrentHashMap<>();
    private final Map<UUID, Object> huds = new ConcurrentHashMap<>();

    @Nullable
    private volatile World world;
    private volatile InstanceState state = InstanceState.LOADING;
    private volatile long stateChangedAtMs = System.currentTimeMillis();
    private volatile boolean resolved;

    /**
     * The per-round world seed: the SINGLE coherent source the terrain, the shrine ring rotation,
     * the cave-anchor selection, and the corrupted-structure subset + facing all derive from, so a
     * given round is internally consistent and distinct rounds vary together. {@code 0} until set.
     *
     * <p>Set by {@code RoundService.onInstanceReady} from the freshly-spawned instance world's own
     * {@code instWorld.getWorldConfig().getSeed()} (the instance.bson omits a hardcoded {@code Seed},
     * so each spawned world auto-seeds via {@code WorldConfig.seed = System.currentTimeMillis()}),
     * read back BEFORE {@code ChaseMode.onStart(round)} and {@code ArenaBuilder.build(round, ...)} run.
     */
    private volatile long worldSeed;
    @Nullable
    private volatile RoundCompletedEvent.Outcome outcome;

    /**
     * Mode-specific gameplay state for the NON-chase modes (Clash {@code ClashState} / Domination
     * {@code DominationState}), held opaquely as {@link Object} so the engine never imports a mode type
     * (the open/closed seam). The owning {@link RoundMode} casts it. Chase keeps its own typed
     * {@link #chaseState} handle below. Set on the world thread in the mode's {@code onStart} before ACTIVE.
     */
    @Nullable
    private volatile Object modeState;

    /**
     * PvP team assignment (player -> 0-based team index), set ONCE on the spawning world thread before the
     * round goes ACTIVE and never mutated after, so off-thread reads are safe. Empty for co-op (Chase).
     */
    private final Map<UUID, Integer> teamAssignments = new ConcurrentHashMap<>();

    /** The arena chosen for a PvP round ({@code ArenaResolver}): source of team/objective/pickup anchors.
     *  {@code null} for Chase (which runs off {@code ArenaLayout} constants). Set before ACTIVE. */
    @Nullable
    private volatile ArenaDefinitionAsset arena;

    /** Pre-made party block sizes (in participant-list order) for the team split; {@code null} = treat the
     *  whole party as one block. The matchmaking lobby sets this so a premade stays on one team. */
    @Nullable
    private volatile List<Integer> partyBlocks;

    // Chase-mode gameplay state (the only co-op mode).
    @Nullable
    private volatile ChaseState chaseState;
    @Nullable
    private volatile HunterController hunterController;
    @Nullable
    private volatile BossController bossController;
    /** Shrine-discovery map markers for this round, created + attached when {@code shrineDiscovery != OFF}. */
    @Nullable
    private volatile MapDiscovery mapDiscovery;

    public RoundInstance(@Nonnull String roundId, @Nonnull KweebecMode mode,
                         @Nonnull RuleSet ruleSet, long startedAtMs) {
        this.roundId = roundId;
        this.mode = mode;
        this.ruleSet = ruleSet;
        this.startedAtMs = startedAtMs;
    }

    @Nonnull
    public String roundId() {
        return roundId;
    }

    @Nonnull
    public KweebecMode mode() {
        return mode;
    }

    @Nonnull
    public RuleSet ruleSet() {
        return ruleSet;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public int durationSeconds() {
        return (int) ((System.currentTimeMillis() - startedAtMs) / 1000L);
    }

    @Nullable
    public World world() {
        return world;
    }

    public void setWorld(@Nullable World world) {
        this.world = world;
    }

    /**
     * The per-round world seed (see the field doc). {@code 0} until the parent sets it in
     * {@code RoundService.onInstanceReady} from {@code instWorld.getWorldConfig().getSeed()}.
     */
    public long worldSeed() {
        return worldSeed;
    }

    public void setWorldSeed(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    @Nonnull
    public InstanceState state() {
        return state;
    }

    public void setState(@Nonnull InstanceState state) {
        this.state = state;
        this.stateChangedAtMs = System.currentTimeMillis();
    }

    public long stateChangedAtMs() {
        return stateChangedAtMs;
    }

    public boolean isResolved() {
        return resolved;
    }

    /** Atomically claim resolution; returns true exactly once (guards double win/lose). */
    public synchronized boolean claimResolution(@Nonnull RoundCompletedEvent.Outcome outcome) {
        if (resolved) {
            return false;
        }
        this.resolved = true;
        this.outcome = outcome;
        return true;
    }

    @Nullable
    public RoundCompletedEvent.Outcome outcome() {
        return outcome;
    }

    // --- party / player state ---

    public void addPlayer(@Nonnull UUID playerId) {
        participants.add(playerId);
        players.computeIfAbsent(playerId, PlayerRoundState::new);
    }

    @Nullable
    public PlayerRoundState playerState(@Nonnull UUID playerId) {
        return players.get(playerId);
    }

    @Nonnull
    public Collection<PlayerRoundState> playerStates() {
        return players.values();
    }

    @Nonnull
    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    @Nonnull
    public List<UUID> participantList() {
        return List.copyOf(participants);
    }

    public int partySize() {
        return participants.size();
    }

    public void markLeft(@Nonnull UUID playerId) {
        PlayerRoundState st = players.get(playerId);
        if (st != null) {
            st.setLeftRound(true);
        }
    }

    /** Players still in the round and not cocooned/escaped/left. */
    public int activeCount() {
        int n = 0;
        for (PlayerRoundState st : players.values()) {
            if (st.isActive()) {
                n++;
            }
        }
        return n;
    }

    /** Players still physically present in the instance (not left), cocooned or not. */
    public int presentCount() {
        int n = 0;
        for (PlayerRoundState st : players.values()) {
            if (!st.hasLeftRound()) {
                n++;
            }
        }
        return n;
    }

    public boolean anyEscaped() {
        for (PlayerRoundState st : players.values()) {
            if (st.hasEscaped()) {
                return true;
            }
        }
        return false;
    }

    // --- per-player HUD handles (stored as Object to keep this class engine-light) ---

    public void putHud(@Nonnull UUID playerId, @Nonnull Object hud) {
        huds.put(playerId, hud);
    }

    @Nullable
    public Object hud(@Nonnull UUID playerId) {
        return huds.get(playerId);
    }

    public void removeHud(@Nonnull UUID playerId) {
        huds.remove(playerId);
    }

    @Nonnull
    public Collection<Object> huds() {
        return huds.values();
    }

    // --- mode-generic state (Clash / Domination) ---

    /** The opaque mode-state handle for a non-chase mode; the owning {@link RoundMode} casts it. */
    @Nullable
    public Object modeState() {
        return modeState;
    }

    public void setModeState(@Nullable Object modeState) {
        this.modeState = modeState;
    }

    /** Assign a player to a 0-based team (PvP). Set once before ACTIVE; never mutate after. */
    public void setTeam(@Nonnull UUID playerId, int team) {
        teamAssignments.put(playerId, team);
    }

    /** The player's 0-based team index, or {@code -1} if unassigned (co-op / not seated). */
    public int teamOf(@Nonnull UUID playerId) {
        Integer t = teamAssignments.get(playerId);
        return t != null ? t : -1;
    }

    /** Live members assigned to a team (a fresh list; iteration-safe). */
    @Nonnull
    public List<UUID> membersOfTeam(int team) {
        List<UUID> out = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Integer> e : teamAssignments.entrySet()) {
            if (e.getValue() != null && e.getValue() == team) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** The full player -> team map (a fresh copy). */
    @Nonnull
    public Map<UUID, Integer> teamAssignments() {
        return Map.copyOf(teamAssignments);
    }

    /** The PvP arena chosen for this round, or {@code null} for Chase. */
    @Nullable
    public ArenaDefinitionAsset arena() {
        return arena;
    }

    public void setArena(@Nullable ArenaDefinitionAsset arena) {
        this.arena = arena;
    }

    /** Pre-made party block sizes for the team split, or {@code null} = the whole party is one block. */
    @Nullable
    public List<Integer> partyBlocks() {
        return partyBlocks;
    }

    public void setPartyBlocks(@Nullable List<Integer> partyBlocks) {
        this.partyBlocks = partyBlocks != null ? List.copyOf(partyBlocks) : null;
    }

    // --- chase mode ---

    @Nullable
    public ChaseState chaseState() {
        return chaseState;
    }

    public void setChaseState(@Nullable ChaseState chaseState) {
        this.chaseState = chaseState;
    }

    @Nullable
    public HunterController hunterController() {
        return hunterController;
    }

    public void setHunterController(@Nullable HunterController hunterController) {
        this.hunterController = hunterController;
    }

    /**
     * The boss-capstone controller for this round, set when {@code ChaseMode.openGate} spawns the multi-phase
     * Warden (only on a {@code bossEnabled} round). {@code null} on a round without a boss.
     */
    @Nullable
    public BossController bossController() {
        return bossController;
    }

    public void setBossController(@Nullable BossController bossController) {
        this.bossController = bossController;
    }

    /**
     * The shrine-discovery map-marker tracker for this round, created + attached by
     * {@code RoundService.onInstanceReady} when the preset's {@code shrineDiscovery} is not OFF.
     * {@code null} on a Hardcore (or otherwise discovery-off) round.
     */
    @Nullable
    public MapDiscovery mapDiscovery() {
        return mapDiscovery;
    }

    public void setMapDiscovery(@Nullable MapDiscovery mapDiscovery) {
        this.mapDiscovery = mapDiscovery;
    }
}
