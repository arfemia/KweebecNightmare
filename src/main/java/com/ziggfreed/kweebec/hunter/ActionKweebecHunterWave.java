package com.ziggfreed.kweebec.hunter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.ziggfreed.common.encounter.run.EncounterLifecycle;
import com.ziggfreed.common.encounter.run.EncounterRuns;
import com.ziggfreed.common.encounter.run.ZigEncounterRun;
import com.ziggfreed.kweebec.feedback.RoundFeedback;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The one thing the hunter encounter script cannot say natively: put a body down relative to a MOVING
 * survivor, sized to the party. The action reads the run off the executing encounter entity, finds
 * the chase round that owns it by the run's owner key, and hands the wave to that round's
 * {@link HunterController#spawnWave} between ticks, so placement, the live ceiling, the archetype
 * ladder, the speed bands and the roster bookkeeping all stay where they live. When {@code Announce}
 * is set and a hunter actually appeared, every present survivor is told the light drew them.
 *
 * <p>It ALWAYS answers the engine finished: a {@code false} from {@code execute} is the engine's "still
 * running", and a blocking action list would wait on it forever. Nothing to do, and a failure, both
 * say so in the log and answer true.
 */
public class ActionKweebecHunterWave extends ActionBase {

    private static final String LOG = "[Kweebec][hunters]";

    private final HunterWave wave;

    public ActionKweebecHunterWave(@Nonnull BuilderKweebecHunterWave builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.wave = builder.wave(support);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport executionSupport,
            @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, executionSupport, sensorInfo, dt, store);
        try {
            ZigEncounterRun run = EncounterRuns.runOn(store, ref);
            String owner = run == null ? null : run.ownerKey();
            RoundInstance round = owner == null ? null : RoundService.getInstance().registry().byId(owner);
            World world = EncounterLifecycle.worldOf(store);
            if (round == null || world == null || round.isResolved() || round.hunterController() == null) {
                SafeLog.info(LOG + " " + HunterWaveType.NAME + " ran on an encounter no live chase round owns"
                        + (owner == null ? "" : " (owner " + owner + ")") + "; nothing to spawn");
                return true;
            }
            // The round's own task queue, so the spawn lands between ticks the way the round's 1 Hz
            // loop does rather than inside the encounter entity's own instruction tick.
            world.execute(() -> spawn(round, world));
        } catch (Throwable t) {
            SafeLog.warn(LOG + " " + HunterWaveType.NAME + " failed: " + t.getMessage());
        }
        return true;
    }

    private void spawn(@Nonnull RoundInstance round, @Nonnull World world) {
        try {
            HunterController hunter = round.hunterController();
            if (hunter == null || round.isResolved()) {
                return;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            int spawned = hunter.spawnWave(round, world, store, wave);
            if (spawned > 0 && wave.announce()) {
                round.forEachPresent(pr -> RoundFeedback.dangerToast(pr, Lang.TOAST_HUNTERS_DRAWN));
            }
        } catch (Throwable t) {
            SafeLog.warn(LOG + " a hunter wave failed in round " + round.roundId() + ": " + t.getMessage());
        }
    }
}
