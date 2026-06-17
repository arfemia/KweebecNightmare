package com.ziggfreed.kweebec.util;

import javax.annotation.Nonnull;

import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Logging wrapper for parse / asset-load paths that a unit JVM can reach.
 *
 * <p>The raw flogger {@link KweebecNightmarePlugin#LOGGER} throws when no Hytale
 * log manager is installed (a unit-test JVM): the resulting {@code Error} escapes
 * {@code catch (Exception)} blocks and crashes the test. These helpers swallow any
 * logging failure so a parse/validate path stays unit-reachable. Mirrors hyMMO's
 * {@code util.SafeLog}.
 *
 * <p>Use this ONLY on parse/validate/load code; ordinary world-thread runtime code
 * keeps using {@link KweebecNightmarePlugin#LOGGER} directly.
 */
public final class SafeLog {

    private SafeLog() {
    }

    public static void info(@Nonnull String message) {
        try {
            KweebecNightmarePlugin.LOGGER.atInfo().log(message);
        } catch (Throwable ignored) {
            // no log manager (unit JVM) - swallow so the caller stays test-reachable
        }
    }

    public static void warn(@Nonnull String message) {
        try {
            KweebecNightmarePlugin.LOGGER.atWarning().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void severe(@Nonnull String message) {
        try {
            KweebecNightmarePlugin.LOGGER.atSevere().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void fine(@Nonnull String message) {
        try {
            KweebecNightmarePlugin.LOGGER.atFine().log(message);
        } catch (Throwable ignored) {
        }
    }
}
