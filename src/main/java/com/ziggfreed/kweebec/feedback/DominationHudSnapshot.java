package com.ziggfreed.kweebec.feedback;

/**
 * An immutable per-player DOMINATION HUD frame, built once per tick by {@code DominationMode} and pushed
 * whole to one player's {@link DominationHud}. Pre-resolved from the viewing player's perspective (their
 * team, their points vs the enemy's), so {@link DominationHud#pushState} takes ONE arg.
 *
 * @param remainingSec seconds left before the round cap (clamped at the HUD to {@code >= 0})
 * @param teamIndex    the viewer's 0-based team (0 = RED, 1 = BLUE), drives the team name + score colours
 * @param yourScore    the viewer's team accrued points
 * @param enemyScore   the opposing team accrued points
 * @param capturePct   the leading control-point capture progress 0..100 (the max in-flight capture across
 *                     all points), shown as a live "contest" readout
 */
public record DominationHudSnapshot(int remainingSec, int teamIndex, int yourScore, int enemyScore,
                                    int capturePct) {
}
