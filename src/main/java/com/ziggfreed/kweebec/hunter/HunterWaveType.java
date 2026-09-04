package com.ziggfreed.kweebec.hunter;

import com.hypixel.hytale.server.npc.NPCPlugin;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The one instruction {@code Type} this mod adds to the shared NPC builder vocabulary,
 * {@code KweebecHunterWave}, legal only inside an encounter script: the hunter encounter script
 * ({@code Server/EncounterManager/KweebecNightmare_Hunters.json}) fires it at each rung of its
 * escalation, and it is the only place a script can reach the round's hunter controller.
 *
 * <p>Registered from the plugin's {@code setup()}, before any builder file is read (the engine reads
 * every script after every plugin's setup returns), which needs the NPC plugin up first: the manifest
 * depends on {@code Hytale:NPC} for exactly that. A Type name is process-global and a second
 * registration of one throws, so this runs once.
 */
public final class HunterWaveType {

    public static final String NAME = "KweebecHunterWave";

    private static volatile boolean registered;

    private HunterWaveType() {
    }

    /** Register the Type (idempotent, guarded). */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        try {
            NPCPlugin npc = NPCPlugin.get();
            if (npc == null) {
                SafeLog.warn("[Kweebec] the NPC plugin is not up, so the " + NAME
                        + " encounter Type is not registered and the hunter waves' script cannot load");
                return;
            }
            npc.registerCoreComponentType(NAME, BuilderKweebecHunterWave::new);
            registered = true;
            SafeLog.info("[Kweebec] registered encounter Type: " + NAME);
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] registering the " + NAME + " encounter Type failed: " + t.getMessage());
        }
    }

    public static boolean isRegistered() {
        return registered;
    }
}
