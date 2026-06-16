package com.ziggfreed.kweebec.hunter;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * The hunter seam: everything the round engine needs to drive a pursuer without
 * caring whether it is AI-driven (the only implementation today,
 * {@link AiHunterController}) or human-driven (the asymmetric "play as the
 * Kweebec" mode, architected-for but built post-jam).
 *
 * <p>ALL methods run on the instance world thread (the state machine hops via
 * {@code world.execute} before calling them).
 */
public interface HunterController {

    /** Spawn the hunter(s) for this round (called once when the ritual / hunt begins). */
    void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * Per-tick update: re-assert the marked target on the chosen survivor(s) (the
     * engine clears a marked player target each tick unless re-asserted) and apply
     * the corruption-scaled speed ramp.
     */
    void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /**
     * The Heartwood Gate alert: hard-lock every hunter onto the single nearest
     * survivor for the final chase to the exit.
     */
    void onAlert(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store);

    /** Remove all hunters (round end / teardown). */
    void despawnAll(@Nonnull World world, @Nonnull Store<EntityStore> store);
}
