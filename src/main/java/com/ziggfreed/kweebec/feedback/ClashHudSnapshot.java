package com.ziggfreed.kweebec.feedback;

/**
 * An immutable per-player CLASH HUD frame, built once per tick by {@code ClashMode} and pushed whole to one
 * player's {@link ClashHud}. Everything is pre-resolved from the viewing player's perspective (their team,
 * their score vs the enemy's), so {@link ClashHud#pushState} takes ONE arg and does no round lookups.
 *
 * @param remainingSec seconds left before the round cap (clamped at the HUD to {@code >= 0})
 * @param teamIndex    the viewer's 0-based team (0 = RED, 1 = BLUE), drives the team name + score colours
 * @param yourScore    the viewer's team score (hits or kills per {@code byKills})
 * @param enemyScore   the opposing team score
 * @param tally        the viewer's personal tally on the scored stat (their own hits or kills)
 * @param byKills      whether the scored stat is kills ({@code true}) or hits ({@code false}); picks the
 *                     {@code Hits N} vs {@code Kills N} tally label
 * @param aliveYour    survivors still eligible on the viewer's team
 * @param aliveEnemy   survivors still eligible on the opposing team
 */
public record ClashHudSnapshot(int remainingSec, int teamIndex, int yourScore, int enemyScore,
                               int tally, boolean byKills, int aliveYour, int aliveEnemy) {
}
