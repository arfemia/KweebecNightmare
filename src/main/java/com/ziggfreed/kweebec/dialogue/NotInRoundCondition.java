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
 * Kweebec's dialogue condition: an option is shown only while the player is NOT
 * already engaged - neither in a live round nor sitting in a queue. Gates every
 * preset-launch ({@link StartRoundAction}) option off the moment the player commits,
 * so the guide offers an {@code already_engaged} branch instead of a dead button.
 *
 * <p>A field-less {@link DialogueCondition} (authored {@code {"Type":"NotInRound"}}).
 * The evaluator returns {@code true} to SHOW the option; it MUST NOT throw (a throw,
 * like a missing evaluator, HIDES the option), so it is fully guarded and fails open
 * (visible) - the queue re-guards {@code alreadyEngaged} at join, so a stray visible
 * option can never double-launch.
 */
public final class NotInRoundCondition extends DialogueCondition {

    /** {@code "NotInRound"} - the authored {@code Type} discriminator. */
    public static final String TYPE_ID = "NotInRound";

    public static final BuilderCodec<NotInRoundCondition> CODEC =
            BuilderCodec.builder(NotInRoundCondition.class, NotInRoundCondition::new).build();

    /** The registrable type: the field-less codec + the not-engaged evaluator. */
    @Nonnull
    public static DialogueConditionType<NotInRoundCondition> type() {
        return DialogueConditionType.of(TYPE_ID, NotInRoundCondition.class, CODEC, NotInRoundCondition::passes);
    }

    private static boolean passes(@Nonnull NotInRoundCondition condition, @Nonnull DialogueContext ctx) {
        try {
            UUID uuid = ctx.playerRef().getUuid();
            return !RoundService.getInstance().registry().isInRound(uuid)
                    && !KweebecLobby.isQueued(uuid);
        } catch (Throwable t) {
            return true; // fail open (visible); the queue re-guards at join.
        }
    }
}
