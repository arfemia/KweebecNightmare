package com.ziggfreed.kweebec.hunter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.encounter.run.EncounterRun;
import com.ziggfreed.common.encounter.run.EncounterRuntime;
import com.ziggfreed.common.encounter.run.EncounterSpawner;
import com.ziggfreed.common.encounter.run.SpawnOptions;
import com.ziggfreed.common.world.SpawnPlacement;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * A round's handle on its hunter waves. The waves themselves are the native encounter script
 * {@code Server/EncounterManager/KweebecNightmare_Hunters.json}, which the engine runs: it reads the
 * round's corruption tier and cleansed-shrine count through the two factors {@link HunterFactors}
 * contributes and fires a {@link ActionKweebecHunterWave} at each rung. What the round keeps is here:
 * {@link #raise standing the encounter up} at the grove's centre the moment the hunt begins, and
 * {@link #dismiss taking it down} at teardown. The hunters a wave puts down are the round's own
 * controller's, not this entity's, so the script authors no cleanup and this handle removes nothing
 * but the encounter entity.
 *
 * <p>World-thread only once created; {@link #raise} completes on the world thread as well.
 */
public final class HunterEncounter {

    /** The hunter waves' script, an id no NPC role carries. */
    public static final String SCRIPT_ID = "KweebecNightmare_Hunters";

    private static final String LOG = "[Kweebec][hunters]";

    private final Ref<EntityStore> encounterRef;
    private final UUID runId;

    private HunterEncounter(@Nonnull Ref<EntityStore> encounterRef, @Nonnull UUID runId) {
        this.encounterRef = encounterRef;
        this.runId = runId;
    }

    /**
     * Stand the round's hunter encounter up at the grove's centre once its chunk is loaded and ticking
     * (the party stands there when the hunt begins, so it is up already; the ask costs nothing).
     * Completes on the world thread with {@code true} when the encounter is up and the round holds its
     * handle, {@code false} when the framework refused the spawn (its binding row switched off, say) or
     * the round ended first, in which case the round runs on with its den roster alone.
     */
    @Nonnull
    public static CompletableFuture<Boolean> raise(@Nonnull RoundInstance round, @Nonnull World world) {
        Vector3d at = centre(world);
        TransformComponent transform = new TransformComponent(at, new Rotation3f(0f, ArenaLayout.SPAWN.yaw(), 0f));
        return EncounterSpawner.spawnWhenLoaded(world, SCRIPT_ID, transform, optionsFor(round))
                .thenApplyAsync(outcome -> adopt(round, world, outcome), world);
    }

    /**
     * What the round asks the framework for: the round id as the run's owner (the factors and the wave
     * action find the round by it), the preset id as its difficulty, no health multiplier (the script
     * binds no subject to scale), and every survivor still in the round as a seeded member.
     */
    @Nonnull
    static SpawnOptions optionsFor(@Nonnull RoundInstance round) {
        return SpawnOptions.forRound(round.roundId(), round.ruleSet().presetId(), 1.0, round.presentPlayerIds());
    }

    private static boolean adopt(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull EncounterSpawner.Outcome outcome) {
        try {
            Ref<EntityStore> ref = outcome.ref();
            if (ref == null) {
                if (outcome.refusal() == EncounterSpawner.Refusal.DISABLED) {
                    SafeLog.info(LOG + " no hunter waves in round " + round.roundId() + ": the " + SCRIPT_ID
                            + " binding is switched off, so the den roster hunts alone");
                } else {
                    SafeLog.warn(LOG + " the hunter encounter did not rise for round " + round.roundId()
                            + " (" + outcome.refusal() + "); the den roster hunts alone");
                }
                return false;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (round.isResolved()) {
                EncounterSpawner.despawn(store, ref, "the round ended before the hunter encounter rose");
                return false;
            }
            EncounterRun run = EncounterRuntime.runOf(store, ref);
            if (run == null) {
                SafeLog.warn(LOG + " the hunter encounter carries no run in round " + round.roundId());
                EncounterSpawner.despawn(store, ref, "no run on the hunter encounter");
                return false;
            }
            round.setHunterEncounter(new HunterEncounter(ref, run.runId()));
            SafeLog.info(LOG + " hunter encounter up for round " + round.roundId() + " (run " + run.shortId() + ")");
            return true;
        } catch (Throwable t) {
            SafeLog.warn(LOG + " adopting the hunter encounter failed in round " + round.roundId() + ": "
                    + t.getMessage());
            return false;
        }
    }

    /** The grove's centre, floor-snapped past the canopy so the encounter entity stands on ground. */
    @Nonnull
    private static Vector3d centre(@Nonnull World world) {
        Anchor centre = ArenaLayout.SPAWN;
        return SpawnPlacement.snapToSurface(world, centre.x(), centre.z(), (int) ArenaLayout.STAND_Y,
                ArenaBuilder.surfaceDecorationKeys());
    }

    /** The run id the framework stamped on the encounter; every encounter event carries it. */
    @Nonnull
    public UUID runId() {
        return runId;
    }

    /** The encounter entity, valid until the round tears it down. */
    @Nonnull
    public Ref<EntityStore> encounterRef() {
        return encounterRef;
    }

    /**
     * Round teardown: remove the encounter entity. The hunters it drew are the round's hunter
     * controller's, which the round has already taken down by then.
     */
    public void dismiss(@Nonnull Store<EntityStore> store) {
        if (encounterRef.isValid()) {
            EncounterSpawner.despawn(store, encounterRef, "round teardown");
        }
    }
}
