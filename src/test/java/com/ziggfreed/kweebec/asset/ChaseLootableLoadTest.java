package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.loot.FactorLookup;
import com.ziggfreed.common.loot.LootEngine;
import com.ziggfreed.common.loot.LootFactors;
import com.ziggfreed.common.loot.LootableAsset;

/**
 * Load-path proof for the shipped Chase reward tables (the score-tiered WIN spoils
 * {@code KweebecExperience.stashResults} decides at round resolve).
 *
 * <p>Not a balance test: it asserts the authored SHAPE (how many entries, that the pick count rises
 * with score and stops at its ceiling, that a win at a representative score actually decides on
 * something) rather than any one quantity, so a later loot pass never has to touch this file.
 */
class ChaseLootableLoadTest {

    @Test
    void chaseAmateurDecodesAndDecidesRewards() throws Exception {
        LootableAsset t = decode("/Server/ZiggfreedCommon/Lootables/Chase_Amateur.json");
        assertEquals(1, t.rollsOrEmpty().size(), "one guaranteed payout authored");
        assertNotNull(t.getPool());
        assertEquals(5, t.getPool().getEntries().length, "five competing entries authored");
        assertPickCountClimbsAndStops(t, 1200, 1, 3);
        assertDecidesSomething(t);
    }

    @Test
    void chaseHardcoreDecodesAndDecidesRewards() throws Exception {
        LootableAsset t = decode("/Server/ZiggfreedCommon/Lootables/Chase_Hardcore.json");
        assertEquals(1, t.rollsOrEmpty().size());
        assertNotNull(t.getPool());
        assertEquals(6, t.getPool().getEntries().length);
        assertPickCountClimbsAndStops(t, 2200, 3, 6);
        assertDecidesSomething(t);
    }

    @Test
    void chaseNightmareDecodesAndDecidesRewards() throws Exception {
        LootableAsset t = decode("/Server/ZiggfreedCommon/Lootables/Chase_Nightmare.json");
        assertEquals(1, t.rollsOrEmpty().size());
        assertNotNull(t.getPool());
        assertEquals(6, t.getPool().getEntries().length);
        assertPickCountClimbsAndStops(t, 1800, 2, 5);
        assertDecidesSomething(t);
    }

    /**
     * A score of exactly one threshold buys exactly one more pick, and a runaway score still stops at
     * the authored ceiling. The threshold case is the one that matters: the weight is one divided by
     * a whole number, which no binary double holds exactly, so a player sitting on the line has to
     * come out ahead rather than a hair short.
     */
    private static void assertPickCountClimbsAndStops(@Nonnull LootableAsset table, int perBonusPick,
            int base, int ceiling) {
        assertEquals(base, picks(table, 0));
        assertEquals(base + 1, picks(table, perBonusPick),
                "a score of exactly one threshold earns the pick it was promised");
        assertEquals(base, picks(table, perBonusPick - 1));
        assertEquals(ceiling, picks(table, 10_000_000), "the ceiling holds however good the run");
    }

    /** A win at a mid-range score always decides on at least the guaranteed payout. */
    private static void assertDecidesSomething(@Nonnull LootableAsset table) {
        Random rng = new Random(42);
        List<LootEngine.Selected> decided = LootEngine.select(table.rollsOrEmpty(), table.poolOrEmpty(),
                null, LootFactors.lookupFor(1000, true), rng::nextDouble);
        assertFalse(decided.isEmpty(), "a win decides on at least the guaranteed payout");
        assertTrue(decided.stream().anyMatch(s -> s.grants() != null && !s.grants().isEmpty()));
    }

    /** Nothing is decided at all on a loss, since every authored gate reads the win. */
    @Test
    void aLostRunDecidesNothing() throws Exception {
        LootableAsset t = decode("/Server/ZiggfreedCommon/Lootables/Chase_Nightmare.json");
        Random rng = new Random(9);
        assertTrue(LootEngine.select(t.rollsOrEmpty(), t.poolOrEmpty(), null,
                LootFactors.lookupFor(9000, false), rng::nextDouble).isEmpty());
    }

    private static int picks(@Nonnull LootableAsset table, int score) {
        FactorLookup lookup = LootFactors.lookupFor(score, true);
        return table.getPool().pickCount(lookup);
    }

    private static LootableAsset decode(String resource) throws Exception {
        try (InputStream in = ChaseLootableLoadTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "resource on classpath: " + resource);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Decoded directly through the codec (no asset store), so the filename-derived id and the
            // contribution fold are out of scope here - what this proves is the authored FIELD shape.
            AssetExtraInfo.Data data = new AssetExtraInfo.Data(LootableAsset.class, "chase", null);
            LootableAsset asset = LootableAsset.CODEC.decodeAndInheritJsonAsset(
                    RawJsonReader.fromJsonString(json), null, new AssetExtraInfo<>(data));
            assertNotNull(asset, resource + " decodes to a non-null asset");
            return asset;
        }
    }
}
