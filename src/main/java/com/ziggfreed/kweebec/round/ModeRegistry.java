package com.ziggfreed.kweebec.round;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The {@link KweebecMode} -> {@link RoundMode} dispatch table, populated ONCE at plugin setup (the
 * composition root imports the concrete modes; the engine never does). {@link RoundStateMachine} and
 * {@link RoundService} call {@link #get(KweebecMode)} instead of a {@code switch}, so a new mode is one
 * {@code register} call - the open/closed seam.
 *
 * <p>Registration happens on the main setup thread before any round starts; reads happen on world
 * threads. The map is built once and never mutated after, so an {@link EnumMap} read is safe.
 */
public final class ModeRegistry {

    private static final Map<KweebecMode, RoundMode> MODES = new EnumMap<>(KweebecMode.class);

    private ModeRegistry() {
    }

    /** Register a mode implementation (idempotent overwrite). Call once per mode at plugin setup. */
    public static void register(@Nonnull KweebecMode mode, @Nonnull RoundMode impl) {
        MODES.put(mode, impl);
    }

    /** The mode implementation for a round, or {@code null} if its mode was never registered. */
    @Nullable
    public static RoundMode get(@Nonnull KweebecMode mode) {
        return MODES.get(mode);
    }

    /** Whether a mode has an implementation (used to reject a start for an unbuilt mode). */
    public static boolean has(@Nonnull KweebecMode mode) {
        return MODES.containsKey(mode);
    }
}
