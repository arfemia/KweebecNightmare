package com.ziggfreed.kweebec.round;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.health.HealthUtil;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.atmosphere.MusicBedService;
import com.ziggfreed.kweebec.death.CocoonService;
import com.ziggfreed.kweebec.event.RoundEvents;
import com.ziggfreed.kweebec.feedback.HeartbeatService;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.feedback.ScareDirector;
import com.ziggfreed.kweebec.integration.KweebecNightmareAPI;

/**
 * The mutating authority over all rounds: spawn (Path A instance + teleport-in),
 * resolve (win/lose teardown), and voluntary exit. Public methods are safe to call
 * from any thread; world-bound work is dispatched via {@code world.execute}.
 *
 * <p>The engine has no party API, so this service owns party/role/lifecycle state
 * via {@link RoundRegistry}. It is MODE-AGNOSTIC: every mode-specific call (stand-up,
 * tick, resolve, teardown) routes through the {@link ModeRegistry} so a new mode is one
 * {@link RoundMode} + one registration, with no edit here.
 */
public final class RoundService {

    /** The pack hostile Kweebec role the Chase hunter uses (read by {@code ChaseRoundMode}). */
    public static final String HUNTER_ROLE = "KweebecNightmare_Blight";

    /** Delay between showing the result and ejecting players, so the banner is seen. */
    private static final long RESULT_HOLD_SECONDS = 6;

    private static final RoundService INSTANCE = new RoundService();

    @Nonnull
    public static RoundService getInstance() {
        return INSTANCE;
    }

    private final RoundRegistry registry = new RoundRegistry();
    private final RoundStateMachine stateMachine = new RoundStateMachine(this);
    private final CleanupTicker cleanupTicker = new CleanupTicker(this);

    private RoundService() {
    }

    @Nonnull
    public RoundRegistry registry() {
        return registry;
    }

    public void startup() {
        stateMachine.start();
        cleanupTicker.start();
        KweebecNightmarePlugin.LOGGER.atInfo().log("[Kweebec] round service started.");
    }

    public void shutdown() {
        stateMachine.stop();
        cleanupTicker.stop();
        HeartbeatService.shutdown();
        ScareDirector.shutdown();
        InstanceLifecycle.shutdown();
    }

    // --- start ---

    /**
     * Start a CHASE round for a party (the co-op compatibility shim; delegates to
     * {@link #startRound(UUID, List, String, KweebecMode)}).
     */
    @Nonnull
    public CompletableFuture<String> startChase(@Nonnull UUID initiator,
                                                @Nonnull List<UUID> party,
                                                @Nullable String presetId) {
        return startRound(initiator, party, presetId, KweebecMode.CHASE);
    }

    /**
     * Start a round of any {@code mode} for a party (the initiator is included). Spawns a fresh instance
     * (the {@link ArenaResolver} picks the instance world) and teleports each member in. Returns the round
     * id, or a failed future on a validation error.
     *
     * <p>The preset id resolves through {@link KweebecNightmareAPI#resolveRuleSet}, composing all four
     * difficulty tiers ({@code defaults < pack < owner < runtime}). The resulting {@link RuleSet} is the
     * round's immutable runtime value object. Per-mode stand-up (mode state, actors, arena, teams, atmosphere)
     * happens in the mode's {@link RoundMode#onStart} from {@link #onInstanceReady}.
     */
    @Nonnull
    public CompletableFuture<String> startRound(@Nonnull UUID initiator,
                                                @Nonnull List<UUID> party,
                                                @Nullable String presetId,
                                                @Nonnull KweebecMode mode) {
        if (!ModeRegistry.has(mode)) {
            return CompletableFuture.failedFuture(new IllegalStateException("mode not built: " + mode.id()));
        }
        if (registry.isInRound(initiator)) {
            return CompletableFuture.failedFuture(new IllegalStateException("already in a round"));
        }
        PlayerRef initRef = Universe.get().getPlayer(initiator);
        if (initRef == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("initiator offline"));
        }
        World fromWorld = Universe.get().getWorld(initRef.getWorldUuid());
        if (fromWorld == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("initiator has no world"));
        }

        // Four-tier resolve: defaults < pack < owner (PresetConfig) < runtime (API).
        RuleSet ruleSet = KweebecNightmareAPI.resolveRuleSet(presetId);
        String roundId = UUID.randomUUID().toString();
        RoundInstance round = new RoundInstance(roundId, mode, ruleSet, System.currentTimeMillis());
        registry.add(round);

        // Bind every eligible member (online + not already in a round).
        for (UUID member : party) {
            if (registry.isInRound(member) && registry.forPlayer(member) != round) {
                continue;
            }
            if (Universe.get().getPlayer(member) == null) {
                continue;
            }
            round.addPlayer(member);
            registry.bindPlayer(member, roundId);
        }
        if (round.partySize() == 0) {
            registry.remove(roundId);
            return CompletableFuture.failedFuture(new IllegalStateException("no eligible players"));
        }

        // Resolve which instance world to spawn (and, for PvP, the chosen arena) BEFORE spawning it.
        ArenaResolver.Resolved arena = ArenaResolver.resolve(round);
        round.setArena(arena.arena());

        CompletableFuture<String> result = new CompletableFuture<>();
        fromWorld.execute(() -> {
            try {
                Store<EntityStore> store = fromWorld.getEntityStore().getStore();
                Ref<EntityStore> ref = initRef.getReference();
                Transform returnPoint = captureReturn(store, ref);

                CompletableFuture<World> worldFuture =
                        InstanceLifecycle.spawnInstance(arena.instanceName(), fromWorld, returnPoint);

                // Teleport the initiator in immediately (same world thread), returning
                // them to where they were on exit.
                if (ref != null && ref.isValid()) {
                    // Snapshot + persist + strip the initiator's inventory before they enter
                    // (restored exactly on exit; persisted so a crash never eats gear).
                    RoundInventoryGuard.onEnter(initiator, store, ref, ruleSet.inventoryMode());
                    InstanceLifecycle.teleportIn(ref, store, worldFuture, returnPoint);
                }
                // Teleport the rest, each on their own current world thread.
                teleportParty(round, initiator, worldFuture);

                worldFuture.whenComplete((instWorld, err) -> {
                    if (err != null || instWorld == null) {
                        KweebecNightmarePlugin.LOGGER.atSevere().log(
                                "[Kweebec] spawnInstance failed: "
                                        + (err != null ? err.getMessage() : "null world"));
                        // The instance never came up; give every stripped player their gear back.
                        for (UUID p : round.participantList()) {
                            RoundInventoryGuard.restore(p);
                        }
                        registry.remove(roundId);
                        result.completeExceptionally(err != null ? err
                                : new IllegalStateException("null instance world"));
                        return;
                    }
                    onInstanceReady(round, instWorld);
                    result.complete(roundId);
                });
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atSevere().log("[Kweebec] startRound failed: " + t.getMessage());
                registry.remove(roundId);
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    private void teleportParty(@Nonnull RoundInstance round, @Nonnull UUID initiator,
                               @Nonnull CompletableFuture<World> worldFuture) {
        for (UUID member : round.participants()) {
            if (member.equals(initiator)) {
                continue;
            }
            PlayerRef mref = Universe.get().getPlayer(member);
            if (mref == null) {
                dropMember(round, member);
                continue;
            }
            World mWorld = Universe.get().getWorld(mref.getWorldUuid());
            if (mWorld == null) {
                dropMember(round, member);
                continue;
            }
            mWorld.execute(() -> {
                try {
                    Store<EntityStore> ms = mWorld.getEntityStore().getStore();
                    Ref<EntityStore> mer = mref.getReference();
                    if (mer != null && mer.isValid()) {
                        // Each member returns to their OWN captured spot, not the initiator's.
                        Transform memberReturn = captureReturn(ms, mer);
                        // Snapshot + persist + strip this member's inventory before they enter.
                        RoundInventoryGuard.onEnter(member, ms, mer, round.ruleSet().inventoryMode());
                        InstanceLifecycle.teleportIn(mer, ms, worldFuture, memberReturn);
                    } else {
                        dropMember(round, member);
                    }
                } catch (Throwable t) {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] teleport member failed: " + t.getMessage());
                    dropMember(round, member);
                }
            });
        }
    }

    /** Mark a party member as having left and free their registry binding immediately. */
    private void dropMember(@Nonnull RoundInstance round, @Nonnull UUID member) {
        round.markLeft(member);
        registry.unbindPlayer(member);
    }

    private void onInstanceReady(@Nonnull RoundInstance round, @Nonnull World instWorld) {
        instWorld.execute(() -> {
            try {
                // The round may have been force-resolved/removed (e.g. /kweebec endall)
                // during the async instance load - dispose the orphan world and bail.
                if (round.isResolved() || registry.byId(round.roundId()) == null) {
                    InstanceLifecycle.removeWorldOffThread(instWorld);
                    return;
                }
                round.setWorld(instWorld);
                // Read back the instance world's actual generation seed (the hardcoded instance.bson Seed
                // was removed so each round auto-seeds from currentTimeMillis) and stamp it BEFORE the mode
                // stands up, so the layout / structures all vary off ONE coherent seed.
                round.setWorldSeed(instWorld.getWorldConfig().getSeed());
                Store<EntityStore> store = instWorld.getEntityStore().getStore();
                RoundMode mode = ModeRegistry.get(round.mode());
                if (mode == null) {
                    KweebecNightmarePlugin.LOGGER.atSevere().log(
                            "[Kweebec] no RoundMode for " + round.mode().id() + "; disposing instance.");
                    InstanceLifecycle.removeWorldOffThread(instWorld);
                    registry.remove(round.roundId());
                    return;
                }
                // Mode stand-up owns ALL mode-specific setup (state, actors, arena, atmosphere, teams,
                // markers, model swaps). Runs before the round goes ACTIVE so the first tick sees it.
                mode.onStart(round, instWorld, store);
                round.setState(InstanceState.ACTIVE);

                RoundEvents.fireRoundStarted(round.roundId(), round.mode().id(),
                        round.ruleSet().presetId(), round.participantList());

                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] round " + round.roundId() + " active ("
                                + round.mode().id() + ", " + round.partySize() + "p, "
                                + round.ruleSet().presetId() + ").");
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atSevere().log(
                        "[Kweebec] onInstanceReady failed: " + t.getMessage());
            }
        });
    }

    // --- resolve (win/lose) ---

    /** End a round with an outcome. Idempotent (first call wins). Mode-agnostic: the scoring / results /
     *  titles are the mode's {@link RoundMode#onResolve}. */
    public void resolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        if (!round.claimResolution(outcome)) {
            return;
        }
        RoundMode mode = ModeRegistry.get(round.mode());
        boolean win = mode != null ? mode.isWin(outcome) : isWinFallback(outcome);
        round.setState(win ? InstanceState.WON : InstanceState.LOST);
        HeartbeatService.stop(round.roundId());
        MusicBedService.clear(round);

        int duration = round.durationSeconds();
        int difficultyScore = mode != null ? mode.difficultyScore(round.ruleSet()) : 0;
        int progress = mode != null ? mode.objectiveProgress(round) : 0;
        Integer winnerTeam = mode != null ? mode.winnerTeam(round, outcome) : null;
        List<UUID> participants = round.participantList();
        World world = round.world();

        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] round " + round.roundId() + " resolved: " + outcome
                        + " (" + duration + "s, progress " + progress + ").");

        if (world == null) {
            RoundEvents.fireRoundCompleted(round.roundId(), round.mode().id(), outcome,
                    participants, duration, progress, difficultyScore, winnerTeam);
            if (mode != null) {
                mode.onResolve(round, outcome, duration, difficultyScore, null, null);
            }
            registry.remove(round.roundId());
            return;
        }

        world.execute(() -> {
            // Fire the native events on the world thread so listeners can hop safely.
            RoundEvents.fireRoundCompleted(round.roundId(), round.mode().id(), outcome,
                    participants, duration, progress, difficultyScore, winnerTeam);
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (mode != null) {
                mode.onResolve(round, outcome, duration, difficultyScore, world, store);
            }
            teardown(round, world);
        });

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> finalizeAndEject(round, world, win),
                RESULT_HOLD_SECONDS, TimeUnit.SECONDS);
    }

    private static boolean isWinFallback(@Nonnull RoundCompletedEvent.Outcome outcome) {
        return outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
    }

    private void teardown(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            // Mode-specific teardown (Chase: hunter/boss/scare/markers; Clash: model restore + markers).
            RoundMode mode = ModeRegistry.get(round.mode());
            if (mode != null) {
                mode.onTeardown(round, world, store);
            }
            // Generic per-player HUD restore.
            for (PlayerRoundState st : round.playerStates()) {
                PlayerRef pr = Universe.get().getPlayer(st.playerId());
                if (pr == null) {
                    continue;
                }
                Ref<EntityStore> ref = pr.getReference();
                if (ref != null && ref.isValid()) {
                    RoundFeedback.restoreHud(store, ref);
                }
                round.removeHud(st.playerId());
            }
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] teardown failed: " + t.getMessage());
        }
    }

    private void finalizeAndEject(@Nonnull RoundInstance round, @Nonnull World world, boolean fullHeal) {
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                for (PlayerRoundState st : round.playerStates()) {
                    PlayerRef pr = Universe.get().getPlayer(st.playerId());
                    if (pr == null) {
                        continue;
                    }
                    Ref<EntityStore> ref = pr.getReference();
                    if (ref != null && ref.isValid()) {
                        reviveThenExit(world, store, ref, fullHeal);
                    }
                }
                round.setState(InstanceState.EVICTING);
                // Flag the instance for removal-when-empty; the cleanup ticker is the backstop.
                InstanceLifecycle.safeRemove(world);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] eject failed: " + t.getMessage());
            }
        });
    }

    /** Ticks for the in-instance revive to fully apply before the cross-world exit (see {@link #reviveThenExit}). */
    private static final long REVIVE_SETTLE_MS = 500;

    /**
     * Restore a dead player to ALIVE (remove their {@link DeathComponent} via the engine
     * respawn path) and THEN teleport them out of the instance, so a cocooned
     * (dead-in-place) player never arrives in the overworld with a lingering death state -
     * which the engine PlayerAddedSystem turns into a respawn/death menu.
     *
     * <p>CRITICAL: the revive (health/stat reset, effect + UI clear) is NOT synchronous - it is
     * driven by the engine's {@code RespawnSystems} firing on {@link DeathComponent} REMOVAL,
     * which {@code DeathComponent.respawn} only SCHEDULES ({@code respawnPlayer} returns an
     * already-completed future). Those systems run over the next instance-world ticks. Exiting
     * cross-world immediately interrupts them, so the {@code DeathComponent} rides along and the
     * player arrives still dead until relog. So for a dead player we let the revive settle in the
     * instance ({@link #REVIVE_SETTLE_MS}) BEFORE the exit. An already-alive player exits immediately.
     *
     * <p>Runs on the instance world thread; the exit re-hops via {@code world.execute}.
     */
    private void reviveThenExit(@Nonnull World world, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, boolean fullHeal) {
        // Capture the UUID now: the cross-world exit invalidates this ref, so the
        // post-exit heal must re-resolve the player by UUID in the overworld.
        UUID uuid = null;
        boolean dead = false;
        try {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            if (pr != null) {
                uuid = pr.getUuid();
            }
            dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
        } catch (Throwable ignored) {
            // best effort
        }
        final UUID playerId = uuid;
        final boolean wasDead = dead;
        try {
            DeathComponent.respawn(store, ref).whenComplete((v, err) -> {
                if (err != null) {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] revive-before-exit respawn failed: " + err.getMessage());
                }
                if (wasDead) {
                    HytaleServer.SCHEDULED_EXECUTOR.schedule(
                            () -> exitFromInstance(world, ref, playerId, fullHeal),
                            REVIVE_SETTLE_MS, TimeUnit.MILLISECONDS);
                } else {
                    exitFromInstance(world, ref, playerId, fullHeal);
                }
            });
        } catch (Throwable t) {
            // Last-resort: if the respawn call itself throws, still get the player out.
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] reviveThenExit failed, exiting directly: " + t.getMessage());
            if (ref.isValid()) {
                InstanceLifecycle.exit(ref, store)
                        .whenComplete((v2, e2) -> scheduleOverworldResync(playerId, fullHeal));
            }
        }
    }

    /** Cross-world exit on the instance world thread, then resync (and, on a win, full-heal) once back in the overworld. */
    private void exitFromInstance(@Nonnull World world, @Nonnull Ref<EntityStore> ref, @Nullable UUID playerId,
                                  boolean fullHeal) {
        world.execute(() -> {
            try {
                Store<EntityStore> ws = world.getEntityStore().getStore();
                if (ref.isValid()) {
                    InstanceLifecycle.exit(ref, ws)
                            .whenComplete((v2, e2) -> scheduleOverworldResync(playerId, fullHeal));
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] exit-after-revive failed: " + t.getMessage());
            }
        });
    }

    /**
     * After a player has fully returned to the overworld, clear any stale {@code PendingTeleport} left over
     * from the death respawn's same-world teleport, then clear the cocoon root + restore the inventory
     * snapshot. On a round WIN ({@code fullHeal}) the survivor is also topped off to full health via the
     * ziggfreed-common {@link HealthUtil} (the same native {@code EntityStats} heal the MMO health-tick path
     * uses), so a battered winner returns whole. Best-effort; never throws.
     */
    private void scheduleOverworldResync(@Nullable UUID uuid, boolean fullHeal) {
        if (uuid == null) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                PlayerRef back = Universe.get().getPlayer(uuid);
                if (back == null) {
                    return;
                }
                World w = Universe.get().getWorld(back.getWorldUuid());
                if (w == null) {
                    return;
                }
                w.execute(() -> {
                    Ref<EntityStore> r = back.getReference();
                    if (r != null) {
                        Store<EntityStore> ws = w.getEntityStore().getStore();
                        PlayerResync.clearPendingTeleport(r, ws);
                        // Clear the cocoon root + any round effect HERE (in the overworld) so the removal syncs.
                        CocoonService.clearEffects(r, ws);
                        // Restore the inventory snapshot taken on entry (drops in-round loot; no-op in KEEP).
                        RoundInventoryGuard.restore(ws, r, uuid);
                        // On a win, fully heal the survivor now that they are back in the overworld.
                        if (fullHeal) {
                            HealthUtil.fullHeal(ws, r);
                        }
                    }
                });
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec] clear-pending-teleport after exit failed: " + t.getMessage());
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    // --- voluntary exit ---

    /** A player leaves their current round (and is teleported back out). */
    public void exit(@Nonnull UUID uuid) {
        RoundInstance round = registry.forPlayer(uuid);
        if (round == null) {
            return;
        }
        round.markLeft(uuid);
        registry.unbindPlayer(uuid);
        World world = round.world();
        PlayerRef pr = Universe.get().getPlayer(uuid);
        if (world == null || pr == null) {
            return;
        }
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Ref<EntityStore> ref = pr.getReference();
                if (ref != null && ref.isValid()) {
                    RoundFeedback.restoreHud(store, ref);
                    // A swapped model is restored by the PlayerReady catch-all when this player reaches the
                    // overworld (the robust path covering exit / disconnect / relog / crash); not here.
                    // A voluntary leave is not a win, so no full-heal on return.
                    reviveThenExit(world, store, ref, false);
                }
                round.removeHud(uuid);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] exit failed: " + t.getMessage());
            }
        });
    }

    /**
     * Last-resort recovery for a round whose per-tick loop is PERSISTENTLY throwing (the state-machine
     * watchdog calls this). Such a round can never reach {@link ChaseMode#tick}'s outcome resolution, so a
     * cocooned (dead-in-place) player would stay frozen under the infinite {@code DisableAll} cocoon
     * forever. This frees every player WITHOUT touching {@link RoundCompletedEvent.Outcome} - which may
     * itself be the unloadable class behind the failure - by routing each through the normal voluntary
     * {@link #exit} path (revive -> cross-world exit -> overworld resync clears the cocoon), then tearing
     * the round down. It deliberately skips scoring + the {@code RoundCompleted} event (a wedged round has
     * no trustworthy outcome). Flips the round out of {@code ACTIVE} and drops it from the registry so the
     * state machine stops ticking it.
     */
    void forceAbort(@Nonnull RoundInstance round) {
        KweebecNightmarePlugin.LOGGER.atSevere().log(
                "[Kweebec] force-aborting wedged round " + round.roundId()
                        + " (tick persistently failing); freeing its players.");
        round.setState(InstanceState.EVICTING);
        HeartbeatService.stop(round.roundId());
        MusicBedService.clear(round);
        World world = round.world();
        // Mode actor + scheduler teardown (despawn hunters/boss, stop whispers, drop markers). All
        // Outcome-free; best-effort so a failure here never blocks freeing the players below.
        if (world != null) {
            try {
                RoundMode mode = ModeRegistry.get(round.mode());
                if (mode != null) {
                    mode.onTeardown(round, world, world.getEntityStore().getStore());
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] force-abort teardown failed for " + round.roundId() + ": " + t.getMessage());
            }
        }
        // Free every bound player on the standard exit path (clears the cocoon on overworld return).
        for (UUID id : round.participantList()) {
            try {
                exit(id);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] force-abort exit failed for " + id + ": " + t.getMessage());
            }
        }
        // Flag the instance for removal-when-empty (players leave via exit above); drop the round so the
        // state machine no longer sees it.
        if (world != null) {
            InstanceLifecycle.safeRemove(world);
        }
        registry.remove(round.roundId());
    }

    /** Admin force-end of every live round. Returns how many were ended. */
    public int endAll() {
        int n = 0;
        for (RoundInstance round : registry.all()) {
            if (!round.isResolved()) {
                resolve(round, RoundCompletedEvent.Outcome.ABORTED);
                n++;
            }
        }
        return n;
    }

    @Nonnull
    private static Transform captureReturn(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        try {
            if (ref != null && ref.isValid()) {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    return tc.getTransform().clone();
                }
            }
        } catch (Throwable ignored) {
            // fall through to origin
        }
        return new Transform();
    }
}
