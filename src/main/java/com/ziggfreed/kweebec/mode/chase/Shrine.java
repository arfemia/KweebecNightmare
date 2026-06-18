package com.ziggfreed.kweebec.mode.chase;

/**
 * Shared id constants for the interactable shrine FURNACE - the block a survivor presses F on to
 * submit Moonbloom, which lights with green fire once {@code RuleSet.cleanseCost()} charges are offered.
 * One authority for the asset id + state name so the Java (placement, state toggle, interaction lookup)
 * and the pack assets (the block item, its RootInteraction, its {@code State.Definitions}) never drift.
 *
 * @see com.ziggfreed.kweebec.moonbloom.Moonbloom the resource spent here
 */
public final class Shrine {

    private Shrine() {
    }

    /** The shrine furnace block item id (authored at {@code Server/Item/Items/KweebecNightmare/KweebecNightmare_Shrine.json}). */
    public static final String SHRINE_BLOCK = "KweebecNightmare_Shrine";

    /** The cleansed "lit" interaction state (green fire), authored under the block's {@code State.Definitions}. */
    public static final String LIT_STATE = "lit";
}
