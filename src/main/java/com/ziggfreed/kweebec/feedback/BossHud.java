package com.ziggfreed.kweebec.feedback;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The boss-capstone health-bar HUD: the Warden's localized name, a phase indicator, and an HP bar.
 * Installed on every survivor when the Warden spawns at the escape climax (see {@code boss/BossController}),
 * stripped on the boss's death / round end. Updates are partial ({@code update(false, ...)}). The element-id
 * contract lives in the paired {@code Common/UI/Custom/Hud/KweebecBossHud.ui}.
 *
 * <p>Mirrors {@link NightmareHud}: one {@link CustomUIHud} per player, a one-shot {@link #build} appends the
 * .ui, and {@link #pushHealth} rewrites the labels + the HP-fill width each tick. Runs on the world thread.
 */
public final class BossHud extends CustomUIHud {

    public static final String HUD_KEY = "kweebecnightmare.boss.hud";

    /** The HP-bar track width (blocks/px) the .ui authors; the red fill width is fraction * this. */
    private static final int HP_TRACK_WIDTH = 400;

    public BossHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_KEY);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cb) {
        cb.append("Hud/KweebecBossHud.ui");
    }

    /**
     * Push the boss's current snapshot to this player's HUD. Runs on the world thread.
     *
     * @param current     current HP of the live phase entity
     * @param max         max HP of the live phase entity (the per-phase MaxHealth)
     * @param phase       current phase number (1-based)
     * @param totalPhases total phase count (for the "Phase X/Y" indicator)
     */
    public void pushHealth(double current, double max, int phase, int totalPhases) {
        UICommandBuilder cb = new UICommandBuilder();

        cb.set("#BossName.Text", Lang.msg(Lang.NPC_WARDEN));
        cb.set("#BossPhase.Text", Lang.msg(Lang.BOSS_HUD_PHASE)
                .param("0", Math.max(1, phase)).param("1", Math.max(1, totalPhases)));

        double fraction = max > 0.0 ? Math.max(0.0, Math.min(1.0, current / max)) : 0.0;
        int fillWidth = (int) Math.round(fraction * HP_TRACK_WIDTH);
        cb.set("#BossHpFill.Anchor.Width", fillWidth);

        update(false, cb);
    }
}
