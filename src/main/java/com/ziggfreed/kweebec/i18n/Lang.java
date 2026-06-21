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

    // Glow-throwable variant display names (the charge item + its harvestable grove plant): Gustbloom
    // (knockback), Mirebloom (slow), Emberbloom (boss-phase damage). Asset-referenced (the item JSON
    // TranslationProperties.Name), registered here so every key has one home.
    public static final String ITEM_GUSTBLOOM = "kweebecnightmare.item.gustbloom.name";
    public static final String ITEM_GUSTBLOOM_PLANT = "kweebecnightmare.item.gustbloom_plant.name";
    public static final String ITEM_MIREBLOOM = "kweebecnightmare.item.mirebloom.name";
    public static final String ITEM_MIREBLOOM_PLANT = "kweebecnightmare.item.mirebloom_plant.name";
    public static final String ITEM_EMBERBLOOM = "kweebecnightmare.item.emberbloom.name";
    public static final String ITEM_EMBERBLOOM_PLANT = "kweebecnightmare.item.emberbloom_plant.name";

    // Item tooltip descriptions (item JSON TranslationProperties.Description; native inline markup
    // rendered client-side on the tooltip's TextSpans, like vanilla items.*.description). The four
    // thrown blooms, their harvestable grove clusters, and the shrine furnace each carry one.
    public static final String ITEM_MOONBLOOM_DESC = "kweebecnightmare.item.moonbloom.description";
    public static final String ITEM_MOONBLOOM_PLANT_DESC = "kweebecnightmare.item.moonbloom_plant.description";
    public static final String ITEM_GUSTBLOOM_DESC = "kweebecnightmare.item.gustbloom.description";
    public static final String ITEM_GUSTBLOOM_PLANT_DESC = "kweebecnightmare.item.gustbloom_plant.description";
    public static final String ITEM_MIREBLOOM_DESC = "kweebecnightmare.item.mirebloom.description";
    public static final String ITEM_MIREBLOOM_PLANT_DESC = "kweebecnightmare.item.mirebloom_plant.description";
    public static final String ITEM_EMBERBLOOM_DESC = "kweebecnightmare.item.emberbloom.description";
    public static final String ITEM_EMBERBLOOM_PLANT_DESC = "kweebecnightmare.item.emberbloom_plant.description";
    public static final String ITEM_SHRINE_DESC = "kweebecnightmare.item.shrine.description";

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
    /** Cue when the whole required group first assembles on the extraction platform and the hold begins. */
    public static final String TOAST_EXTRACTION_HOLD = "kweebecnightmare.toast.extraction_hold";
    /** Shown when an asset-driven spawn rule pulls extra hunters in near the party. */
    public static final String TOAST_HUNTERS_DRAWN = "kweebecnightmare.toast.hunters_drawn";

    // Custom HUD labels.
    public static final String HUD_TIMER = "kweebecnightmare.hud.timer";
    public static final String HUD_SHRINES = "kweebecnightmare.hud.shrines";
    public static final String HUD_ALIVE = "kweebecnightmare.hud.alive";
    public static final String HUD_CORRUPTION = "kweebecnightmare.hud.corruption";
    public static final String HUD_OBJECTIVE_RITUAL = "kweebecnightmare.hud.objective.ritual";
    public static final String HUD_OBJECTIVE_ESCAPE = "kweebecnightmare.hud.objective.escape";
    // Co-op extraction HUD line (ESCAPE phase): the live "{0}/{1} on the platform" count, plus the {2}s hold
    // countdown when the whole group is assembled, or a gather prompt while it is not.
    public static final String HUD_EXTRACTION_HOLD = "kweebecnightmare.hud.extraction.hold";
    public static final String HUD_EXTRACTION_WAIT = "kweebecnightmare.hud.extraction.wait";

    // World-map / compass marker names (the exit POI placed when the Heartwood Gate opens).
    public static final String MARKER_EXIT = "kweebecnightmare.marker.exit";
    /** The shrine-discovery marker placed on a survivor's map when they first touch a shrine furnace. */
    public static final String MARKER_SHRINE = "kweebecnightmare.marker.shrine";

    // Boss capstone (the corrupted-Kweebec Warden): NPC name, the boss HUD label + phase indicator, and
    // the spawn banner + roar toast. The phase indicator interpolates {0}=current phase, {1}=total phases.
    public static final String NPC_WARDEN = "kweebecnightmare.npc.warden.name";
    public static final String BOSS_HUD_PHASE = "kweebecnightmare.boss.hud.phase";
    public static final String BOSS_TITLE_AWAKENS = "kweebecnightmare.boss.title.awakens";
    public static final String BOSS_TITLE_AWAKENS_SUB = "kweebecnightmare.boss.title.awakens.sub";
    public static final String BOSS_TOAST_PHASE = "kweebecnightmare.boss.toast.phase";
    public static final String BOSS_TOAST_DEFEATED = "kweebecnightmare.boss.toast.defeated";

    // --- Clash / Domination (PvP) ---
    // Match title banners (start / win / lose / draw) + the team names used as title args.
    public static final String CLASH_TITLE_START = "kweebecnightmare.clash.title.start";
    public static final String CLASH_TITLE_START_SUB = "kweebecnightmare.clash.title.start.sub";
    public static final String CLASH_TITLE_VICTORY = "kweebecnightmare.clash.title.victory";
    public static final String CLASH_TITLE_VICTORY_SUB = "kweebecnightmare.clash.title.victory.sub";
    public static final String CLASH_TITLE_DEFEAT = "kweebecnightmare.clash.title.defeat";
    public static final String CLASH_TITLE_DEFEAT_SUB = "kweebecnightmare.clash.title.defeat.sub";
    public static final String CLASH_TITLE_DRAW = "kweebecnightmare.clash.title.draw";
    public static final String CLASH_TITLE_DRAW_SUB = "kweebecnightmare.clash.title.draw.sub";
    public static final String CLASH_TITLE_SUDDEN_DEATH = "kweebecnightmare.clash.title.sudden_death";
    public static final String CLASH_TITLE_SUDDEN_DEATH_SUB = "kweebecnightmare.clash.title.sudden_death.sub";
    // Clash toasts.
    public static final String CLASH_TOAST_ELIMINATED = "kweebecnightmare.clash.toast.eliminated";
    public static final String CLASH_TOAST_RESPAWN = "kweebecnightmare.clash.toast.respawn";
    public static final String CLASH_TOAST_KO = "kweebecnightmare.clash.toast.ko";
    public static final String CLASH_TOAST_TEAMMATE_DOWN = "kweebecnightmare.clash.toast.teammate_down";
    public static final String CLASH_TOAST_PICKUP = "kweebecnightmare.clash.toast.pickup";
    public static final String CLASH_TOAST_POINT_CAPTURED = "kweebecnightmare.clash.toast.point_captured";
    public static final String CLASH_TOAST_POINT_LOST = "kweebecnightmare.clash.toast.point_lost";
    // Clash / Domination HUD labels + team names (rendered RED/BLUE).
    public static final String CLASH_HUD_TIMER = "kweebecnightmare.clash.hud.timer";
    public static final String CLASH_HUD_SCORE = "kweebecnightmare.clash.hud.score";
    public static final String CLASH_HUD_TEAM = "kweebecnightmare.clash.hud.team";
    public static final String CLASH_HUD_TALLY_HITS = "kweebecnightmare.clash.hud.tally.hits";
    public static final String CLASH_HUD_TALLY_KILLS = "kweebecnightmare.clash.hud.tally.kills";
    public static final String CLASH_HUD_ALIVE = "kweebecnightmare.clash.hud.alive";
    public static final String CLASH_HUD_CAPTURE = "kweebecnightmare.clash.hud.capture";
    public static final String CLASH_TEAM_RED = "kweebecnightmare.clash.team.red";
    public static final String CLASH_TEAM_BLUE = "kweebecnightmare.clash.team.blue";
    // Domination marker name.
    public static final String DOM_MARKER_POINT = "kweebecnightmare.domination.marker.point";
    // Coloured (TextSpans) team-score HUD variants - your-score-first ordering flips per team.
    public static final String CLASH_HUD_SCORE_RED = "kweebecnightmare.clash.hud.score.red";
    public static final String CLASH_HUD_SCORE_BLUE = "kweebecnightmare.clash.hud.score.blue";
    // PvP results screen (team breakdown) + the per-mode leaderboard + the PvP preset display names.
    public static final String RESULTS_CLASH_BREAKDOWN = "kweebecnightmare.results.clash.breakdown";
    public static final String RESULTS_CLASH_KOS = "kweebecnightmare.results.clash.kos";
    public static final String RESULTS_CLASH_PREVIEW = "kweebecnightmare.results.clash.preview";
    public static final String LB_CLASH_TITLE = "kweebecnightmare.leaderboard.clash.title";
    public static final String LB_CLASH_AXIS_MODE = "kweebecnightmare.leaderboard.clash.axis.mode";
    public static final String LB_CLASH_STAT_HITS = "kweebecnightmare.leaderboard.clash.stat.hits";
    public static final String LB_CLASH_STAT_KILLS = "kweebecnightmare.leaderboard.clash.stat.kills";
    public static final String LB_CLASH_STAT_KOS = "kweebecnightmare.leaderboard.clash.stat.kos";
    public static final String PRESET_CLASH_1V1 = "kweebecnightmare.preset.clash_1v1.name";
    public static final String PRESET_CLASH_2V2 = "kweebecnightmare.preset.clash_2v2.name";
    public static final String PRESET_DOMINATION_KOTH = "kweebecnightmare.preset.domination_koth.name";

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

    // The guide NPC's dialogue header name (the "nightmares_intro" backstory + preset launcher).
    public static final String DIALOGUE_NIGHTMARES_NPC = "kweebecnightmare.dialogue.nightmares_intro.npc";

    // The Duelmaster (clash host) dialogue header name (the "clash_intro" PvP entry).
    public static final String DIALOGUE_CLASH_NPC = "kweebecnightmare.dialogue.clash_intro.npc";

    // Leaderboard page (launched by the guide's "Open: leaderboard" dialogue option). The option
    // LABEL is by-convention (dialogue.nightmares_intro.greet.opt.3) and lives only in the .lang.
    public static final String LB_TITLE = "kweebecnightmare.leaderboard.title";
    public static final String LB_TAB_SOLO = "kweebecnightmare.leaderboard.tab.solo";
    public static final String LB_TAB_DUO = "kweebecnightmare.leaderboard.tab.duo";
    public static final String LB_TAB_TRIO = "kweebecnightmare.leaderboard.tab.trio";
    public static final String LB_TAB_SQUAD = "kweebecnightmare.leaderboard.tab.squad";
    public static final String LB_COL_RANK = "kweebecnightmare.leaderboard.col.rank";
    public static final String LB_COL_PLAYER = "kweebecnightmare.leaderboard.col.player";
    public static final String LB_COL_SCORE = "kweebecnightmare.leaderboard.col.score";
    public static final String LB_COL_TOTAL = "kweebecnightmare.leaderboard.col.total";
    public static final String LB_COL_TIME = "kweebecnightmare.leaderboard.col.time";
    public static final String LB_COL_PLAYS = "kweebecnightmare.leaderboard.col.plays";
    public static final String LB_EMPTY = "kweebecnightmare.leaderboard.empty";
    public static final String LB_YOUR_RANK = "kweebecnightmare.leaderboard.your_rank";
    public static final String LB_YOUR_RANK_NONE = "kweebecnightmare.leaderboard.your_rank_none";
    // Sort toggle + Rankings/Stats view toggle button labels.
    public static final String LB_SORT_SCORE = "kweebecnightmare.leaderboard.sort.score";
    public static final String LB_SORT_TOTAL = "kweebecnightmare.leaderboard.sort.total";
    public static final String LB_SORT_TIME = "kweebecnightmare.leaderboard.sort.time";
    public static final String LB_VIEW_RANKINGS = "kweebecnightmare.leaderboard.view.rankings";
    public static final String LB_VIEW_STATS = "kweebecnightmare.leaderboard.view.stats";
    // Leading filter-row labels (clarify which axis each selector row drives).
    public static final String LB_AXIS_DIFFICULTY = "kweebecnightmare.leaderboard.axis.difficulty";
    public static final String LB_AXIS_PLAYERS = "kweebecnightmare.leaderboard.axis.players";
    public static final String LB_AXIS_SORT = "kweebecnightmare.leaderboard.axis.sort";
    public static final String LB_AXIS_VIEW = "kweebecnightmare.leaderboard.axis.view";
    // The synthesized "All" tab label (lifetime roll-up; on each tab axis with 2+ concrete tabs).
    public static final String LB_FILTER_ALL = "kweebecnightmare.leaderboard.filter.all";
    // Lifetime stat column labels (Stats view).
    public static final String LB_STAT_STUNNED = "kweebecnightmare.leaderboard.stat.stunned";
    public static final String LB_STAT_MOONBLOOM = "kweebecnightmare.leaderboard.stat.moonbloom";
    public static final String LB_STAT_SHRINES = "kweebecnightmare.leaderboard.stat.shrines";
    public static final String LB_STAT_WINS = "kweebecnightmare.leaderboard.stat.wins";
    // Difficulty tab labels reuse the existing preset names (the 3 core + the 4 variety presets).
    public static final String PRESET_AMATEUR = "kweebecnightmare.preset.amateur.name";
    public static final String PRESET_NIGHTMARE = "kweebecnightmare.preset.nightmare.name";
    public static final String PRESET_HARDCORE = "kweebecnightmare.preset.hardcore.name";
    public static final String PRESET_ENDLESS = "kweebecnightmare.preset.endless.name";
    public static final String PRESET_SWARM = "kweebecnightmare.preset.swarm.name";
    public static final String PRESET_PITCH = "kweebecnightmare.preset.pitch.name";
    public static final String PRESET_BLITZ = "kweebecnightmare.preset.blitz.name";

    // End-of-game results screen (the common instance-experience ResultsPage).
    public static final String RESULTS_WIN = "kweebecnightmare.results.win";
    public static final String RESULTS_LOSS = "kweebecnightmare.results.loss";
    public static final String RESULTS_DRAW = "kweebecnightmare.results.draw";
    public static final String RESULTS_ABORT = "kweebecnightmare.results.abort";
    public static final String RESULTS_DURATION = "kweebecnightmare.results.duration";
    public static final String RESULTS_BREAKDOWN = "kweebecnightmare.results.breakdown";
    public static final String RESULTS_REWARDS = "kweebecnightmare.results.rewards";
    public static final String RESULTS_NO_REWARDS = "kweebecnightmare.results.no_rewards";
    public static final String RESULTS_PENDING = "kweebecnightmare.results.pending";
    public static final String RESULTS_BTN_LB = "kweebecnightmare.results.btn.leaderboard";
    public static final String RESULTS_BTN_AGAIN = "kweebecnightmare.results.btn.again";
    public static final String RESULTS_BTN_CLOSE = "kweebecnightmare.results.btn.close";
    public static final String RESULTS_TEAM_TOTAL = "kweebecnightmare.results.team_total";
    public static final String RESULTS_COL_TIME = "kweebecnightmare.results.col.time";
    public static final String RESULTS_COL_DAMAGE = "kweebecnightmare.results.col.damage";
    public static final String RESULTS_COL_STUN = "kweebecnightmare.results.col.stun";
    public static final String RESULTS_COL_SHRINE = "kweebecnightmare.results.col.shrine";
    public static final String RESULTS_COL_DURATION = "kweebecnightmare.results.col.duration";
    public static final String RESULTS_COL_BASE = "kweebecnightmare.results.col.base";
    // Run-stats section (raw per-run activity, a second breakdown below the points).
    public static final String RESULTS_STATS = "kweebecnightmare.results.stats";
    public static final String RESULTS_STAT_DMG_TAKEN = "kweebecnightmare.results.stat.damage_taken";
    public static final String RESULTS_STAT_STUNNED = "kweebecnightmare.results.stat.stunned";
    public static final String RESULTS_STAT_MOONBLOOM = "kweebecnightmare.results.stat.moonbloom";
    // Shown in the in-instance preview where the spoils chips would be (rewards grant on overworld return).
    public static final String RESULTS_SPOILS_PENDING = "kweebecnightmare.results.spoils_pending";
    // Manual claim: the results-screen Claim button + the post-claim confirmation note.
    public static final String RESULTS_BTN_CLAIM = "kweebecnightmare.results.btn.claim";
    public static final String RESULTS_CLAIMED = "kweebecnightmare.results.claimed";

    // Play screen - the "Claim Rewards" button (chooser) for spoils missed earlier + its toast.
    public static final String PLAY_BTN_CLAIM = "kweebecnightmare.play.btn.claim";
    public static final String PLAY_TOAST_CLAIMED = "kweebecnightmare.play.toast.claimed";

    // Play screen - the live roster / ready chrome (the common PlayModePage, queued state).
    public static final String QUEUE_SCREEN_COUNT = "kweebecnightmare.queue.screen.count";
    public static final String QUEUE_SCREEN_WAITING = "kweebecnightmare.queue.screen.waiting";
    public static final String QUEUE_SCREEN_COUNTDOWN = "kweebecnightmare.queue.screen.countdown";
    public static final String QUEUE_SCREEN_LAUNCHING = "kweebecnightmare.queue.screen.launching";
    public static final String QUEUE_SCREEN_WAIT = "kweebecnightmare.queue.screen.wait";
    public static final String QUEUE_SCREEN_LEAVE = "kweebecnightmare.queue.screen.leave";
    public static final String QUEUE_SCREEN_NOT_QUEUED = "kweebecnightmare.queue.screen.not_queued";
    public static final String QUEUE_SCREEN_TOAST_QUEUED = "kweebecnightmare.queue.screen.toast_queued";

    // Play screen - the mode chooser (the common PlayModePage, not-queued state): title, the chosen
    // difficulty line, and the Public / Party / Solo card labels + one-line descriptions.
    public static final String PLAY_TITLE = "kweebecnightmare.play.title";
    public static final String PLAY_DIFFICULTY = "kweebecnightmare.play.difficulty";
    public static final String MODE_PUBLIC = "kweebecnightmare.play.mode.public";
    public static final String MODE_PUBLIC_DESC = "kweebecnightmare.play.mode.public.desc";
    public static final String MODE_PARTY = "kweebecnightmare.play.mode.party";
    public static final String MODE_PARTY_DESC = "kweebecnightmare.play.mode.party.desc";
    public static final String MODE_SOLO = "kweebecnightmare.play.mode.solo";
    public static final String MODE_SOLO_DESC = "kweebecnightmare.play.mode.solo.desc";

    // Party + invite screen (the common PartyInvitePage).
    public static final String PARTY_TITLE = "kweebecnightmare.party.title";
    public static final String PARTY_TAB_PARTY = "kweebecnightmare.party.tab.party";
    public static final String PARTY_TAB_INVITE = "kweebecnightmare.party.tab.invite";
    public static final String PARTY_EMPTY_INVITE = "kweebecnightmare.party.empty.invite";
    public static final String PARTY_EMPTY_PARTY = "kweebecnightmare.party.empty.party";
    public static final String PARTY_BTN_INVITE = "kweebecnightmare.party.btn.invite";
    public static final String PARTY_BTN_KICK = "kweebecnightmare.party.btn.kick";
    public static final String PARTY_BTN_LEAVE = "kweebecnightmare.party.btn.leave";
    public static final String PARTY_BTN_DISBAND = "kweebecnightmare.party.btn.disband";
    public static final String PARTY_BTN_QUEUE = "kweebecnightmare.party.btn.queue";
    public static final String PARTY_BTN_ACCEPT = "kweebecnightmare.party.btn.accept";
    public static final String PARTY_BTN_DECLINE = "kweebecnightmare.party.btn.decline";
    public static final String PARTY_OWNER_BADGE = "kweebecnightmare.party.owner_badge";
    public static final String PARTY_COUNT = "kweebecnightmare.party.count";
    public static final String PARTY_PRIVACY_PUBLIC = "kweebecnightmare.party.privacy.public";
    public static final String PARTY_PRIVACY_PRIVATE = "kweebecnightmare.party.privacy.private";
    public static final String PARTY_TOAST_INVITE_FAILED = "kweebecnightmare.party.toast.invite_failed";

    // Party feedback toasts + banner.
    public static final String PARTY_MSG_INVITED = "kweebecnightmare.party.msg.invited";
    public static final String PARTY_MSG_BANNER = "kweebecnightmare.party.msg.banner";
    public static final String PARTY_MSG_BANNER_SUB = "kweebecnightmare.party.msg.banner.sub";
    public static final String PARTY_MSG_SENT = "kweebecnightmare.party.msg.sent";
    public static final String PARTY_MSG_DECLINED = "kweebecnightmare.party.msg.declined";
    public static final String PARTY_MSG_EXPIRED = "kweebecnightmare.party.msg.expired";
    public static final String PARTY_MSG_JOINED = "kweebecnightmare.party.msg.joined";
    public static final String PARTY_MSG_LEFT = "kweebecnightmare.party.msg.left";
    public static final String PARTY_MSG_KICKED = "kweebecnightmare.party.msg.kicked";
    public static final String PARTY_MSG_KICKED_MEMBER = "kweebecnightmare.party.msg.kicked_member";
    public static final String PARTY_MSG_DISBANDED = "kweebecnightmare.party.msg.disbanded";
    public static final String PARTY_MSG_OWNER = "kweebecnightmare.party.msg.owner";

    // Command feedback for the new party / queue sub-commands.
    public static final String CMD_PARTY_OPENED = "kweebecnightmare.cmd.party_opened";

    /** Build a localized {@link Message} from a key (resolved client-side). */
    @Nonnull
    public static Message msg(@Nonnull String key) {
        return Message.translation(key);
    }
}
