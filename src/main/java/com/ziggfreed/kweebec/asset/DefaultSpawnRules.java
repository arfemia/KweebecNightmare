package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline EXTRA-SPAWN RULES, the in-memory {@code defaults} floor
 * {@link SpawnRuleConfig} folds packs on top of. Each fires extra hunters during a round
 * in response to a {@link SpawnRuleAsset.Trigger}, placed via a {@link SpawnRuleAsset.Placement}
 * relative to the survivors (see {@link SpawnRuleAsset}); a pack or the runtime API tunes
 * WHEN / WHERE / HOW MANY as data.
 *
 * <p>The matching {@code Server/KweebecNightmare/SpawnRules/*.json} files are the authoring
 * reference + editor surface (and the engine's DEFAULT_PACK asset layer); this class is the
 * source of truth for the zero-pack case.
 *
 * <p>The {@code of(...)} args are, in order: {@code id, trigger, placement, archetypeId, count,
 * weight, cap, cooldownSeconds, minCorruptionTier, maxPerRound, ringRadius, atSeconds, atTier}.
 */
public final class DefaultSpawnRules {

    /** When a shrine is cleansed, one reinforcement closes in near a random survivor (capped per round). */
    public static final String SHRINE_REINFORCE = "shrine_reinforce";
    /** When the grove rots into a higher corruption tier, a surrounding wave rings the party. */
    public static final String CORRUPTION_WAVE = "corruption_wave";

    private DefaultSpawnRules() {
    }

    /**
     * SHRINE_LIT -> NEAR_RANDOM_PLAYER: each cleansed shrine summons one extra hunter close to a
     * random survivor, up to 3 times a round, with an 8s cooldown so a fast cleanse streak does not
     * stack a swarm at once. Draws from the corruption-eligible roster (no forced archetype).
     */
    @Nonnull
    public static SpawnRuleAsset shrineReinforce() {
        return SpawnRuleAsset.of(SHRINE_REINFORCE, "SHRINE_LIT", "NEAR_RANDOM_PLAYER",
                null, 1, 1.0, 0, 8.0, 0, 3, 14.0, 0, 0);
    }

    /**
     * CORRUPTION_TIER -> RING_AROUND_PLAYERS: crossing into tier 1+ rings the party with a wave of
     * one extra hunter, once per round (a single dramatic surround as the dark deepens). Eligible only
     * from tier 1 onward.
     */
    @Nonnull
    public static SpawnRuleAsset corruptionWave() {
        return SpawnRuleAsset.of(CORRUPTION_WAVE, "CORRUPTION_TIER", "RING_AROUND_PLAYERS",
                null, 1, 1.0, 0, 0.0, 1, 1, 18.0, 0, 0);
    }

    /** All baseline spawn rules, in display order. */
    @Nonnull
    public static List<SpawnRuleAsset> all() {
        return List.of(shrineReinforce(), corruptionWave());
    }
}
