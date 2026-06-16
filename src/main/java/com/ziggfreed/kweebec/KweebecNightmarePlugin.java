package com.ziggfreed.kweebec;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.ziggfreed.kweebec.command.KweebecCommand;
import com.ziggfreed.kweebec.death.CocoonOnDeathSystem;
import com.ziggfreed.kweebec.round.RoundService;

/**
 * Entry point for Kweebec Nightmare, the standalone co-op horror chase minigame.
 *
 * <p>Wires the Chase MVP: the cocoon-on-death ECS system (intercepts player death
 * for in-round players only), the {@code /kweebec} command, and the round service
 * (state machine + cleanup ticker). Integration is outbound native events only -
 * no MMO dependency. Survival mode + the diegetic entry triggers (void-rift pad /
 * shrine block / guide NPC) are designed-for and land next.
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
        // Death interception: cocoon in-round players, leave everyone else's death alone.
        getEntityStoreRegistry().registerSystem(new CocoonOnDeathSystem());

        // Round entry command (the first of the designed-for triggers).
        getCommandRegistry().registerCommand(new KweebecCommand());

        // Round engine: 1 Hz state machine + cleanup ticker.
        RoundService.getInstance().startup();

        LOGGER.atInfo().log("KweebecNightmare setup complete (Chase MVP, in dev).");
    }

    @Override
    protected void shutdown() {
        RoundService.getInstance().shutdown();
        LOGGER.atInfo().log("KweebecNightmare shutdown complete.");
    }
}
