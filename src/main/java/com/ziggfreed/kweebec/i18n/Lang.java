package com.ziggfreed.kweebec.i18n;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;

/**
 * Localization key registry + {@link Message} factory. Every player-facing string
 * resolves through a key here (no raw display literals); the en-US values ship in
 * {@code Server/Languages/en-US/kweebecnightmare.lang}. Keys are grouped by surface.
 */
public final class Lang {

    private Lang() {
    }

    // Event-title banners.
    public static final String TITLE_HUNT_BEGINS = "kweebecnightmare.title.hunt_begins";
    public static final String TITLE_HUNT_BEGINS_SUB = "kweebecnightmare.title.hunt_begins.sub";
    public static final String TITLE_GATE_OPEN = "kweebecnightmare.title.gate_open";
    public static final String TITLE_GATE_OPEN_SUB = "kweebecnightmare.title.gate_open.sub";
    public static final String TITLE_YOU_SURVIVED = "kweebecnightmare.title.you_survived";
    public static final String TITLE_YOU_SURVIVED_SUB = "kweebecnightmare.title.you_survived.sub";
    public static final String TITLE_CAUGHT = "kweebecnightmare.title.caught";
    public static final String TITLE_CAUGHT_SUB = "kweebecnightmare.title.caught.sub";
    public static final String TITLE_NIGHT_FALLS = "kweebecnightmare.title.night_falls";
    public static final String TITLE_NIGHT_FALLS_SUB = "kweebecnightmare.title.night_falls.sub";

    // Danger / status toasts.
    public static final String TOAST_SHRINE_LIT = "kweebecnightmare.toast.shrine_lit";
    public static final String TOAST_SHRINE_PROGRESS = "kweebecnightmare.toast.shrine_progress";
    public static final String TOAST_SHRINE_CHANNEL = "kweebecnightmare.toast.shrine_channel";
    public static final String TOAST_SHRINE_FLARE = "kweebecnightmare.toast.shrine_flare";
    public static final String TOAST_COCOONED = "kweebecnightmare.toast.cocooned";
    public static final String TOAST_RESCUED = "kweebecnightmare.toast.rescued";
    public static final String TOAST_TEAMMATE_DOWN = "kweebecnightmare.toast.teammate_down";
    public static final String TOAST_HUNTER_LOCKED = "kweebecnightmare.toast.hunter_locked";
    public static final String TOAST_TIME_LOW = "kweebecnightmare.toast.time_low";

    // Custom HUD labels.
    public static final String HUD_TIMER = "kweebecnightmare.hud.timer";
    public static final String HUD_SHRINES = "kweebecnightmare.hud.shrines";
    public static final String HUD_ALIVE = "kweebecnightmare.hud.alive";
    public static final String HUD_CORRUPTION = "kweebecnightmare.hud.corruption";
    public static final String HUD_OBJECTIVE_RITUAL = "kweebecnightmare.hud.objective.ritual";
    public static final String HUD_OBJECTIVE_ESCAPE = "kweebecnightmare.hud.objective.escape";

    // Command + system feedback.
    public static final String CMD_PLAYERS_ONLY = "kweebecnightmare.cmd.players_only";
    public static final String CMD_ALREADY_IN_ROUND = "kweebecnightmare.cmd.already_in_round";
    public static final String CMD_NOT_IN_ROUND = "kweebecnightmare.cmd.not_in_round";
    public static final String CMD_STARTING = "kweebecnightmare.cmd.starting";
    public static final String CMD_START_FAILED = "kweebecnightmare.cmd.start_failed";
    public static final String CMD_LEAVING = "kweebecnightmare.cmd.leaving";
    public static final String CMD_UNKNOWN_PRESET = "kweebecnightmare.cmd.unknown_preset";
    public static final String CMD_NO_ACTIVE_ROUNDS = "kweebecnightmare.cmd.no_active_rounds";
    public static final String CMD_ENDED = "kweebecnightmare.cmd.ended";
    public static final String CMD_USAGE = "kweebecnightmare.cmd.usage";

    // Command + argument descriptions (the engine resolves these as lang keys, not literals).
    public static final String CMD_DESC = "kweebecnightmare.cmd.kweebec.desc";
    public static final String ARG_SUB_DESC = "kweebecnightmare.cmd.arg.sub";
    public static final String ARG_PRESET_DESC = "kweebecnightmare.cmd.arg.preset";

    /** Build a localized {@link Message} from a key (resolved client-side). */
    @Nonnull
    public static Message msg(@Nonnull String key) {
        return Message.translation(key);
    }
}
