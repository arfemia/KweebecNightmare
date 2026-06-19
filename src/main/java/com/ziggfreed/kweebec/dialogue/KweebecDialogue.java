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
import com.ziggfreed.common.dialogue.asset.DialogueAssetStore;
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

        // Pull the authored bodies from the common dialogue store (pack JSON under
        // Server/ZiggfreedCommon/Dialogues/, filtered to this game's Owner) and decode them
        // through THIS engine (its domain Play/NotInRound/Engaged types). Runs lazily on first
        // use (an NPC/command opening a dialogue), which is after the LoadedAssetsEvent fold, so
        // the store is populated. Re-call to pick up a hot re-import.
        DIALOGUES.clear();
        DIALOGUES.putAll(DialogueAssetStore.getInstance().resolveAll(engine, "kweebec"));

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
}
