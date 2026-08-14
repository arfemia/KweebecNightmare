package com.ziggfreed.kweebec.dialogue;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.dialogue.DialogueEngine;
import com.ziggfreed.common.dialogue.NpcDialogue;
import com.ziggfreed.common.dialogue.asset.DialogueAssetStore;
import com.ziggfreed.common.dialogue.i18n.DialogueI18n;
import com.ziggfreed.common.dialogue.i18n.I18nModuleDialogueI18n;
import com.ziggfreed.common.dialogue.page.DialoguePageDeps;
import com.ziggfreed.common.dialogue.page.SimpleDialogueExecContext;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * Kweebec's consumer-side wiring of the generic {@code ziggfreed-common} dialogue
 * engine. Builds ONE {@link DialogueEngine} with the generics PLUS kweebec's own
 * {@link OpenPlayAction} (open the Play / queue-mode chooser for a preset) and
 * {@link NotInRoundCondition}/
 * {@link EngagedCondition} (gate launch options on engagement), an in-memory
 * {@link KweebecDialogueFlags} store, the {@code kweebecnightmare.} i18n namespace,
 * and a context-aware name header; resolves the authored dialogues (the guide NPC's
 * preset-launch backstory and the clash-host PvP entry) live off the shared store; and
 * exposes the {@link DialoguePageDeps} a page (command or NPC) opens against.
 *
 * <p>What an authored option's generic {@code Open} action opens is no longer a router this
 * class runs - it is a {@link com.ziggfreed.common.ui.route.Destination} the shared engine
 * resolves through the process-wide {@link com.ziggfreed.common.ui.route.Destinations} registry.
 * {@link KweebecDestinations} seeds kweebec's own two (the round leaderboard, the party invite
 * screen) into it.
 *
 * <p><b>Registration timing (Pattern A).</b> The store decodes every dialogue body ONCE, at
 * {@code LoadAssetEvent}, right after every plugin's {@code setup()} has returned - so kweebec's
 * {@code Play}/{@code NotInRound}/{@code Engaged} types must be in the shared
 * {@link com.ziggfreed.common.dialogue.DialogueTypeTable} before that fires. {@link #init()} is
 * called eagerly from {@link com.ziggfreed.kweebec.KweebecNightmarePlugin#setup()}, not lazily on
 * first NPC interaction - a late build would still register (the table logs one warning), but
 * every dialogue file that named its late type would already have failed to load.
 */
public final class KweebecDialogue {

    /** The guide NPC's backstory + preset-launch dialogue ({@code dialogue.nightmares_intro.*}). */
    public static final String NIGHTMARES_INTRO_ID = "nightmares_intro";

    /** The {@code ContextNpc} the guide role passes, used to pick the dialogue's name header. */
    public static final String GUIDE_CONTEXT = "guide";

    /** The {@code ContextNpc} the clash-host role passes, used to pick the dialogue's name header. */
    public static final String CLASH_CONTEXT = "clash";

    private static volatile DialoguePageDeps deps;

    private KweebecDialogue() {
    }

    /**
     * The page deps, built by {@link #init()}. Falls back to building them here (with one warn)
     * if a caller reaches this before plugin setup ran - defensive only; the real registration
     * point is {@code setup()}.
     */
    @Nonnull
    public static DialoguePageDeps deps() {
        DialoguePageDeps d = deps;
        if (d == null) {
            synchronized (KweebecDialogue.class) {
                d = deps;
                if (d == null) {
                    warn("KweebecDialogue.deps() reached before KweebecDialogue.init() ran from"
                            + " plugin setup - building the engine late; any dialogue file naming its"
                            + " types has already failed to load this boot");
                    init();
                    d = deps;
                }
            }
        }
        return d;
    }

    /**
     * Register kweebec's dialogue vocabulary and build the page deps. MUST be called from
     * {@link com.ziggfreed.kweebec.KweebecNightmarePlugin#setup()}, before assets load.
     */
    public static synchronized void init() {
        DialogueEngine engine = DialogueEngine.builder()
                .action(OpenPlayAction.type())
                .condition(NotInRoundCondition.type())
                .condition(EngagedCondition.type())
                .warn(KweebecDialogue::warn)
                .build();

        DialogueI18n i18n = new I18nModuleDialogueI18n("kweebecnightmare.");
        deps = new DialoguePageDeps(
                engine,
                // Read the store's decoded snapshot on every lookup (never cached): at THIS call
                // (setup time) the store is still empty, and it only fills once the engine's
                // LoadedAssetsEvent listener folds the pack layer in later in boot.
                id -> id == null ? null
                        : DialogueAssetStore.getInstance().dialogues().get(id.toLowerCase(Locale.ROOT)),
                (dialogue, nodeId, optionIndex, contextId, ref, store, playerRef, player) ->
                        new SimpleDialogueExecContext(store, ref, playerRef, player, contextId,
                                KweebecDialogueFlags.store(playerRef.getUuid()), null,
                                dialogue, nodeId, optionIndex),
                i18n,
                KweebecDialogue::npcName,
                null);
    }

    /** The dialogue header name, chosen by the opening NPC's context id (guide is the default). */
    @Nonnull
    private static Message npcName(@Nullable String contextId) {
        if (CLASH_CONTEXT.equals(contextId)) {
            return Lang.msg(Lang.DIALOGUE_CLASH_NPC);
        }
        return Lang.msg(Lang.DIALOGUE_NIGHTMARES_NPC);
    }

    private static void warn(@Nullable String msg) {
        try {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Dialogue] %s", msg);
        } catch (Throwable ignored) {
            // log-manager-less unit JVM; swallow.
        }
    }
}
