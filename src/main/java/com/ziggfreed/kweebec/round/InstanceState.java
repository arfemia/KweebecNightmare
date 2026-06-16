package com.ziggfreed.kweebec.round;

/**
 * Lifecycle state of a single round instance world. Mirrors the dungeon
 * lifecycle shape: a round is spawned (LOADING), runs (ACTIVE), resolves
 * (WON/LOST), then drains players and is evicted (EVICTING) so the cleanup
 * ticker can remove the world.
 */
public enum InstanceState {

    /** Instance world is being spawned + players teleported in. */
    LOADING,

    /** Round is live and ticking. */
    ACTIVE,

    /** Round resolved as a player win. Players are being shown the result + drained. */
    WON,

    /** Round resolved as a loss (all cocooned or the cap expired). */
    LOST,

    /** Players drained / leaving; the world is queued for removal. */
    EVICTING
}
