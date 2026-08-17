package com.ziggfreed.kweebec.util;

import javax.annotation.Nonnull;

import com.ziggfreed.common.util.GuardedLogger;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Logging wrapper for parse / asset-load paths that a unit JVM can reach.
 *
 * <p>A thin static wrapper over one shared {@link GuardedLogger} instance (no prefix). See
 * {@link GuardedLogger} for the guard itself: the raw flogger {@link KweebecNightmarePlugin#LOGGER}
 * throws when no Hytale log manager is installed (a unit-test JVM), and the resulting
 * {@code Error} escapes {@code catch (Exception)} blocks and crashes the test - the guard swallows
 * that so a parse/validate path stays unit-reachable.
 *
 * <p>Use this ONLY on parse/validate/load code; ordinary world-thread runtime code
 * keeps using {@link KweebecNightmarePlugin#LOGGER} directly.
 */
public final class SafeLog {

    private static final GuardedLogger DELEGATE = new GuardedLogger(() -> KweebecNightmarePlugin.LOGGER, "");

    private SafeLog() {
    }

    public static void info(@Nonnull String message) {
        DELEGATE.info(message);
    }

    public static void warn(@Nonnull String message) {
        DELEGATE.warn(message);
    }

    public static void severe(@Nonnull String message) {
        DELEGATE.severe(message);
    }

    public static void fine(@Nonnull String message) {
        DELEGATE.fine(message);
    }
}
