package com.ziggfreed.kweebec.dialogue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.dialogue.DialogueAction;
import com.ziggfreed.common.dialogue.DialogueActionExecutor;
import com.ziggfreed.common.dialogue.DialogueActionType;
import com.ziggfreed.common.dialogue.DialogueExecContext;
import com.ziggfreed.common.dialogue.DialogueOptionStyle;
import com.ziggfreed.common.dialogue.DialogueSugar;
import com.ziggfreed.common.instance.play.PlayModePage;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.experience.KweebecExperience;

/**
 * Kweebec's custom dialogue action: open the {@link PlayModePage} for a chosen Chase
 * preset, so the player picks HOW to enter (Public / Party / Solo) instead of being
 * queued the instant they pick a difficulty. The replacement for the old
 * {@code StartRoundAction} (which queued immediately) - difficulty selection now leads to
 * the queue-mode chooser, not a silent solo queue.
 *
 * <p>A cross-package {@link DialogueAction} subtype - it declares its OWN {@code Preset}
 * field + its OWN static {@link BuilderCodec} (it cannot reuse the common subtypes'
 * {@code final}/protected fields across packages; it copies the {@code Goto} shape).
 * Authored as option sugar {@code { "Play": "<presetId>" }}, styled
 * {@link DialogueOptionStyle#ACCEPT} (green + check). The handler opens the page and marks
 * the dialogue as having opened another page (so it does not re-render over the chooser),
 * mirroring the generic {@code OpenPage} action.
 */
public final class OpenPlayAction extends DialogueAction {

    /** {@code "Play"} - the authored option-sugar key + the {@code Type} discriminator. */
    public static final String TYPE_ID = "Play";

    public static final BuilderCodec<OpenPlayAction> CODEC =
            BuilderCodec.builder(OpenPlayAction.class, OpenPlayAction::new)
                    .append(new KeyedCodec<>("Preset", Codec.STRING, false),
                            (a, v) -> a.preset = v, a -> a.preset)
                    .add()
                    .build();

    @Nullable
    protected String preset;

    @Nullable
    public String getPreset() {
        return preset;
    }

    /** The registrable type: data codec + handler + ACCEPT row look + the {@code Play} option sugar. */
    @Nonnull
    public static DialogueActionType<OpenPlayAction> type() {
        return DialogueActionType.of(TYPE_ID, OpenPlayAction.class, CODEC, OpenPlayAction::handle)
                .withStyle(DialogueOptionStyle.ACCEPT)
                .withSugar(DialogueSugar.string(TYPE_ID, 40, "Preset", TYPE_ID));
    }

    private static void handle(@Nonnull OpenPlayAction action, @Nonnull DialogueExecContext ctx,
                               @Nonnull DialogueActionExecutor.Mut out) {
        try {
            ctx.player().getPageManager().openCustomPage(ctx.ref(), ctx.store(),
                    new PlayModePage(ctx.playerRef(), KweebecExperience.playModeDeps(), action.getPreset()));
            // The chooser is now the active page; do not let the dialogue re-render over it.
            out.markOpenedOtherPage();
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] OpenPlay handler failed: " + t.getMessage());
        }
    }
}
