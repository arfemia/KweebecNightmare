package com.ziggfreed.kweebec.asset;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * The jar's baseline BOSS CAPSTONES, the in-memory {@code defaults} floor {@link BossConfig} folds
 * packs on top of. Each describes the multi-phase corrupted-Kweebec Warden {@code boss/BossController}
 * spawns at the escape climax (see {@link BossAsset}); a pack tunes the roles / thresholds / adds / cues
 * as data.
 *
 * <p>The matching {@code Server/KweebecNightmare/Bosses/*.json} files are the authoring reference + editor
 * surface (and the engine's DEFAULT_PACK asset layer); this class is the source of truth for the zero-pack
 * case.
 *
 * <p>The {@code of(...)} args are, in order: {@code id, nameKey, phase1Role, phase2Role, phase3Role,
 * phase2ThresholdFraction, phase3ThresholdFraction, phase1AddCount, phase2AddCount, phase3AddCount,
 * addRole, addCap, spawnSoundId, phaseSwapSoundId}.
 */
public final class DefaultBosses {

    /** The default boss id (the corrupted-Kweebec Warden). */
    public static final String WARDEN = "warden";

    private DefaultBosses() {
    }

    /**
     * The Warden: three phases at 600 / 450 / 300 HP (the role MaxHealths), swapping at 50% and 25% of
     * the CURRENT phase's HP. Summons 0 / 2 / 3 Blight adds on entering each phase (capped at 4 live
     * adds). Spawn + phase-swap sound ids are left blank by default (a pack supplies a roar); the boss
     * still spawns and swaps silently without them.
     */
    @Nonnull
    public static BossAsset warden() {
        return BossAsset.of(WARDEN, "kweebecnightmare.npc.warden.name",
                "KweebecNightmare_Warden",
                "KweebecNightmare_Warden_Phase2",
                "KweebecNightmare_Warden_Phase3",
                0.5, 0.25,
                0, 2, 3,
                "KweebecNightmare_Blight", 4,
                "", "");
    }

    /** All baseline bosses, in display order. */
    @Nonnull
    public static List<BossAsset> all() {
        return List.of(warden());
    }
}
