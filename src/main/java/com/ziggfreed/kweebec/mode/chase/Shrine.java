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

    /**
     * The cleansed "Lit" interaction state (green fire), authored under the block's {@code State.Definitions}.
     * MUST be PascalCase: the engine generates the block-state asset key {@code *<block>_State_Definitions_Lit}
     * and rejects a lowercase ("lit") definition name with "Asset key ... has incorrect format!".
     */
    public static final String LIT_STATE = "Lit";

    /**
     * World-map / compass marker icon for a DISCOVERED (un-cleansed) shrine - a temple objective, visually
     * distinct from the exit's {@code Portal.png}. Swapped to {@link #LIT_MARKER_ICON} once the shrine lights.
     */
    public static final String MARKER_ICON = "Temple_Gateway.png";

    /** World-map / compass marker icon a shrine swaps to once CLEANSED (a navigable "done" landmark). */
    public static final String LIT_MARKER_ICON = "Campfire.png";
}
