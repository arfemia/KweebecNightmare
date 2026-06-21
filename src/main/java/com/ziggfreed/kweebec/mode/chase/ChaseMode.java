package com.ziggfreed.kweebec.mode.chase;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.instance.zone.ZoneHoldTimer;
import com.ziggfreed.common.sound.BlockStateSound;
import com.ziggfreed.common.worldmap.MapDiscovery;
import com.ziggfreed.common.worldmap.WorldMapMarkers;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.api.RoundCompletedEvent;
import com.ziggfreed.kweebec.hunter.SpawnTrigger;
import com.ziggfreed.kweebec.atmosphere.MusicBedService;
import com.ziggfreed.kweebec.boss.BossController;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.death.CocoonService;
import com.ziggfreed.kweebec.event.KweebecDamageSystem;
import com.ziggfreed.kweebec.feedback.HeartbeatService;
import com.ziggfreed.kweebec.feedback.NightmareHud;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.feedback.ScareDirector;
import com.ziggfreed.kweebec.hunter.HunterController;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.ChasePhase;
import com.ziggfreed.kweebec.round.ExtractionMode;
import com.ziggfreed.kweebec.round.PlayerResync;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * The Chase round loop ("Relight & Escape"), driven once per second from the
 * state machine on the instance world thread. PREP gives the party a breath, then
 * RITUAL spawns the hunter and the heartbeat while survivors channel the grove
 * shrines (corruption climbs with time + per shrine). Lighting the last shrine
 * opens the Heartwood Gate and locks the hunter onto the nearest survivor; the party
 * then escapes by HOLDING the Heartwood platform together for {@code RuleSet.extractionHoldSeconds()}
 * (the co-op extraction, {@link #checkExtraction}); all-cocooned or the cap loses.
 *
 * <p>Stateless: all per-round state lives on {@link RoundInstance}/{@link ChaseState}.
 */
public final class ChaseMode {

    private static final int PREP_SECONDS = 15;
    private static final double RESCUE_SECONDS = 3.0;
    /** World-map POI id + icon for the exit marker placed at gate-open (see {@code openGate}). */
    private static final String EXIT_MARKER_ID = "kweebec_exit";
    private static final String EXIT_MARKER_ICON = "Portal.png";
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
        // Total shrines = the deterministic worldgen surface hosts + the runtime-carved caves. Shrines are
        // discovered via their furnace interaction (no ring, no pre-tracked positions); this is just the
        // win denominator. The surface count is fixed by the biome's shrine-host List props.
        ChaseState chase = new ChaseState(
                ArenaLayout.SURFACE_WORLDGEN_SHRINES + round.ruleSet().caveShrineCount());
        chase.setPhase(ChasePhase.PREP);
        chase.setPrepEndsAtMs(System.currentTimeMillis() + PREP_SECONDS * 1000L);
        // The co-op extraction hold (the reusable ziggfreed-common ZoneHoldTimer); duration is the
        // per-difficulty RuleSet.extractionHoldSeconds(). Fed each ESCAPE tick by checkExtraction.
        chase.setExtractionHold(new ZoneHoldTimer(round.ruleSet().extractionHoldSeconds()));
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
            handleShrines(world, chase);
            maybeRespawnMoonbloom(round, world, chase);
            HunterController hunter = round.hunterController();
            if (hunter != null) {
                hunter.tick(round, world, store);
                // Asset-driven extra-spawn rules: a corruption-tier crossing fires CORRUPTION_TIER once;
                // TIME_ELAPSED + PLAYER_PROXIMITY are evaluated every tick (each gated by its own rule's
                // cooldown / max-per-round / threshold inside the controller). Best-effort.
                int newTier = chase.pollTierIncrease();
                if (newTier >= 0) {
                    hunter.evaluateSpawnRules(round, world, store,
                            SpawnTrigger.CORRUPTION_TIER, newTier);
                }
                hunter.evaluateSpawnRules(round, world, store,
                        SpawnTrigger.TIME_ELAPSED, round.durationSeconds());
                hunter.evaluateSpawnRules(round, world, store,
                        SpawnTrigger.PLAYER_PROXIMITY, 0);
            }
            // Per-survivor horror conductor: proximity vignette (hysteresis) + jumpscare +
            // whisper layer + tier music escalation. Best-effort; never throws into the loop.
            ScareDirector.tick(round, world, store);
        }

        handleRescues(round, world, store);

        // Gate / escape. Lighting the last shrine enters the ESCAPE climax; whether the gate OPENS
        // immediately or stays barred until a capstone boss falls is decided in enterEscape.
        if (chase.phase() == ChasePhase.RITUAL && chase.allShrinesLit() && !chase.isGateOpen()) {
            enterEscape(round, world, store, chase);
        }
        if (chase.phase() == ChasePhase.ESCAPE) {
            // Boss capstone tick: track the Warden's HP, backstop its phase swaps, drive the boss HUD.
            // Best-effort; never throws into the loop.
            BossController boss = round.bossController();
            if (boss != null) {
                boss.tick(round, world, store);
            }
            // A BARRING boss (RuleSet.bossBarsGate) holds the Heartwood Gate shut until it falls: open the
            // gate the instant the Warden is defeated. boss == null here means either a non-barring round
            // (enterEscape already opened the gate) or a barring boss that failed to spawn (enterEscape
            // opened the gate as the no-soft-lock fallback) - in both cases isGateOpen() is already true.
            if (!chase.isGateOpen() && (boss == null || boss.isDefeated())) {
                openGate(round, world, store, chase);
            }
            // Escape is only possible once the gate is physically open (a barring boss blocks it until dead).
            if (chase.isGateOpen()) {
                checkExtraction(round, world, store, chase, now);
            }
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
            // Round-start extra-spawn rules (e.g. an opening flanker near the party). Best-effort.
            hunter.evaluateSpawnRules(round, world, store, SpawnTrigger.ROUND_START, 0);
        }
        HeartbeatService.start(round);
        forEachPresent(round, pr -> RoundFeedback.title(pr,
                Lang.TITLE_HUNT_BEGINS, Lang.TITLE_HUNT_BEGINS_SUB, true));
    }

    // --- shrines ---

    /**
     * Per-tick lit RECONCILER (1.4.0 furnace rework). Shrines are now cleansed by submitting Moonbloom at
     * their interactable furnace ({@link com.ziggfreed.kweebec.interaction.ShrineSubmitInteraction}, which
     * calls {@link #lightShrine}), NOT by a proximity scan. This loop only re-asserts the green-fire "lit"
     * block state for any shrine that is logically lit but whose block has not yet been switched - healing a
     * missed call, a chunk reload, or an {@code ArenaBuilder} +4s/+9s re-paste that reset the block to default.
     */
    private static void handleShrines(@Nonnull World world, @Nonnull ChaseState chase) {
        for (ShrineState shrine : chase.shrines()) {
            if (shrine.isLit() && !shrine.litRendered()) {
                renderLit(world, shrine);
            }
        }
    }

    /**
     * Complete a shrine: mark it lit, bump corruption once, switch its furnace block to the green-fire
     * state, announce it grove-wide, and log the win line. Idempotent (the {@link ShrineState#isLit()} guard
     * means a double press never double-lights or double-counts corruption). Called from the furnace
     * interaction on the world thread (the SAME thread as the 1 Hz tick). The all-shrines-lit -> gate chain
     * in {@link #tick} is unchanged - it polls {@code ChaseState.allShrinesLit()} the same as before.
     */
    public static void lightShrine(@Nonnull RoundInstance round, @Nonnull World world,
                                   @Nonnull ChaseState chase, @Nonnull ShrineState shrine) {
        if (shrine.isLit()) {
            return;
        }
        shrine.setLit(true);
        chase.addCorruption(round.ruleSet().corruptionPerShrine());
        renderLit(world, shrine);
        // The grove catches its breath: lighting a shrine cleanses every survivor's hunter proximity-slow
        // stack, so the next chase starts the escalation fresh (the relief beat the design calls for).
        KweebecDamageSystem.clearAllProximityStacks();
        // One-shot ignite "whoosh" at the furnace (the steady crackle while lit is the native "Lit"-state
        // AmbientSoundEventId). The state's InteractionSoundEventId does NOT fire on a server-set state, so
        // play the authored id ourselves via the mod-agnostic ziggfreed-common helper (the id stays in the
        // block JSON). Best-effort, world-thread.
        Vector3i firePos = shrine.blockPos();
        if (firePos != null) {
            BlockStateSound.playInteractionSound(world, firePos.x(), firePos.y(), firePos.z(),
                    Shrine.LIT_STATE, SoundCategory.SFX, world.getEntityStore().getStore(), "SHRINE_IGNITE");
            // Swap this shrine's map marker to its cleansed "done" icon for anyone who discovered it
            // (no-op when discovery is off / the marker was never registered).
            MapDiscovery discovery = round.mapDiscovery();
            if (discovery != null) {
                discovery.updateIcon(ShrineState.markerPoiId(firePos), Shrine.LIT_MARKER_ICON);
            }
        }
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][win] shrine " + shrine.index() + " CLEANSED ("
                        + chase.litShrines() + "/" + chase.totalShrines() + ")");
        forEachPresent(round, pr -> RoundFeedback.successToast(pr, Lang.TOAST_CLEANSE_DONE));
        // Asset-driven extra-spawn rules: cleansing a shrine can summon reinforcements NEAR the party.
        // Same world thread as the 1 Hz tick, so the store read is safe. Best-effort; the rule layer's
        // own cooldown / max-per-round / cap gate the actual spawn. A toast cues the player if one fires.
        HunterController hunter = round.hunterController();
        if (hunter != null) {
            int liveBefore = hunter.hunterPositions(world.getEntityStore().getStore()).size();
            hunter.evaluateSpawnRules(round, world, world.getEntityStore().getStore(),
                    SpawnTrigger.SHRINE_LIT, 0);
            int liveAfter = hunter.hunterPositions(world.getEntityStore().getStore()).size();
            if (liveAfter > liveBefore) {
                forEachPresent(round, pr -> RoundFeedback.dangerToast(pr, Lang.TOAST_HUNTERS_DRAWN));
            }
        }
    }

    /**
     * Switch a shrine's furnace block to its {@code "lit"} interaction state (green fire), once. No-ops if
     * the block position is unset, the chunk is not yet queryable, or the cell is no longer the shrine
     * furnace (so the per-tick reconciler retries next tick). World-thread only; best-effort.
     */
    private static void renderLit(@Nonnull World world, @Nonnull ShrineState shrine) {
        Vector3i p = shrine.blockPos();
        if (p == null) {
            return;
        }
        try {
            BlockType bt = world.getBlockType(p.x(), p.y(), p.z());
            if (bt == null || bt.getData() == null || bt.getBlockForState(Shrine.LIT_STATE) == null) {
                return; // chunk not ready / not the shrine block; retry next reconcile tick
            }
            world.setBlockInteractionState(p, bt, Shrine.LIT_STATE);
            shrine.setLitRendered(true);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log("[Kweebec] shrine relight render failed: " + t.getMessage());
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
            // Regrow any ENABLED grove throwable (Gust/Mire) on the same wave (RespawnWithWaves entries).
            ArenaBuilder.plantGroveThrowables(round, world, wave, true);
            chase.incrementMoonbloomRespawnsFired();
            forEachPresent(round, pr -> RoundFeedback.warningToast(pr, Lang.TOAST_MOONBLOOM_RESPAWN));
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

    /**
     * Enter the ESCAPE climax (the last shrine just lit): raise corruption to max and decide how the gate
     * behaves. A BARRING boss round (bossEnabled + bossBarsGate, e.g. Nightmare / Hardcore) spawns the
     * Warden and holds the Heartwood Gate SHUT - the gate opens later in {@link #tick} the instant the boss
     * falls. Every other round (no boss, or a non-barring obstacle boss) opens the gate immediately here. If
     * a barring boss fails to spawn we open the gate anyway, so the escape is never soft-locked.
     */
    private static void enterEscape(@Nonnull RoundInstance round, @Nonnull World world,
                                    @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        chase.setPhase(ChasePhase.ESCAPE);
        chase.setCorruption(1.0);
        boolean barring = round.ruleSet().bossEnabled() && round.ruleSet().bossBarsGate();
        boolean bossUp = spawnBossIfEnabled(round, world, store);
        if (barring && bossUp) {
            // The Warden rose to bar the gate: leave it SHUT. boss.spawn already cued the
            // "The Warden Awakens / It rose to bar the gate" title; the gate opens on the boss's defeat.
            KweebecNightmarePlugin.LOGGER.atInfo().log(
                    "[Kweebec][win] all shrines lit (" + chase.litShrines() + "/" + chase.totalShrines()
                            + "); the Warden BARS the Heartwood Gate - defeat it to open the escape.");
            return;
        }
        // No barring boss (or it failed to spawn): open the gate now. A non-barring obstacle boss that just
        // spawned simply stands beside the open gate.
        openGate(round, world, store, chase);
    }

    /**
     * Spawn the round's capstone boss when the rule-set enables one and none is live yet. Returns
     * {@code true} only when a phase-1 boss entity actually spawned (so a barring round can defer the gate
     * on it); {@code false} when no boss is enabled, none resolves, or the spawn no-shows - the caller must
     * then open the gate normally rather than wait on a boss that will never die. World-thread; best-effort.
     */
    private static boolean spawnBossIfEnabled(@Nonnull RoundInstance round, @Nonnull World world,
                                              @Nonnull Store<EntityStore> store) {
        if (!round.ruleSet().bossEnabled() || round.bossController() != null) {
            return false;
        }
        BossController boss = BossController.forRound(round);
        if (boss == null) {
            return false;
        }
        round.setBossController(boss);
        return boss.spawn(round, world, store);
    }

    /**
     * Physically open the Heartwood Gate: reveal the gate prefab, place the exit marker, lock the hunter
     * onto the nearest survivor, and cue the "gate open" beat. Called immediately by {@link #enterEscape}
     * for a non-barring round, or from {@link #tick} the moment a barring boss is defeated. Idempotent.
     */
    private static void openGate(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
        if (chase.isGateOpen()) {
            return;
        }
        chase.setGateOpen(true);
        chase.setAlertFired(true);
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][win] GATE OPEN. Hold ESCAPE z=" + ArenaLayout.ESCAPE.z()
                        + " (radius " + ArenaLayout.ESCAPE_RADIUS + ") as a group for "
                        + fmt(round.ruleSet().extractionHoldSeconds()) + "s to extract.");
        // Reveal the exit platform NOW (the dramatic gate-open beat) - it does NOT stand at the escape from
        // round start. The old purple light archway is gone; the exit is the copied vanilla circle with
        // Moonbloom, pasted here at ArenaLayout.ESCAPE. The win is the co-op hold (checkExtraction).
        ArenaBuilder.pasteExit(world);
        // Place the exit map marker (a world-map / compass POI at the escape) so survivors can find
        // the way out in the dark. Game-mode-asset-driven: the RuleSet.exitMarker() knob (Hardcore
        // ships it off, so its survivors get no marker). The marker lives on this round's own
        // instance world, so it is naturally scoped to the round and dies with the world at teardown.
        if (round.ruleSet().exitMarker()) {
            // Compass updating is the rendering precondition (the world-map tracker only delivers
            // markers while the compass or world map is enabled); enable it so the POI shows.
            world.setCompassUpdating(true);
            WorldMapMarkers.place(world, EXIT_MARKER_ID,
                    ArenaLayout.ESCAPE.x(), ArenaLayout.STAND_Y, ArenaLayout.ESCAPE.z(),
                    EXIT_MARKER_ICON, Lang.msg(Lang.MARKER_EXIT));
        }
        HunterController hunter = round.hunterController();
        if (hunter != null) {
            hunter.onAlert(round, world, store);
        }
        forEachPresent(round, pr -> {
            RoundFeedback.title(pr, Lang.TITLE_GATE_OPEN, Lang.TITLE_GATE_OPEN_SUB, true);
            RoundFeedback.dangerToast(pr, Lang.TOAST_HUNTER_LOCKED);
        });
    }

    /**
     * Co-op extraction hold - the reworked escape (replaces the old "any single survivor reaching the
     * exit / crossing the gate wins"). Each ESCAPE-phase tick (gate open), count the survivors REQUIRED
     * on the Heartwood platform and how many are currently standing on it (within {@link ArenaLayout#ESCAPE}
     * by {@link ArenaLayout#ESCAPE_RADIUS_SQ}). The required set depends on {@link RuleSet#extractionMode()}:
     * <ul>
     *   <li>{@link ExtractionMode#ALL_MOBILE} - every ACTIVE (mobile) survivor; a cocooned teammate does
     *       not block (the team can extract and leave them behind).</li>
     *   <li>{@link ExtractionMode#EVERYONE} - every survivor still able to take part: active OR a
     *       still-rescuable cocoon (which BLOCKS the hold until revived); a PERMANENTLY cocooned teammate
     *       ({@code !isReviveAllowed}) is excused so the round can never soft-lock.</li>
     * </ul>
     * While the WHOLE required group holds the platform, a continuous timer accrues; it RESETS to zero the
     * instant the group breaks (someone steps off or is caught). When the hold reaches
     * {@link RuleSet#extractionHoldSeconds()}, every active survivor is marked escaped at once and the round
     * resolves {@code ESCAPED} next tick (via {@link #resolveOutcome} -> {@code anyEscaped()}).
     *
     * <p><b>Escaped is set ONLY at completion</b>, never per-tick on contact: an on-pad survivor must stay
     * a valid hunter target / scare subject during the hold (setting escaped flips {@code isActive()} to
     * false, which would drop them from hunter targeting and let the group cheese the hold by parking on
     * the platform unthreatened).
     */
    private static void checkExtraction(@Nonnull RoundInstance round, @Nonnull World world,
                                        @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase, long now) {
        UUID worldUuid = world.getWorldConfig().getUuid();
        RuleSet rules = round.ruleSet();
        boolean everyone = rules.extractionMode() == ExtractionMode.EVERYONE;
        int required = 0;
        int onPad = 0;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.hasLeftRound() || st.hasEscaped()) {
                continue;
            }
            if (st.isCocooned()) {
                // A cocooned teammate can never be ON the platform. In EVERYONE mode a STILL-RESCUABLE cocoon is
                // required (so it blocks extraction until a teammate revives them); a cocoon that can no
                // longer be rescued is EXCUSED so the hold can never soft-lock. Gate on canRescue (not just
                // isReviveAllowed) so a BLED-OUT cocoon - downs remaining but past its rescue deadline,
                // which handleRescues will never revive - is excused too, not left blocking forever. In
                // ALL_MOBILE mode a cocoon never counts toward the requirement.
                if (everyone && CocoonService.canRescue(round, st)) {
                    required++;
                }
                continue;
            }
            // Active (mobile) survivor: always required, on the platform iff within the extraction radius.
            required++;
            Ref<EntityStore> ref = presentRef(st.playerId(), worldUuid);
            Vector3d pos = positionOf(store, ref);
            if (pos != null
                    && ArenaLayout.ESCAPE.horizontalDistanceSq(pos.x(), pos.z()) <= ArenaLayout.ESCAPE_RADIUS_SQ) {
                onPad++;
            }
        }
        chase.setExtractionCounts(onPad, required);

        // No one left to extract (every survivor is down/escaped/excused -> required == 0): this is NOT an
        // extraction win, it is a LOSS the round resolves via resolveOutcome's CAUGHT (required == 0 implies
        // activeCount == 0). Reset + skip the hold so a previously-latched completion can never replay
        // "EXTRACTION complete - 0 survivor(s)" every tick (the symptom seen when the round failed to resolve).
        ZoneHoldTimer hold = chase.extractionHold();
        if (hold == null) {
            return; // defensive; set in onStart
        }
        if (required <= 0) {
            hold.reset();
            return;
        }

        // The reusable ZoneHoldTimer owns the continuous-hold-with-reset state machine; we feed it the live
        // counts each tick. It accrues while onPad >= required (resetting the instant the group breaks) and
        // latches complete after RuleSet.extractionHoldSeconds(). A rising edge into "holding" cues the toast.
        boolean wasHolding = hold.isHolding();
        boolean complete = hold.update(onPad, required, now);
        if (!wasHolding && hold.isHolding()) {
            forEachPresent(round, pr -> RoundFeedback.warningToast(pr, Lang.TOAST_EXTRACTION_HOLD));
        }
        if (!complete) {
            return;
        }
        // Hold complete: the whole required group escapes together. At this point every active survivor is
        // on the platform by construction (allHolding required onPad >= required >= activeCount), so marking all
        // active survivors escaped marks exactly the on-pad group; resolveOutcome sees anyEscaped() this tick.
        int escaped = 0;
        for (PlayerRoundState st : round.playerStates()) {
            if (st.isActive()) {
                st.setEscaped(true);
                escaped++;
            }
        }
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec][win] EXTRACTION complete - " + escaped + " survivor(s) held the platform for "
                        + fmt(rules.extractionHoldSeconds()) + "s; round ESCAPED.");
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
        // Extraction line: shown only once the gate is open (the ESCAPE climax). onPad/required are the
        // live "X / Y on the platform" counts; holdRemain is the seconds left in the current continuous hold
        // (the full hold when the group is not yet all assembled, since the timer has not started/has reset).
        boolean extracting = phase == ChasePhase.ESCAPE && chase.isGateOpen();
        int onPad = chase.extractionOnPad();
        int required = chase.extractionRequired();
        int holdRemain = extractionHoldRemaining(round, chase);
        for (PlayerRoundState st : round.playerStates()) {
            Object hud = round.hud(st.playerId());
            if (hud instanceof NightmareHud nh) {
                try {
                    nh.pushState(remaining, phase, lit, total, alive, corruption,
                            extracting, onPad, required, holdRemain);
                } catch (Throwable ignored) {
                    // HUD is non-essential
                }
            }
        }
    }

    /** Seconds left in the current extraction hold (the full hold when not yet started / reset). */
    private static int extractionHoldRemaining(@Nonnull RoundInstance round, @Nonnull ChaseState chase) {
        ZoneHoldTimer hold = chase.extractionHold();
        if (hold == null) {
            return (int) Math.ceil(Math.max(0.0, round.ruleSet().extractionHoldSeconds()));
        }
        return hold.remainingSeconds(System.currentTimeMillis());
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
