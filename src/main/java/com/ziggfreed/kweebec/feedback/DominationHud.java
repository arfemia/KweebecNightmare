package com.ziggfreed.kweebec.feedback;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The DOMINATION (control-point PvP) overlay HUD: a match countdown, the coloured team-vs-team points score,
 * the viewer's team name, and a live leading-capture readout. One {@link CustomUIHud} per player; a one-shot
 * {@link #build} appends the .ui and {@link #pushState} rewrites the labels each tick (partial
 * {@code update(false, ...)}). Runs on the world thread. The element-id contract lives in the paired
 * {@code Common/UI/Custom/Hud/KweebecDominationHud.ui}.
 *
 * <p>Mirrors {@link ClashHud}: the team-vs-team score line goes through {@code TextSpans} with a per-team
 * coloured lang value (your own points first, in your team colour).
 */
public final class DominationHud extends CustomUIHud {

    public static final String HUD_KEY = "kweebecnightmare.domination.hud";

    public DominationHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_KEY);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cb) {
        cb.append("Hud/KweebecDominationHud.ui");
    }

    /**
     * Push the viewer's current DOMINATION frame to their HUD. Runs on the world thread.
     *
     * @param s the per-player snapshot {@code DominationMode} built this tick
     */
    public void pushState(@Nonnull DominationHudSnapshot s) {
        UICommandBuilder cb = new UICommandBuilder();

        int rs = Math.max(0, s.remainingSec());
        String clock = String.format("%d:%02d", rs / 60, rs % 60);
        cb.set("#MatchTimer.Text", Lang.msg(Lang.CLASH_HUD_TIMER).param("0", clock));

        String scoreKey = s.teamIndex() == 1 ? Lang.CLASH_HUD_SCORE_BLUE : Lang.CLASH_HUD_SCORE_RED;
        cb.set("#TeamScore.TextSpans", Lang.msg(scoreKey)
                .param("0", s.yourScore()).param("1", s.enemyScore()));

        cb.set("#YourTeam.Text", teamName(s.teamIndex()));

        int pct = Math.max(0, Math.min(100, s.capturePct()));
        cb.set("#Capture.Text", Lang.msg(Lang.CLASH_HUD_CAPTURE).param("0", pct));

        update(false, cb);
    }

    /** The localized team name (RED for team 0, BLUE otherwise), as a nested-resolvable {@link Message}. */
    @Nonnull
    private static Message teamName(int teamIndex) {
        return Lang.msg(teamIndex == 1 ? Lang.CLASH_TEAM_BLUE : Lang.CLASH_TEAM_RED);
    }
}
