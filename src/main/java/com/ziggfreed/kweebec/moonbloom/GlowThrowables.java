package com.ziggfreed.kweebec.moonbloom;

/**
 * Shared id constants for the GLOW-THROWABLE variants - the sibling family of the {@link Moonbloom}
 * stun shroom. Each is a reskinned vanilla glowing mushroom thrown to burst with a different
 * configurable on-impact effect (the effect itself is authored 100% in the pack burst JSON, no Java):
 *
 * <ul>
 *   <li><b>Gustbloom</b> (blue) - knocks entities back (a peel / escape tool).</li>
 *   <li><b>Mirebloom</b> (green) - slows entities (a kite tool).</li>
 *   <li><b>Emberbloom</b> (orange) - deals real AoE damage; the Chase boss phase spawns harvestable
 *       Emberbloom clusters so survivors can throw them at the Warden.</li>
 * </ul>
 *
 * <p>One authority for the asset ids so the Java (gather/give/placement, the {@code KweebecDamageSystem}
 * Ember friendly-fire guard, the {@code ArenaBuilder} cluster prefab keys) and the pack assets (item /
 * drop / plant block / throw chain / damage cause) never drift. The {@code _PLANT}/{@code _PREFAB} ids
 * mirror the Moonbloom gather loop ({@code Plant} block -> {@code Drop} droplist -> charge item).
 */
public final class GlowThrowables {

    private GlowThrowables() {
    }

    // --- Gustbloom (knockback) ---
    public static final String GUST_ITEM = "KweebecNightmare_Gustbloom";
    public static final String GUST_PLANT = "KweebecNightmare_Gustbloom_Plant";
    /** Prefab key (one plant block) the ArenaBuilder pastes for a Gustbloom cluster. */
    public static final String GUST_PREFAB = "KweebecNightmare/Gustbloom";

    // --- Mirebloom (slow) ---
    public static final String MIRE_ITEM = "KweebecNightmare_Mirebloom";
    public static final String MIRE_PLANT = "KweebecNightmare_Mirebloom_Plant";
    /** Prefab key (one plant block) the ArenaBuilder pastes for a Mirebloom cluster. */
    public static final String MIRE_PREFAB = "KweebecNightmare/Mirebloom";

    // --- Emberbloom (boss-phase damage) ---
    public static final String EMBER_ITEM = "KweebecNightmare_Emberbloom";
    public static final String EMBER_PLANT = "KweebecNightmare_Emberbloom_Plant";
    /** Prefab key (one plant block) the BossController/ArenaBuilder pastes for an Emberbloom cluster. */
    public static final String EMBER_PREFAB = "KweebecNightmare/Emberbloom";

    /** The custom DamageCause the Emberbloom burst deals (so the friendly-fire guard can null it on a survivor). */
    public static final String EMBER_DAMAGE_CAUSE = "KweebecNightmare_EmberHit";
}
