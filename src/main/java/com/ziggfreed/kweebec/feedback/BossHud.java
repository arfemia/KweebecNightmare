package com.ziggfreed.kweebec.feedback;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
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

    /** Number of colour segments the .ui authors for the HP bar (5% granularity over a 400px track). */
    private static final int HP_SEGMENTS = 20;

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
        // Only show the "Phase X/Y" indicator when the boss actually has multiple phases (the
        // later phases carry an authored role). A single-phase Warden (phases 2/3 disabled) blanks
        // the line rather than reading a meaningless "Phase 1/1". The blank MUST be Message.raw("")
        // (a real empty rawText string), NOT Message.empty(): an empty Message serializes as a
        // FormattedMessage object with no text/translationKey, and the client's String-typed `.Text`
        // setter cannot construct a System.String from it - it hard-disconnects with "CustomUI Set
        // command couldn't set value ... No parameterless constructor defined for type 'System.String'".
        if (totalPhases > 1) {
            cb.set("#BossPhase.Text", Lang.msg(Lang.BOSS_HUD_PHASE)
                    .param("0", Math.max(1, phase)).param("1", totalPhases));
        } else {
            cb.set("#BossPhase.Text", Message.raw(""));
        }

        double fraction = max > 0.0 ? Math.max(0.0, Math.min(1.0, current / max)) : 0.0;
        // Toggle each HP segment's .Visible (the bar is 20 fixed colour Groups, NOT a width-resized
        // fill: a Group's .Width is NOT a runtime-settable CustomUI selector and the client
        // hard-disconnects with "Set command selector doesn't match a markup property". .Visible IS
        // settable. Show ceil(fraction * N) segments left-to-right; keep >=1 lit while the boss lives.
        int visibleSegments = (int) Math.ceil(fraction * HP_SEGMENTS);
        if (fraction > 0.0 && visibleSegments < 1) {
            visibleSegments = 1;
        }
        for (int i = 0; i < HP_SEGMENTS; i++) {
            cb.set("#BossHpSeg" + i + ".Visible", i < visibleSegments);
        }

        update(false, cb);
    }
}
