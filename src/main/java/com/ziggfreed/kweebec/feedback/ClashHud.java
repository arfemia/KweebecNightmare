package com.ziggfreed.kweebec.feedback;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The CLASH (team PvP) overlay HUD: a match countdown, the coloured team-vs-team score, the viewer's team
 * name, their personal hits/kills tally, and the per-team alive counts. One {@link CustomUIHud} per player;
 * a one-shot {@link #build} appends the .ui and {@link #pushState} rewrites the labels each tick (partial
 * {@code update(false, ...)}). Runs on the world thread. The element-id contract lives in the paired
 * {@code Common/UI/Custom/Hud/KweebecClashHud.ui}.
 *
 * <p>The team-vs-team score is the one coloured line: it goes through the element's {@code TextSpans}
 * property with a lang value carrying native {@code <color is="#hex">} markup the client parses per-locale
 * (the only place rich markup renders; see the i18n rich-text rule). The colour ordering flips by the
 * viewer's team (their own score first, in their team colour), so two score keys exist - one per team.
 */
public final class ClashHud extends CustomUIHud {

    public static final String HUD_KEY = "kweebecnightmare.clash.hud";

    public ClashHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HUD_KEY);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cb) {
        cb.append("Hud/KweebecClashHud.ui");
    }

    /**
     * Push the viewer's current CLASH frame to their HUD. Runs on the world thread.
     *
     * @param s the per-player snapshot {@code ClashMode} built this tick
     */
    public void pushState(@Nonnull ClashHudSnapshot s) {
        UICommandBuilder cb = new UICommandBuilder();

        int rs = Math.max(0, s.remainingSec());
        String clock = String.format("%d:%02d", rs / 60, rs % 60);
        cb.set("#MatchTimer.Text", Lang.msg(Lang.CLASH_HUD_TIMER).param("0", clock));

        // Your-vs-enemy score, coloured per the viewer's team. RED (team 0) renders their own score red and
        // the enemy blue; BLUE (team 1) the reverse. Set on TextSpans so the <color> markup is parsed.
        String scoreKey = s.teamIndex() == 1 ? Lang.CLASH_HUD_SCORE_BLUE : Lang.CLASH_HUD_SCORE_RED;
        cb.set("#TeamScore.TextSpans", Lang.msg(scoreKey)
                .param("0", s.yourScore()).param("1", s.enemyScore()));

        cb.set("#YourTeam.Text", teamName(s.teamIndex()));

        Message tally = s.byKills()
                ? Lang.msg(Lang.CLASH_HUD_TALLY_KILLS).param("0", s.tally())
                : Lang.msg(Lang.CLASH_HUD_TALLY_HITS).param("0", s.tally());
        cb.set("#Tally.Text", tally);

        cb.set("#Alive.Text", Lang.msg(Lang.CLASH_HUD_ALIVE)
                .param("0", Math.max(0, s.aliveYour())).param("1", Math.max(0, s.aliveEnemy())));

        update(false, cb);
    }

    /** The localized team name (RED for team 0, BLUE otherwise), as a nested-resolvable {@link Message}. */
    @Nonnull
    private static Message teamName(int teamIndex) {
        return Lang.msg(teamIndex == 1 ? Lang.CLASH_TEAM_BLUE : Lang.CLASH_TEAM_RED);
    }
}
