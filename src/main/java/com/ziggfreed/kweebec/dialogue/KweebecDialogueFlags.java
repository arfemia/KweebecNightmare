package com.ziggfreed.kweebec.dialogue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.ziggfreed.common.dialogue.DialogueFlagStore;

/**
 * Kweebec's in-memory per-player dialogue-flag memory. A minigame has no persistent
 * save analogue for dialogue flags (the only consumer of flag PERSISTENCE is an
 * MMO's quest-reset semantics), so a per-player {@link Set} kept in a static map
 * matches the session lifecycle: cheap, no component, no codec. {@link #clear} is
 * the hook to wipe a player's flags on round exit when the dialogue is later folded
 * into the round flow.
 */
public final class KweebecDialogueFlags {

    private static final Map<UUID, Set<String>> FLAGS = new ConcurrentHashMap<>();

    private KweebecDialogueFlags() {
    }

    /** A {@link DialogueFlagStore} view over one player's flag set. */
    @Nonnull
    public static DialogueFlagStore store(@Nonnull UUID uuid) {
        return new DialogueFlagStore() {
            @Override
            public boolean has(@Nonnull String flag) {
                Set<String> set = FLAGS.get(uuid);
                return set != null && set.contains(flag);
            }

            @Override
            public void set(@Nonnull String flag) {
                FLAGS.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(flag);
            }
        };
    }

    /** Drop a player's dialogue flags (e.g. on round exit). */
    public static void clear(@Nonnull UUID uuid) {
        FLAGS.remove(uuid);
    }
}
