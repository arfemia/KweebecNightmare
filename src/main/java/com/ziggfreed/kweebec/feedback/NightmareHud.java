package com.ziggfreed.kweebec.feedback;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.kweebec.round.ChasePhase;

/**
 * The Chase round's custom overlay HUD: a countdown clock, the current objective,
 * shrines-lit, alive-count, and the corruption meter. Installed via
 * {@code HudManager.addCustomHud} on instance entry; the native HUD is stripped
 * alongside so the screen reads as the nightmare's. Updates are partial
 * ({@code update(false, ...)}). The element-id contract lives in the paired
 * {@code Common/UI/Custom/Hud/KweebecNightmareHud.ui}.
 */
public final class NightmareHud extends CustomUIHud {

    public NightmareHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder cb) {
        cb.append("Hud/KweebecNightmareHud.ui");
    }

    /**
     * Push the current round snapshot to this player's HUD. Runs on the world thread.
     *
     * @param remainingSec seconds left before the round cap
     * @param phase        current chase phase (drives the objective line)
     * @param lit          shrines relit
     * @param total        total shrines
     * @param alive        survivors still up
     * @param corruption   0..1 corruption fraction
     */
    public void pushState(int remainingSec, @Nonnull ChasePhase phase,
                          int lit, int total, int alive, double corruption) {
        UICommandBuilder cb = new UICommandBuilder();

        int rs = Math.max(0, remainingSec);
        String clock = String.format("%d:%02d", rs / 60, rs % 60);
        cb.set("#Timer.Text", Lang.msg(Lang.HUD_TIMER).param("0", clock));

        Message objective = phase == ChasePhase.ESCAPE
                ? Lang.msg(Lang.HUD_OBJECTIVE_ESCAPE)
                : Lang.msg(Lang.HUD_OBJECTIVE_RITUAL);
        cb.set("#Objective.Text", objective);

        cb.set("#Shrines.Text", Lang.msg(Lang.HUD_SHRINES).param("0", lit).param("1", total));
        cb.set("#Alive.Text", Lang.msg(Lang.HUD_ALIVE).param("0", alive));

        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, corruption)) * 100.0);
        cb.set("#Corruption.Text", Lang.msg(Lang.HUD_CORRUPTION).param("0", pct));

        update(false, cb);
    }
}
