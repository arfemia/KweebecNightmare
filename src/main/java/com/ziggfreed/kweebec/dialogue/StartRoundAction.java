package com.ziggfreed.kweebec.dialogue;

import java.util.UUID;

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
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.lobby.KweebecLobby;

/**
 * Kweebec's custom dialogue action: queue the player for a Chase round of a chosen
 * preset. A cross-package {@link DialogueAction} subtype - it declares its OWN
 * {@code Preset} field + its OWN static {@link BuilderCodec} (it cannot reuse the
 * common subtypes' {@code final}/protected fields across packages; it copies the
 * {@code Goto} shape). Registered on the engine via {@link #type()} in
 * {@link KweebecDialogue}.
 *
 * <p>Authored as option sugar {@code { "StartRound": "<presetId>" }} (the
 * {@link DialogueSugar#string} expander), styled {@link DialogueOptionStyle#ACCEPT}
 * (green + check). The handler closes the page FIRST (so a join exception, which the
 * executor swallows + logs, still closes it) then hands the player to
 * {@link KweebecLobby}; the queue owns all join/countdown feedback.
 */
public final class StartRoundAction extends DialogueAction {

    /** {@code "StartRound"} - the authored option-sugar key + the {@code Type} discriminator. */
    public static final String TYPE_ID = "StartRound";

    public static final BuilderCodec<StartRoundAction> CODEC =
            BuilderCodec.builder(StartRoundAction.class, StartRoundAction::new)
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

    /** The registrable type: data codec + handler + ACCEPT row look + the {@code StartRound} option sugar. */
    @Nonnull
    public static DialogueActionType<StartRoundAction> type() {
        return DialogueActionType.of(TYPE_ID, StartRoundAction.class, CODEC, StartRoundAction::handle)
                .withStyle(DialogueOptionStyle.ACCEPT)
                .withSugar(DialogueSugar.string(TYPE_ID, 40, "Preset", TYPE_ID));
    }

    private static void handle(@Nonnull StartRoundAction action, @Nonnull DialogueExecContext ctx,
                               @Nonnull DialogueActionExecutor.Mut out) {
        // Close FIRST: handler throws are caught + logged by the executor and the page
        // re-opens unless a close was already requested, so request it before the join.
        out.requestClose();
        try {
            UUID uuid = ctx.playerRef().getUuid();
            KweebecLobby.join(uuid, action.getPreset());
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] StartRound handler failed: " + t.getMessage());
        }
    }
}
