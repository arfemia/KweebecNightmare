package com.ziggfreed.kweebec.dialogue;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.dialogue.DialogueEngine;
import com.ziggfreed.common.dialogue.NpcDialogue;
import com.ziggfreed.common.dialogue.i18n.DialogueI18n;
import com.ziggfreed.common.dialogue.i18n.I18nModuleDialogueI18n;
import com.ziggfreed.common.dialogue.page.DialoguePageDeps;
import com.ziggfreed.common.dialogue.page.SimpleDialogueExecContext;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * Kweebec's consumer-side wiring of the generic {@code ziggfreed-common} dialogue
 * engine. Builds ONE {@link DialogueEngine} with generics only (no quest/reward/gate
 * domain types - the minigame has none), an in-memory {@link KweebecDialogueFlags}
 * flag store, the {@code kweebecnightmare.} i18n namespace, and a name header; folds
 * the demo dialogue; and exposes the {@link DialoguePageDeps} the talk command opens
 * a page against. This is the whole consumer surface - the engine, page, and `.ui`
 * all live in the shared lib.
 */
public final class KweebecDialogue {

    /** The demo dialogue id (its content keys are {@code dialogue.kweebec_intro.*}). */
    public static final String INTRO_ID = "kweebec_intro";

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
        DialogueEngine engine = DialogueEngine.builder().warn(KweebecDialogue::warn).build();

        NpcDialogue intro = engine.decodeAuthored(INTRO_ID, INTRO_JSON);
        if (intro != null) {
            // Key on the canonical (engine-lowercased) id so PUT matches the lowercased GET lookup.
            DIALOGUES.put(intro.getId(), intro);
        }

        DialogueI18n i18n = new I18nModuleDialogueI18n("kweebecnightmare.");
        deps = new DialoguePageDeps(
                engine,
                id -> id == null ? null : DIALOGUES.get(id.toLowerCase(Locale.ROOT)),
                (dialogue, nodeId, optionIndex, contextId, ref, store, playerRef, player) ->
                        new SimpleDialogueExecContext(store, ref, playerRef, player, contextId,
                                KweebecDialogueFlags.store(playerRef.getUuid()), null,
                                dialogue, nodeId, optionIndex),
                i18n,
                contextId -> Lang.msg(Lang.DIALOGUE_INTRO_NPC),
                null);
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
}
