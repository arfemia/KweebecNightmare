package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;

/**
 * The Clash-mode (team PvP brawl) knobs, an immutable grouped VIEW over the flat {@link RuleSet} PvP
 * fields (returned by {@link RuleSet#clash()}). Grouping keeps consumer call sites clean
 * ({@code round.ruleSet().clash().winCondition()}) without a parallel codec authority - the values are
 * authored as flat preset knobs on {@code RoundPresetAsset} and folded into {@link RuleSet}, exactly like
 * every other stake.
 *
 * @param winCondition          how the round is decided (see {@link WinCondition})
 * @param respawnPolicy         whether a death is permanent / limited / infinite (see {@link RespawnPolicy})
 * @param respawnDelaySeconds   seconds before a respawning player is returned to their team spawn
 * @param maxLives              lives under {@link RespawnPolicy#LIMITED} before elimination (>=1)
 * @param scoreToWin            kill/score cap that ends the match early (MOST_KILLS / TDM); 0 = no cap
 * @param suddenDeathSeconds    overtime window on a tie before a DRAW; 0 = resolve to DRAW immediately
 * @param mushroomCadenceSeconds  interval between periodic mushroom-pickup spawn waves; 0 = no waves
 * @param mushroomWaveCount     pickups planted per wave per pickup anchor
 * @param mushroomMaxAlive      approximate cap on concurrent uncollected pickups (harvest-block model)
 * @param mushroomCycle         ordered grove-throwable ids rotated through across waves (e.g. Gust/Moonbloom)
 */
public record ClashConfig(@Nonnull WinCondition winCondition,
                          @Nonnull RespawnPolicy respawnPolicy,
                          int respawnDelaySeconds,
                          int maxLives,
                          int scoreToWin,
                          int suddenDeathSeconds,
                          int mushroomCadenceSeconds,
                          int mushroomWaveCount,
                          int mushroomMaxAlive,
                          @Nonnull String[] mushroomCycle) {

    /** Defensive copy of the rotating pickup-id cycle. */
    @Nonnull
    public String[] mushroomCycle() {
        return mushroomCycle.clone();
    }
}
