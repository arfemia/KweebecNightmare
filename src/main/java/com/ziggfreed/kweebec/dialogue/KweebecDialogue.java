package com.ziggfreed.kweebec.dialogue;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.dialogue.DialogueEngine;
import com.ziggfreed.common.dialogue.DialogueExecContext;
import com.ziggfreed.common.dialogue.NpcDialogue;
import com.ziggfreed.common.dialogue.i18n.DialogueI18n;
import com.ziggfreed.common.dialogue.i18n.I18nModuleDialogueI18n;
import com.ziggfreed.common.dialogue.page.DialoguePageDeps;
import com.ziggfreed.common.dialogue.page.SimpleDialogueExecContext;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.party.page.PartyInvitePage;
import com.ziggfreed.kweebec.experience.KweebecExperience;

/**
 * Kweebec's consumer-side wiring of the generic {@code ziggfreed-common} dialogue
 * engine. Builds ONE {@link DialogueEngine} with the generics PLUS kweebec's own
 * {@link OpenPlayAction} (open the Play / queue-mode chooser for a preset) and
 * {@link NotInRoundCondition}/
 * {@link EngagedCondition} (gate launch options on engagement), an in-memory
 * {@link KweebecDialogueFlags} store, the {@code kweebecnightmare.} i18n namespace,
 * and a context-aware name header; folds two authored dialogues (the {@code /kntalk}
 * Whispering Sapling demo and the guide NPC's preset-launch backstory); and exposes
 * the {@link DialoguePageDeps} a page (command or NPC) opens against.
 */
public final class KweebecDialogue {

    /** The demo dialogue id (its content keys are {@code dialogue.kweebec_intro.*}). */
    public static final String INTRO_ID = "kweebec_intro";

    /** The guide NPC's backstory + preset-launch dialogue ({@code dialogue.nightmares_intro.*}). */
    public static final String NIGHTMARES_INTRO_ID = "nightmares_intro";

    /** The {@code ContextNpc} the guide role passes, used to pick the dialogue's name header. */
    public static final String GUIDE_CONTEXT = "guide";

    private static volatile DialoguePageDeps deps;
    private static final Map<String, NpcDialogue> DIALOGUES = new ConcurrentHashMap<>();

    private KweebecDialogue() {
    }

    /** The page deps (lazy-built on first use). */
    @Nonnull
    public static synchronized DialoguePageDeps deps() {
        if (deps == null) {
            init();
        }
        return deps;
    }

    private static void init() {
        DialogueEngine engine = DialogueEngine.builder()
                .action(OpenPlayAction.type())
                .condition(NotInRoundCondition.type())
                .condition(EngagedCondition.type())
                .router(KweebecDialogue::route)
                .warn(KweebecDialogue::warn)
                .build();

        decodeInto(engine, INTRO_ID, INTRO_JSON);
        decodeInto(engine, NIGHTMARES_INTRO_ID, NIGHTMARES_INTRO_JSON);

        DialogueI18n i18n = new I18nModuleDialogueI18n("kweebecnightmare.");
        deps = new DialoguePageDeps(
                engine,
                id -> id == null ? null : DIALOGUES.get(id.toLowerCase(Locale.ROOT)),
                (dialogue, nodeId, optionIndex, contextId, ref, store, playerRef, player) ->
                        new SimpleDialogueExecContext(store, ref, playerRef, player, contextId,
                                KweebecDialogueFlags.store(playerRef.getUuid()), null,
                                dialogue, nodeId, optionIndex),
                i18n,
                KweebecDialogue::npcName,
                null);
    }

    /** Decode an authored body and key it on the canonical (engine-lowercased) id. */
    private static void decodeInto(@Nonnull DialogueEngine engine, @Nonnull String id, @Nonnull String json) {
        NpcDialogue d = engine.decodeAuthored(id, json);
        if (d != null) {
            DIALOGUES.put(d.getId(), d);
        }
    }

    /** The dialogue header name, chosen by the opening NPC's context id. */
    @Nonnull
    private static Message npcName(@Nullable String contextId) {
        if (GUIDE_CONTEXT.equals(contextId)) {
            return Lang.msg(Lang.DIALOGUE_NIGHTMARES_NPC);
        }
        return Lang.msg(Lang.DIALOGUE_INTRO_NPC);
    }

    /**
     * The {@link com.ziggfreed.common.dialogue.DialoguePageRouter} for the engine's generic
     * {@code OpenPage} action: an option authored {@code { "Open": "leaderboard" }} opens the
     * shared {@link LeaderboardPage}; {@code { "Open": "party" }} opens the {@link PartyInvitePage}.
     * Returns true so the dialogue page does not re-open over it.
     */
    private static boolean route(@Nonnull String target, @Nonnull DialogueExecContext ctx) {
        if ("leaderboard".equalsIgnoreCase(target)) {
            ctx.player().getPageManager().openCustomPage(ctx.ref(), ctx.store(),
                    new LeaderboardPage(ctx.playerRef(), KweebecExperience.leaderboardDeps()));
            return true;
        }
        if ("party".equalsIgnoreCase(target)) {
            ctx.player().getPageManager().openCustomPage(ctx.ref(), ctx.store(),
                    new PartyInvitePage(ctx.playerRef(), KweebecExperience.partyDeps()));
            return true;
        }
        return false;
    }

    private static void warn(@Nullable String msg) {
        try {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Dialogue] %s", msg);
        } catch (Throwable ignored) {
            // log-manager-less unit JVM; swallow.
        }
    }

    /**
     * The demo dialogue (the "Whispering Sapling"): a greeting with a lore branch, a
     * warning branch that SETS a flag, and a flag-gated "secret" option that only
     * appears after the warning. Demonstrates greet/goto/close, option sugar
     * ({@code Goto}), canonical {@code SetFlag} actions, and {@code Flag}-gated
     * conditions - the full generic feature set, with no MMO domain types.
     */
    private static final String INTRO_JSON = """
            {
              "Start": [{ "Node": "greet" }],
              "Nodes": {
                "greet": {
                  "Options": [
                    { "Goto": "lore" },
                    { "Actions": [
                        { "Type": "SetFlag", "Flag": "warned" },
                        { "Type": "Goto", "Node": "warning" }
                    ] },
                    { "Conditions": [{ "Type": "Flag", "Flag": "warned" }], "Goto": "secret" }
                  ]
                },
                "lore":    { "Options": [ { "Goto": "greet" } ] },
                "warning": { "Options": [ { "Goto": "greet" } ] },
                "secret":  { "Options": [ { "Goto": "greet" } ] }
              }
            }
            """;

    /**
     * The guide NPC's dialogue ("The Grove Warden"): introduces the nightmare, a lore
     * branch telling the backstory, then a {@code preset_pick} node whose options each
     * open the Play / queue-mode chooser for a Chase preset via {@link OpenPlayAction} -
     * gated by
     * {@link NotInRoundCondition} so they vanish once the player commits. An
     * {@link EngagedCondition}-gated branch points an already-queued player at an
     * {@code already_engaged} note. Launch options are the three canonical difficulties;
     * the variety presets stay reachable via {@code /kweebec start <preset>}.
     */
    private static final String NIGHTMARES_INTRO_JSON = """
            {
              "Start": [{ "Node": "greet" }],
              "Nodes": {
                "greet": {
                  "Options": [
                    { "Goto": "lore" },
                    { "Conditions": [{ "Type": "NotInRound" }], "Goto": "preset_pick" },
                    { "Conditions": [{ "Type": "Engaged" }], "Goto": "already_engaged" },
                    { "Open": "leaderboard" }
                  ]
                },
                "lore": {
                  "Options": [
                    { "Conditions": [{ "Type": "NotInRound" }], "Goto": "preset_pick" },
                    { "Goto": "greet" }
                  ]
                },
                "preset_pick": {
                  "Options": [
                    { "Conditions": [{ "Type": "NotInRound" }], "Play": "amateur" },
                    { "Conditions": [{ "Type": "NotInRound" }], "Play": "nightmare" },
                    { "Conditions": [{ "Type": "NotInRound" }], "Play": "hardcore" },
                    { "Goto": "greet" }
                  ]
                },
                "already_engaged": {
                  "Options": [
                    { "Goto": "greet" }
                  ]
                }
              }
            }
            """;
}
