package com.ziggfreed.kweebec.boss;

import java.util.Collection;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.encounter.event.EncounterDefeatedEvent;
import com.ziggfreed.common.encounter.event.EncounterEngagedEvent;
import com.ziggfreed.common.encounter.event.EncounterPhaseChangedEvent;
import com.ziggfreed.common.encounter.event.EncounterResetEvent;
import com.ziggfreed.common.encounter.event.EncounterSignalEvent;
import com.ziggfreed.kweebec.mode.chase.ChaseMode;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * How the round hears the Warden fight: this mod's one inbound listener on the engine event bus. The
 * ziggfreed-common encounter framework fires a native event at every beat of the fight, each carrying the
 * run id; a live round whose {@link BossEncounter} holds that run id is the one the beat belongs to, and
 * the reaction is the round's own policy: the marker and the Emberbloom supply on the engage and each
 * phase, the Heartwood Gate opening on the defeat, and the no-soft-lock fallbacks (the script's own
 * no-show beat, or a run that ended some other way before the gate opened) opening the gate rather than
 * leaving the party barred behind a boss that will never fall.
 *
 * <p>A listener runs synchronously on the world thread the event fired from, which is the round's own
 * instance world, and often from inside the engine system that decided the beat (the defeat lands from
 * the death system's tick). Each reaction therefore hops through {@code world.execute}, the round's own
 * task queue, so it runs between ticks the way the round's 1 Hz loop does rather than inside another
 * system's iteration. Registered once from the plugin's {@code setup()} through the plugin's event
 * registry, so it is unregistered with the plugin.
 */
public final class BossEncounterListener {

    /**
     * The beat the Warden script sends when no boss bound within its Intro timeout (the marker did not
     * synthesize, the role failed to load): the round opens the gate on it.
     */
    public static final String NO_SHOW_SIGNAL = "zc:kweebec:no_show";

    private BossEncounterListener() {
    }

    /** Subscribe to the five beats the round reacts to. Call once from plugin setup. */
    public static void install(@Nonnull JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(EncounterEngagedEvent.class, BossEncounterListener::onEngaged);
        plugin.getEventRegistry().registerGlobal(EncounterPhaseChangedEvent.class, BossEncounterListener::onPhaseChanged);
        plugin.getEventRegistry().registerGlobal(EncounterDefeatedEvent.class, BossEncounterListener::onDefeated);
        plugin.getEventRegistry().registerGlobal(EncounterSignalEvent.class, BossEncounterListener::onSignal);
        plugin.getEventRegistry().registerGlobal(EncounterResetEvent.class, BossEncounterListener::onReset);
    }

    private static void onEngaged(@Nonnull EncounterEngagedEvent event) {
        react(event.runId(), "engage", m -> m.boss().onEngaged(m.round(), m.world(), m.store()));
    }

    private static void onPhaseChanged(@Nonnull EncounterPhaseChangedEvent event) {
        int phaseIndex = event.phaseIndex();
        react(event.runId(), "phase", m -> m.boss().onPhase(m.round(), m.world(), m.store(), phaseIndex));
    }

    private static void onDefeated(@Nonnull EncounterDefeatedEvent event) {
        react(event.runId(), "defeat", m -> {
            m.boss().onEnded(m.world());
            SafeLog.info("[Kweebec][boss] Warden DEFEATED in round " + m.round().roundId());
            ChaseMode.openGate(m.round(), m.world(), m.store(), m.chase());
        });
    }

    private static void onSignal(@Nonnull EncounterSignalEvent event) {
        if (!NO_SHOW_SIGNAL.equals(event.signalId())) {
            return;
        }
        react(event.runId(), "no-show", m -> {
            SafeLog.warn("[Kweebec][boss] the Warden never rose in round " + m.round().roundId()
                    + "; opening the gate so the escape is not soft-locked");
            m.boss().onEnded(m.world());
            ChaseMode.openGate(m.round(), m.world(), m.store(), m.chase());
        });
    }

    /**
     * A run ends with a reset, always last: after the defeat (nothing left to do), or before the gate ever
     * opened (a timeout, an admin ending the encounter), in which case the gate opens now.
     */
    private static void onReset(@Nonnull EncounterResetEvent event) {
        String reason = String.valueOf(event.reason());
        react(event.runId(), "reset", m -> {
            if (m.chase().isGateOpen()) {
                return;
            }
            SafeLog.warn("[Kweebec][boss] the Warden fight ended (" + reason + ") before the gate opened in round "
                    + m.round().roundId() + "; opening it");
            m.boss().onEnded(m.world());
            ChaseMode.openGate(m.round(), m.world(), m.store(), m.chase());
        });
    }

    // --- matching a beat to its round ---

    /** A live round and its fight, resolved for one beat. */
    private record Match(@Nonnull RoundInstance round, @Nonnull BossEncounter boss, @Nonnull World world,
                         @Nonnull Store<EntityStore> store, @Nonnull ChaseState chase) {
    }

    /** What a reaction does with its match. */
    private interface Reaction {
        void run(@Nonnull Match match);
    }

    /**
     * Match {@code runId} to its live round and run {@code reaction} on that round's world thread between
     * ticks (a beat nobody owns, or one for a round already resolved, is ignored). The match is re-taken
     * inside the hop, so a round that resolved in between is skipped rather than acted on.
     */
    private static void react(@Nonnull UUID runId, @Nonnull String what, @Nonnull Reaction reaction) {
        Match m = match(runId);
        if (m == null) {
            return;
        }
        try {
            m.world().execute(() -> {
                Match live = match(runId);
                if (live == null) {
                    return;
                }
                try {
                    reaction.run(live);
                } catch (Throwable t) {
                    SafeLog.warn("[Kweebec][boss] " + what + " reaction failed in round " + live.round().roundId()
                            + ": " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec][boss] could not queue the " + what + " reaction for round "
                    + m.round().roundId() + ": " + t.getMessage());
        }
    }

    @Nullable
    private static Match match(@Nonnull UUID runId) {
        RoundInstance round = roundFor(RoundService.getInstance().registry().all(), runId);
        if (round == null || round.isResolved()) {
            return null;
        }
        BossEncounter boss = round.bossEncounter();
        World world = round.world();
        ChaseState chase = round.chaseState();
        if (boss == null || world == null || chase == null) {
            return null;
        }
        return new Match(round, boss, world, world.getEntityStore().getStore(), chase);
    }

    /** The round whose Warden fight carries {@code runId}, or null when no live round does. */
    @Nullable
    static RoundInstance roundFor(@Nonnull Collection<RoundInstance> rounds, @Nonnull UUID runId) {
        for (RoundInstance round : rounds) {
            if (runId.equals(BossEncounter.runIdOf(round.bossEncounter()))) {
                return round;
            }
        }
        return null;
    }
}
