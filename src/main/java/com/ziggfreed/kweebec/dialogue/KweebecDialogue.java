package com.ziggfreed.kweebec.dialogue;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.ziggfreed.common.dialogue.DialogueEngine;
import com.ziggfreed.common.dialogue.schema.NpcDialogue;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * Kweebec's consumer-side wiring of the generic {@code ziggfreed-common} dialogue
 * engine. Contributes kweebec's own {@link OpenPlayAction} (open the Play / queue-mode chooser for
 * a preset) and {@link NotInRoundCondition}/
 * {@link EngagedCondition} (gate launch options on engagement) to the server's ONE
 * {@link DialogueEngine}. Its authored keys resolve wherever they are painted because the
 * {@code kweebecnightmare.lang} files this mod ships ARE the declaration: the shared library
 * attributes a bare authored key against the server's loaded lang catalogue, so nothing is
 * registered for i18n. Its two conversations (the guide NPC's preset-launch backstory and the
 * clash-host PvP entry) live in the shared store like everybody else's, and the SCREEN they open on
 * is the library's, built from process-wide state, so there is nothing here to hand a page.
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
 * {@link com.ziggfreed.common.dialogue.schema.DialogueTypeTable} before that fires. {@link #init()} is
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


    private KweebecDialogue() {
    }


    /**
     * Register kweebec's dialogue vocabulary. MUST be called from
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
    }


    private static void warn(@Nullable String msg) {
        try {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Dialogue] %s", msg);
        } catch (Throwable ignored) {
            // log-manager-less unit JVM; swallow.
        }
    }
}
