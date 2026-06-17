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
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.atmosphere.AtmosphereService;
import com.ziggfreed.kweebec.atmosphere.MusicBedService;
import com.ziggfreed.kweebec.event.RoundEvents;
import com.ziggfreed.kweebec.feedback.HeartbeatService;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.hunter.AiHunterController;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.mode.chase.ChaseMode;
import com.ziggfreed.kweebec.mode.chase.ChaseState;

/**
 * The mutating authority over all rounds: spawn (Path A instance + teleport-in),
 * resolve (win/lose teardown), and voluntary exit. Public methods are safe to call
 * from any thread; world-bound work is dispatched via {@code world.execute}.
 *
 * <p>The engine has no party API, so this service owns party/role/lifecycle state
 * via {@link RoundRegistry}. Only the Chase mode is wired in the MVP.
 */
public final class RoundService {

    /** The pack hostile Kweebec role the hunter uses. */
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
        InstanceLifecycle.shutdown();
    }

    // --- start ---

    /**
     * Start a Chase round for a party (the initiator is included). Spawns a fresh
     * instance from the pack asset and teleports each member in. Returns the round
     * id, or a failed future on a validation error.
     */
    @Nonnull
    public CompletableFuture<String> startChase(@Nonnull UUID initiator,
                                                @Nonnull List<UUID> party,
                                                @Nonnull RoundPreset preset) {
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

        String roundId = UUID.randomUUID().toString();
        RoundInstance round = new RoundInstance(roundId, KweebecMode.CHASE,
                preset.ruleSet(), System.currentTimeMillis());
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

        CompletableFuture<String> result = new CompletableFuture<>();
        fromWorld.execute(() -> {
            try {
                Store<EntityStore> store = fromWorld.getEntityStore().getStore();
                Ref<EntityStore> ref = initRef.getReference();
                Transform returnPoint = captureReturn(store, ref);

                CompletableFuture<World> worldFuture =
                        InstanceLifecycle.spawnInstance(round.mode().instanceName(), fromWorld, returnPoint);

                // Teleport the initiator in immediately (same world thread), returning
                // them to where they were on exit.
                if (ref != null && ref.isValid()) {
                    InstanceLifecycle.teleportIn(ref, store, worldFuture, returnPoint);
                }
                // Teleport the rest, each on their own current world thread.
                teleportParty(round, initiator, worldFuture);

                worldFuture.whenComplete((instWorld, err) -> {
                    if (err != null || instWorld == null) {
                        KweebecNightmarePlugin.LOGGER.atSevere().log(
                                "[Kweebec] spawnInstance failed: "
                                        + (err != null ? err.getMessage() : "null world"));
                        registry.remove(roundId);
                        result.completeExceptionally(err != null ? err
                                : new IllegalStateException("null instance world"));
                        return;
                    }
                    onInstanceReady(round, instWorld);
                    result.complete(roundId);
                });
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atSevere().log("[Kweebec] startChase failed: " + t.getMessage());
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
                round.setHunterController(new AiHunterController(HUNTER_ROLE));
                ChaseMode.onStart(round);
                round.setState(InstanceState.ACTIVE);

                AtmosphereService.lock(instWorld);
                ArenaBuilder.build(round, instWorld);

                RoundEvents.fireRoundStarted(round.roundId(), round.mode().id(),
                        round.ruleSet().presetId(), round.participantList());

                KweebecNightmarePlugin.LOGGER.atInfo().log(
                        "[Kweebec] round " + round.roundId() + " active ("
                                + round.partySize() + "p, " + round.ruleSet().presetId() + ").");
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atSevere().log(
                        "[Kweebec] onInstanceReady failed: " + t.getMessage());
            }
        });
    }

    // --- resolve (win/lose) ---

    /** End a round with an outcome. Idempotent (first call wins). */
    public void resolve(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        if (!round.claimResolution(outcome)) {
            return;
        }
        boolean win = outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
        round.setState(win ? InstanceState.WON : InstanceState.LOST);
        HeartbeatService.stop(round.roundId());
        MusicBedService.clear(round);

        ChaseState chase = round.chaseState();
        int progress = chase != null ? chase.litShrines() : 0;
        int duration = round.durationSeconds();
        List<UUID> participants = round.participantList();
        World world = round.world();

        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] round " + round.roundId() + " resolved: " + outcome
                        + " (" + duration + "s, " + progress + " shrines).");

        if (world == null) {
            RoundEvents.fireRoundCompleted(round.roundId(), round.mode().id(), outcome,
                    participants, duration, progress);
            registry.remove(round.roundId());
            return;
        }

        world.execute(() -> {
            // Fire the native event on the world thread so listeners can hop safely.
            RoundEvents.fireRoundCompleted(round.roundId(), round.mode().id(), outcome,
                    participants, duration, progress);
            showResult(round, outcome);
            teardown(round, world);
        });

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> finalizeAndEject(round, world),
                RESULT_HOLD_SECONDS, TimeUnit.SECONDS);
    }

    private void showResult(@Nonnull RoundInstance round, @Nonnull RoundCompletedEvent.Outcome outcome) {
        boolean win = outcome == RoundCompletedEvent.Outcome.ESCAPED
                || outcome == RoundCompletedEvent.Outcome.SURVIVED;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound()) {
                continue;
            }
            PlayerRef pr = Universe.get().getPlayer(st.playerId());
            if (pr == null) {
                continue;
            }
            if (win) {
                RoundFeedback.title(pr, Lang.TITLE_YOU_SURVIVED, Lang.TITLE_YOU_SURVIVED_SUB, true);
            } else if (outcome == RoundCompletedEvent.Outcome.TIMED_OUT) {
                RoundFeedback.title(pr, Lang.TITLE_NIGHT_FALLS, Lang.TITLE_NIGHT_FALLS_SUB, true);
            } else {
                RoundFeedback.title(pr, Lang.TITLE_CAUGHT, Lang.TITLE_CAUGHT_SUB, true);
            }
        }
    }

    private void teardown(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (round.hunterController() != null) {
                round.hunterController().despawnAll(world, store);
            }
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

    private void finalizeAndEject(@Nonnull RoundInstance round, @Nonnull World world) {
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
                        reviveThenExit(world, store, ref);
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

    /**
     * Restore a player to ALIVE (remove their {@link DeathComponent} via the engine
     * respawn path) and THEN teleport them out of the instance, so a cocooned
     * (dead-in-place) player never arrives in the overworld with a lingering death
     * state - which the engine PlayerAddedSystem turns into a respawn/death menu (and a
     * SEVERE "wasn't alive but didn't have a pending death message").
     *
     * <p>For an already-alive player (escapee or never cocooned) the respawn future
     * completes immediately with no DeathComponent, so this degrades to a plain exit.
     * Runs on the instance world thread; the post-respawn exit re-hops via
     * {@code world.execute} so it always lands back on the world thread.
     */
    private void reviveThenExit(@Nonnull World world, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref) {
        try {
            DeathComponent.respawn(store, ref).whenComplete((v, err) -> {
                if (err != null) {
                    KweebecNightmarePlugin.LOGGER.atWarning().log(
                            "[Kweebec] revive-before-exit respawn failed: " + err.getMessage());
                }
                world.execute(() -> {
                    try {
                        Store<EntityStore> ws = world.getEntityStore().getStore();
                        if (ref.isValid()) {
                            InstanceLifecycle.exit(ref, ws);
                        }
                    } catch (Throwable t) {
                        KweebecNightmarePlugin.LOGGER.atWarning().log(
                                "[Kweebec] exit-after-revive failed: " + t.getMessage());
                    }
                });
            });
        } catch (Throwable t) {
            // Last-resort: if the respawn call itself throws, still get the player out.
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] reviveThenExit failed, exiting directly: " + t.getMessage());
            if (ref.isValid()) {
                InstanceLifecycle.exit(ref, store);
            }
        }
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
                    reviveThenExit(world, store, ref);
                }
                round.removeHud(uuid);
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] exit failed: " + t.getMessage());
            }
        });
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
