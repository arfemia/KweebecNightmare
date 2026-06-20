package com.ziggfreed.kweebec.mode.clash;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.universe.world.World;
import com.ziggfreed.common.instance.arena.ArenaDefinitionAsset;
import com.ziggfreed.kweebec.arena.ArenaBuilder;
import com.ziggfreed.kweebec.moonbloom.GlowThrowables;
import com.ziggfreed.kweebec.moonbloom.Moonbloom;
import com.ziggfreed.kweebec.round.ClashConfig;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * Periodic mushroom-pickup spawner for the PvP modes (the "knockback + stun mushrooms periodically spawn"
 * arsenal). On a fixed cadence it plants harvestable Gustbloom (knockback) and Moonbloom (stun) clusters at
 * the arena's pickup anchors, rotating through the configured cycle so both teams can grab them mid-fight.
 * Reuses the existing {@code ArenaBuilder} grove-throwable gather loop ({@code plantAtPoints}) - a harvested
 * cluster drops the throwable charge, thrown via the existing burst chain. Cadence / wave size / cycle are
 * asset-driven ({@link ClashConfig}). Stateless across calls: the wave counter lives on the mode state.
 *
 * <p>The harvest-block substrate cannot precisely count uncollected pickups, so {@code mushroomMaxAlive} is
 * an approximate cap honored by NOT spawning a new wave while one is still recent (documented limitation).
 */
public final class ClashPickupSpawner {

    /** Default rotation when a preset authors no {@code MushroomCycle}: knockback then stun. */
    private static final String[] DEFAULT_CYCLE = { GlowThrowables.GUST_PREFAB, Moonbloom.PREFAB };

    private ClashPickupSpawner() {
    }

    /**
     * Spawn any pickup waves now due (catch-up loop). {@code wavesFired} is the count already spawned; the
     * return value is the new count the caller stores on its mode state. World-thread caller (the paste hops
     * off-thread + back internally).
     */
    public static int tick(@Nonnull RoundInstance round, @Nonnull World world, int wavesFired) {
        ArenaDefinitionAsset arena = round.arena();
        if (arena == null || arena.pickupAnchors().isEmpty()) {
            return wavesFired;
        }
        ClashConfig c = round.ruleSet().clash();
        int cadence = c.mushroomCadenceSeconds();
        if (cadence <= 0) {
            return wavesFired;
        }
        int waveSize = Math.max(1, c.mushroomWaveCount());
        int due = round.durationSeconds() / cadence;
        String[] cycle = resolveCycle(c);
        List<ArenaDefinitionAsset.PickupAnchor> anchors = arena.pickupAnchors();
        while (wavesFired < due) {
            String prefab = cycle[wavesFired % cycle.length];
            List<double[]> points = new ArrayList<>(waveSize);
            for (int i = 0; i < waveSize; i++) {
                ArenaDefinitionAsset.PickupAnchor a = anchors.get(i % anchors.size());
                points.add(new double[]{ a.x(), a.z() });
            }
            ArenaBuilder.plantAtPoints(world, prefab, points);
            wavesFired++;
        }
        return wavesFired;
    }

    @Nonnull
    private static String[] resolveCycle(@Nonnull ClashConfig c) {
        String[] authored = c.mushroomCycle();
        return authored.length > 0 ? authored : DEFAULT_CYCLE;
    }
}
