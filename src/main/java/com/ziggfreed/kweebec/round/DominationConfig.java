package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;

import com.ziggfreed.common.instance.zone.ContestRule;

/**
 * The Domination-mode (control-point PvP) knobs, an immutable grouped VIEW over the flat {@link RuleSet}
 * domination fields (returned by {@link RuleSet#domination()}). The control engine is generic over N
 * points (King-of-the-Hill = one objective anchor, classic Domination = three) - the point LOCATIONS are
 * authored on the arena ({@code ArenaDefinitionAsset.objectiveAnchors}), so a 3-point variant is pure data.
 * Respawn is always on for Domination (it is a hold objective, not elimination); the delay is here.
 *
 * @param scoreToWin           points a team must accrue to win; highest at the timer wins otherwise (tie = DRAW)
 * @param pointHoldSeconds     uncontested seconds a team must hold a point to flip its control (the capture latch)
 * @param accrualPerSecond     points per held point per second to the controlling team
 * @param pointRadius          fallback occupancy radius when an objective anchor authors none
 * @param contestRule          when an enemy presence blocks capture (see common {@link ContestRule})
 * @param captureNeutralizes   whether a contest resets in-progress capture (true) or only freezes it (false)
 * @param respawnDelaySeconds  seconds before a killed player respawns at their team spawn (always-on respawn)
 */
public record DominationConfig(int scoreToWin,
                               double pointHoldSeconds,
                               int accrualPerSecond,
                               double pointRadius,
                               @Nonnull ContestRule contestRule,
                               boolean captureNeutralizes,
                               int respawnDelaySeconds) {
}
