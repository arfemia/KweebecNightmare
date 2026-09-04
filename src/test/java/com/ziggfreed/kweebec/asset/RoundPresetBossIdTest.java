package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.ziggfreed.kweebec.round.RuleSet;

/**
 * A preset's {@code BossId} is an encounter script id, which the engine indexes by file name and reads
 * case-sensitively, so the preset reader must carry it exactly as authored. The fixture is the test's
 * own (a mixed-case id no shipped preset uses), so a content pass never touches this file.
 */
class RoundPresetBossIdTest {

    @Test
    void theBossIdKeepsItsCase() throws Exception {
        RuleSet rules = decode("""
                {
                  "BossEnabled": true,
                  "BossId": "Some_Pack_Boss_Script",
                  "BossBarsGate": true
                }
                """, "fixture");

        assertTrue(rules.bossEnabled());
        assertEquals("Some_Pack_Boss_Script", rules.bossId(), "the encounter script id is carried verbatim");
        assertTrue(rules.bossBarsGate());
    }

    @Test
    void aBlankBossIdMeansNoBoss() throws Exception {
        RuleSet rules = decode("""
                {
                  "BossEnabled": true,
                  "BossId": "  "
                }
                """, "fixture");

        assertTrue(rules.bossEnabled());
        assertNull(rules.bossId(), "a blank id is not a boss");
    }

    private static RuleSet decode(String json, String presetId) throws Exception {
        AssetExtraInfo.Data data = new AssetExtraInfo.Data(RoundPresetAsset.class, presetId, null);
        RoundPresetAsset asset = RoundPresetAsset.CODEC.decodeAndInheritJsonAsset(
                RawJsonReader.fromJsonString(json), null, new AssetExtraInfo<>(data));
        assertNotNull(asset, "the fixture decodes");
        return asset.toRuleSet(presetId);
    }
}
