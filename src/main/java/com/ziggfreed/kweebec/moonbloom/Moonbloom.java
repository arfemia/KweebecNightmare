package com.ziggfreed.kweebec.moonbloom;

/**
 * Shared id constants for the Moonbloom resource - the gathered glowing-mushroom charge
 * a survivor spends to cleanse a shrine or throws to stun a hunter. One authority for
 * the asset ids so the Java (gather/give/count/spend/placement) and the pack assets
 * (item / drop / plant block / throw chain) never drift.
 */
public final class Moonbloom {

    private Moonbloom() {
    }

    /** The throwable + spendable charge item (drops from a harvested {@link #PLANT_BLOCK}). */
    public static final String CHARGE_ITEM = "KweebecNightmare_Moonbloom";

    /** The harvestable grove plant block; broken/harvested it yields one {@link #CHARGE_ITEM}. */
    public static final String PLANT_BLOCK = "KweebecNightmare_Moonbloom_Plant";

    /** The one-block plant-cluster prefab key the ArenaBuilder pastes for a Moonbloom pickup. */
    public static final String PREFAB = "KweebecNightmare/Moonbloom";
}
