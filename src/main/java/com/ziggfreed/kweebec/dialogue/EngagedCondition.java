package com.ziggfreed.kweebec.dialogue;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.ziggfreed.common.dialogue.DialogueCondition;
import com.ziggfreed.common.dialogue.DialogueConditionType;
import com.ziggfreed.common.dialogue.DialogueContext;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * The inverse of {@link NotInRoundCondition}: an option is shown only WHILE the
 * player is engaged - in a live round OR sitting in a queue. Reveals the guide's
 * {@code already_engaged} branch (a note + a reminder to {@code /kweebec leave}) so a
 * player who re-opens the dialogue after queueing gets an explanation rather than a
 * silently-hidden launch list.
 *
 * <p>Field-less ({@code {"Type":"Engaged"}}); fails CLOSED on error (hidden) so a
 * glitch never shows the note to a free player. The launch options use
 * {@link NotInRoundCondition}, so exactly one of the two branches is ever visible.
 */
public final class EngagedCondition extends DialogueCondition {

    /** {@code "Engaged"} - the authored {@code Type} discriminator. */
    public static final String TYPE_ID = "Engaged";

    public static final BuilderCodec<EngagedCondition> CODEC =
            BuilderCodec.builder(EngagedCondition.class, EngagedCondition::new).build();

    /** The registrable type: the field-less codec + the engaged evaluator. */
    @Nonnull
    public static DialogueConditionType<EngagedCondition> type() {
        return DialogueConditionType.of(TYPE_ID, EngagedCondition.class, CODEC, EngagedCondition::passes);
    }

    private static boolean passes(@Nonnull EngagedCondition condition, @Nonnull DialogueContext ctx) {
        try {
            UUID uuid = ctx.playerRef().getUuid();
            return RoundService.getInstance().registry().isInRound(uuid)
                    || KweebecLobby.isQueued(uuid);
        } catch (Throwable t) {
            return false; // fail closed (hidden); the launch branch (NotInRound) stays the default.
        }
    }
}
