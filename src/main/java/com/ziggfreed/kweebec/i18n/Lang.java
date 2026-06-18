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

    // Custom item display names (Moonbloom charge + the grove plant + the shrine furnace block).
    public static final String ITEM_MOONBLOOM = "kweebecnightmare.item.moonbloom.name";
    public static final String ITEM_MOONBLOOM_PLANT = "kweebecnightmare.item.moonbloom_plant.name";
    public static final String ITEM_SHRINE = "kweebecnightmare.item.shrine.name";

    // Block interaction hints (the F-prompt shown when looking at an interactable block).
    public static final String INTERACTION_SHRINE_HINT = "kweebecnightmare.interaction.shrine.hint";

    // Moonbloom loop toasts (cleanse / submit / dormant / stun / gather).
    public static final String TOAST_CLEANSE_DONE = "kweebecnightmare.toast.cleanse_done";
    public static final String TOAST_CLEANSE_NEED = "kweebecnightmare.toast.cleanse_need";
    public static final String TOAST_SHRINE_SUBMIT = "kweebecnightmare.toast.shrine_submit";
    public static final String TOAST_SHRINE_DORMANT = "kweebecnightmare.toast.shrine_dormant";
    public static final String TOAST_MOONBLOOM_RESPAWN = "kweebecnightmare.toast.moonbloom_respawn";

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
    public static final String CMD_GIVE_DONE = "kweebecnightmare.cmd.give_done";
    public static final String CMD_SCORE_HEADER = "kweebecnightmare.cmd.score_header";
    public static final String CMD_SCORE_NONE = "kweebecnightmare.cmd.score_none";
    public static final String CMD_LB_HEADER = "kweebecnightmare.cmd.lb_header";
    public static final String CMD_LB_EMPTY = "kweebecnightmare.cmd.lb_empty";
    public static final String CMD_QUEUED = "kweebecnightmare.cmd.queued";
    public static final String CMD_ALREADY_QUEUED = "kweebecnightmare.cmd.already_queued";
    public static final String CMD_NOT_QUEUED_OR_IN_ROUND = "kweebecnightmare.cmd.not_queued_or_in_round";
    public static final String CMD_LEFT_QUEUE = "kweebecnightmare.cmd.left_queue";
    public static final String CMD_GUIDE_SPAWNED = "kweebecnightmare.cmd.guide_spawned";
    public static final String CMD_GUIDE_FAILED = "kweebecnightmare.cmd.guide_failed";

    // Matchmaking queue feedback (toasts + the launch-countdown banner). The queue itself
    // delivers these via the shared ziggfreed-common Notify / EventTitles primitives.
    public static final String QUEUE_JOINED = "kweebecnightmare.queue.joined";
    public static final String QUEUE_LEFT = "kweebecnightmare.queue.left";
    public static final String QUEUE_COUNTDOWN = "kweebecnightmare.queue.countdown";
    public static final String QUEUE_COUNTDOWN_SUB = "kweebecnightmare.queue.countdown.sub";
    public static final String QUEUE_LAUNCHING = "kweebecnightmare.queue.launching";
    public static final String QUEUE_CANCELLED = "kweebecnightmare.queue.cancelled";
    public static final String QUEUE_FAILED = "kweebecnightmare.queue.failed";

    // Command + argument descriptions (the engine resolves these as lang keys, not literals).
    public static final String CMD_DESC = "kweebecnightmare.cmd.kweebec.desc";
    public static final String ARG_SUB_DESC = "kweebecnightmare.cmd.arg.sub";
    public static final String ARG_PRESET_DESC = "kweebecnightmare.cmd.arg.preset";

    // Dialogue demo (the /kntalk trigger + the Whispering Sapling name header). The
    // dialogue's node/option TEXT keys are by-convention (dialogue.kweebec_intro.*)
    // and live only in the .lang, not as constants here.
    public static final String CMD_TALK_DESC = "kweebecnightmare.cmd.kntalk.desc";
    public static final String DIALOGUE_INTRO_NPC = "kweebecnightmare.dialogue.kweebec_intro.npc";

    // The guide NPC's dialogue header name (the "nightmares_intro" backstory + preset launcher).
    public static final String DIALOGUE_NIGHTMARES_NPC = "kweebecnightmare.dialogue.nightmares_intro.npc";

    /** Build a localized {@link Message} from a key (resolved client-side). */
    @Nonnull
    public static Message msg(@Nonnull String key) {
        return Message.translation(key);
    }
}
