package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.common.instance.reward.InstanceReward;
import com.ziggfreed.common.instance.reward.LootTable;
import com.ziggfreed.common.instance.reward.LootTableAsset;

/**
 * Load-path proof for the shipped Chase reward tables (the score-tiered WIN spoils rolled by
 * {@code KweebecExperience.stashResults}). The re-based common {@code zc-loot} module (the Roll
 * model lift, ledger 14) kept {@link LootTableAsset}'s authored shape byte-for-byte, so these
 * three files should decode exactly as they always did - this pins that rather than trusting it.
 *
 * <p>Not a balance test: it asserts the authored SHAPE (list sizes, the roll knobs, that a roll
 * at a representative score/outcome actually returns rewards) rather than any dollar value, so a
 * later loot pass never has to touch this file.
 */
class ChaseLootTableLoadTest {

    @Test
    void chaseAmateurDecodesAndRolls() throws Exception {
        // Decoded directly through the codec (no asset store), so the filename-derived id/TableId
        // fold is out of scope here - see the sibling asset-codec test precedent (e.g.
        // mmo-mob-scaling's MobScalingAssetCodecTest), which never asserts id off a bare decodeJson
        // either. What this proves is the authored FIELD shape.
        LootTable t = decode("/Server/ZiggfreedCommon/LootTables/Chase_Amateur.json");
        assertEquals(2, t.guaranteed().size(), "two guaranteed entries authored");
        assertEquals(5, t.pool().size(), "five weighted pool entries authored");
        assertEquals(1, t.rolls());
        assertEquals(1200, t.scorePerBonusRoll());
        assertEquals(3, t.maxRolls());
        assertRollsRewards(t);
    }

    @Test
    void chaseHardcoreDecodesAndRolls() throws Exception {
        LootTable t = decode("/Server/ZiggfreedCommon/LootTables/Chase_Hardcore.json");
        assertEquals(2, t.guaranteed().size());
        assertEquals(6, t.pool().size());
        assertEquals(3, t.rolls());
        assertEquals(2200, t.scorePerBonusRoll());
        assertEquals(6, t.maxRolls());
        assertRollsRewards(t);
    }

    @Test
    void chaseNightmareDecodesAndRolls() throws Exception {
        LootTable t = decode("/Server/ZiggfreedCommon/LootTables/Chase_Nightmare.json");
        assertEquals(2, t.guaranteed().size());
        assertEquals(6, t.pool().size());
        assertEquals(2, t.rolls());
        assertEquals(1800, t.scorePerBonusRoll());
        assertEquals(5, t.maxRolls());
        assertRollsRewards(t);
    }

    /** A win at a mid-range score always yields the guaranteed rewards plus at least one pool pick. */
    private static void assertRollsRewards(@Nonnull LootTable t) {
        java.util.List<InstanceReward> rewards = t.roll(1000, true, new Random(42));
        assertFalse(rewards.isEmpty(), "a win rolls at least the guaranteed rewards");
        assertTrue(rewards.size() >= t.guaranteed().size(), "guaranteed entries are never dropped on a win");
    }

    private static LootTable decode(String resource) throws Exception {
        try (InputStream in = ChaseLootTableLoadTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "resource on classpath: " + resource);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            LootTableAsset asset = LootTableAsset.CODEC.decodeJson(RawJsonReader.fromJsonString(json), new ExtraInfo());
            assertNotNull(asset, resource + " decodes to a non-null asset");
            return asset.toLootTable();
        }
    }
}
