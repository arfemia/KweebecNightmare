package com.ziggfreed.kweebec;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.narwhals.perfectutils.api.AggroAPI;
import com.narwhals.perfectutils.api.StunMobAPI;
import com.ziggfreed.common.npc.NpcActions;
import com.ziggfreed.common.npc.NpcDialogueDepsRegistry;
import com.ziggfreed.kweebec.asset.KweebecAssetRegistrar;
import com.ziggfreed.kweebec.command.KweebecCommand;
import com.ziggfreed.kweebec.command.KweebecTalkCommand;
import com.ziggfreed.kweebec.death.CocoonOnDeathSystem;
import com.ziggfreed.kweebec.dialogue.KweebecDialogue;
import com.ziggfreed.kweebec.event.KweebecDamageSystem;
import com.ziggfreed.kweebec.event.MoonbloomCollectSystem;
import com.ziggfreed.kweebec.interaction.ShrineSubmitInteraction;
import com.ziggfreed.kweebec.lobby.KweebecLobby;
import com.ziggfreed.kweebec.npc.KweebecGuideConfig;
import com.ziggfreed.kweebec.npc.KweebecGuidePlacementStore;
import com.ziggfreed.kweebec.npc.KweebecGuideSpawn;
import com.ziggfreed.kweebec.experience.KweebecExperience;
import com.ziggfreed.kweebec.round.RoundInventoryGuard;
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
        // Register the generic ziggfreed-common "press-F opens a dialogue" NPC action
        // (ZigOpenDialogue) and point it at kweebec's DialoguePageDeps, BEFORE any NPC
        // role asset referencing {Type:ZigOpenDialogue} loads - else the guide role
        // silently fails to parse. Mirrors how the MMO registers OpenMmoUi in setup().
        NpcDialogueDepsRegistry.set(KweebecDialogue::deps);
        NpcActions.register();

        // Custom asset stores (Presets, Hunters, Control) - registered FIRST so they
        // exist before the engine's asset-load event; their load listeners fold pack
        // content into PresetConfig / HunterArchetypeConfig (defaults < pack < owner).
        KweebecAssetRegistrar.registerAll(this);

        // Death interception: cocoon in-round players, leave everyone else's death alone.
        getEntityStoreRegistry().registerSystem(new CocoonOnDeathSystem());

        // Damage observer: thrown-Moonbloom stun attribution + per-survivor damage-taken
        // scoring. Read-only outside in-round players (never alters damage elsewhere).
        getEntityStoreRegistry().registerSystem(new KweebecDamageSystem());

        // Moonbloom-gathered observer: credits a lifetime stat when an in-round player harvests
        // a Moonbloom plant (gather is asset-only, so this supplies the Java seam). Read-only.
        getEntityStoreRegistry().registerSystem(new MoonbloomCollectSystem());

        // Round entry command (the first of the designed-for triggers).
        getCommandRegistry().registerCommand(new KweebecCommand());

        // Dialogue demo trigger: opens the shared ziggfreed-common dialogue page.
        getCommandRegistry().registerCommand(new KweebecTalkCommand());

        // Interactable shrine furnace: the block's RootInteraction fires this handler on F (submit Moonbloom).
        try {
            getCodecRegistry(Interaction.CODEC).register(
                    ShrineSubmitInteraction.TYPE_NAME, ShrineSubmitInteraction.class, ShrineSubmitInteraction.CODEC);
            LOGGER.atInfo().log("[Kweebec] registered interaction: " + ShrineSubmitInteraction.TYPE_NAME);
        } catch (Exception e) {
            LOGGER.atSevere().log("[Kweebec] failed to register shrine interaction: " + e.getMessage());
        }

        // The shared instance-experience layer: the (common) leaderboard + party service +
        // pending-reward store + the page deps, loaded from the plugin data dir. Built after
        // KweebecLobby.init below would be ideal, but it only reads KweebecLobby.service()
        // lazily at page-open, so order here is fine.
        KweebecExperience.init(getDataDirectory());

        // Grove Warden guide auto-spawn config (<data dir>/guide.json; defaults written on first run):
        // which worlds get the guide (default the "default" overworld only) + its spawn offset/yaw.
        // Mirrors MMO Skill Tree's spawn-hub.json.
        KweebecGuideConfig.getInstance().load(getDataDirectory());

        // Persistent once-per-world marker (<data dir>/guide-placements.json) so a reboot never stacks a
        // second guide beside the one already saved in the world. Loaded BEFORE the player-ready hook
        // below can fire. Mirrors MMO Skill Tree's MmoNpcPlacementStore auto-spawn marker.
        KweebecGuidePlacementStore.getInstance().init(getDataDirectory());

        // Inventory preserve/restore: snapshot + strip a survivor's gear on round entry, restore it
        // exactly on exit. Persisted under the data dir so a crash/disconnect/restart mid-round never
        // eats gear (the next-login net below re-applies a leftover snapshot).
        RoundInventoryGuard.init(getDataDirectory());

        // Round engine: 1 Hz state machine + cleanup ticker.
        RoundService.getInstance().startup();

        // Matchmaking lobby: the fill-window + countdown queue the guide dialogue and
        // /kweebec start feed (its launcher closes over RoundService.startChase).
        KweebecLobby.init();

        // Auto-spawn the "Grove Warden" guide once per world on player-ready (the diegetic
        // entry trigger); press-F opens the backstory dialogue + preset launcher.
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, KweebecGuideSpawn::onPlayerReady);

        // Re-deliver any rewards a player could not claim last time because their inventory
        // was full (the no-claim-with-full-inventory guard holds them until they make space).
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, KweebecExperience::onPlayerReady);

        // Inventory-restore crash/disconnect net: re-apply a leftover inventory snapshot for a player
        // who entered a round but never got restored in-instance (crash / disconnect / restart mid-round).
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, RoundInventoryGuard::onPlayerReady);

        // Perfect Utils is a hard dependency (loads first); confirm the aggro API resolved so a
        // missing/older jar is obvious in the log rather than a silent fall-back to natural sensors.
        if (AggroAPI.get() != null) {
            LOGGER.atInfo().log("[Kweebec] Perfect Utils AggroAPI present; hunter will use dynamic aggro.");
        } else {
            LOGGER.atWarning().log(
                    "[Kweebec] Perfect Utils AggroAPI NOT present at setup; hunter will fall back to natural sensors.");
        }
        // StunMobAPI backs the thrown-Moonbloom hunter freeze; a missing/older jar means
        // throws still register a hit (counter + score) but the hunter will not freeze.
        if (StunMobAPI.get() != null) {
            LOGGER.atInfo().log("[Kweebec] Perfect Utils StunMobAPI present; thrown Moonbloom will stun hunters.");
        } else {
            LOGGER.atWarning().log(
                    "[Kweebec] Perfect Utils StunMobAPI NOT present at setup; thrown Moonbloom will not freeze hunters.");
        }

        LOGGER.atInfo().log("KweebecNightmare setup complete (Chase MVP, in dev).");
    }

    @Override
    protected void shutdown() {
        RoundService.getInstance().shutdown();
        KweebecLobby.shutdown();
        KweebecExperience.shutdown();
        LOGGER.atInfo().log("KweebecNightmare shutdown complete.");
    }
}
