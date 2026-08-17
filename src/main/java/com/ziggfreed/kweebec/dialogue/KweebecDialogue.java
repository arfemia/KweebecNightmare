package com.ziggfreed.kweebec.dialogue;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.dialogue.DialogueEngine;
import com.ziggfreed.common.dialogue.NpcDialogue;
import com.ziggfreed.common.dialogue.asset.DialogueAssetStore;
import com.ziggfreed.common.dialogue.page.DialoguePageDeps;
import com.ziggfreed.common.dialogue.page.SimpleDialogueExecContext;
import com.ziggfreed.common.i18n.ContentI18n;
import com.ziggfreed.common.i18n.ContentKeys;
import com.ziggfreed.common.i18n.I18nModuleContentI18n;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * Kweebec's consumer-side wiring of the generic {@code ziggfreed-common} dialogue
 * engine. Contributes kweebec's own {@link OpenPlayAction} (open the Play / queue-mode chooser for
 * a preset) and {@link NotInRoundCondition}/
 * {@link EngagedCondition} (gate launch options on engagement) to the server's ONE
 * {@link DialogueEngine}, declares the
 * {@code kweebecnightmare.} i18n namespace,
 * and a context-aware name header; resolves the authored dialogues (the guide NPC's
 * preset-launch backstory and the clash-host PvP entry) live off the shared store; and
 * exposes the {@link DialoguePageDeps} a page (command or NPC) opens against.
 *
 * <p><b>Where a conversation's memory is kept is not wired here, and must not be.</b> The library
 * owns both lifetimes now and routes each memory to the right one by what its author declared: a
 * {@code Memories} entry carrying {@code "Session": true} lasts as long as the player's visit,
 * anything else survives a restart. Neither of kweebec's two conversations declares a memory or a
 * {@code Once} at all, so nothing here changes either way - but a round-scoped beat added later
 * declares {@code Session} in its own file rather than being given a store from Java.
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
 * first NPC interaction - a late registration would still take effect (the table logs one
 * warning), but every dialogue file that named its late type would already have failed to load.
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
        // Contributed, not built: there is ONE engine per server and kweebec's three types join
        // whatever else is installed, so a conversation may carry a Play option beside another
        // mod's action and both run.
        String owner = KweebecNightmarePlugin.REGISTRY_OWNER;
        DialogueEngine.registerShared(owner, OpenPlayAction.type());
        DialogueEngine.registerShared(owner, NotInRoundCondition.type());
        DialogueEngine.registerShared(owner, EngagedCondition.type());
        DialogueEngine engine = DialogueEngine.shared();

        // This mod's namespace, declared once: registered with the shared library so any surface
        // painting kweebec's authored content resolves a key against the kweebecnightmare.lang
        // catalogue, and handed to the dialogue page for its own node/option text.
        ContentI18n i18n = new I18nModuleContentI18n("kweebecnightmare.");
        ContentKeys.install(i18n);
        deps = new DialoguePageDeps(
                engine,
                // Read the store's decoded snapshot on every lookup (never cached): at THIS call
                // (setup time) the store is still empty, and it only fills once the engine's
                // LoadedAssetsEvent listener folds the pack layer in later in boot.
                id -> id == null ? null
                        : DialogueAssetStore.getInstance().dialogues().get(id.toLowerCase(Locale.ROOT)),
                (dialogue, nodeId, optionIndex, contextId, ref, store, player) ->
                        new SimpleDialogueExecContext(store, ref, player, contextId,
                                null, dialogue, nodeId, optionIndex),
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
