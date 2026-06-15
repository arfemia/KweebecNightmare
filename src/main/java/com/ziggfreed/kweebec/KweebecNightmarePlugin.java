package com.ziggfreed.kweebec;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * Entry point for Kweebec Nightmare, the standalone co-op horror chase minigame.
 *
 * <p>Scaffold stage: this only stands the plugin up so the build + load can be
 * verified end to end. The round state machine, hunter AI, atmosphere lock,
 * feedback stack, lobby trigger and the optional MMOSkillTree bridge land in the
 * later phases (see the plan note {@code utilize-a-new-git-snappy-moon}).
 */
public class KweebecNightmarePlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static KweebecNightmarePlugin instance;

    @Nonnull
    public static KweebecNightmarePlugin getInstance() {
        return instance;
    }

    public KweebecNightmarePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("KweebecNightmare initializing...");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("KweebecNightmare setup complete (scaffold v%s).", "0.1.0");
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("KweebecNightmare shutdown complete.");
    }
}
