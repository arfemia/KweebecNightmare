package com.ziggfreed.kweebec.atmosphere;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;

/**
 * Locks a round world into a frozen, dark midnight under a forced dark weather.
 * Midnight ({@code dayTime ~= 0.5}) + paused clock so the night never advances,
 * plus a whole-world forced weather (validated first; an unknown id would blank
 * the sky). All calls are world-thread-only and self-hop via {@code world.execute}.
 */
public final class AtmosphereService {

    /**
     * Midnight. dayTime is a fraction of the calendar day from hour 0:
     * 0.0 = midnight (darkest, night is centered here), 0.5 = noon (brightest).
     * Verified against {@code WorldTimeResource.setDayTime} ({@code dayStart.truncatedTo(DAYS)}).
     */
    private static final double MIDNIGHT = 0.0;

    /**
     * Dark weather, first match wins: an optional pack weather (deferred to polish),
     * then the vanilla Void/Blood-Moon/Terror weathers (all confirmed to exist).
     */
    private static final String[] DARK_WEATHER_CANDIDATES = {
            "KweebecNightmare_VoidBlight", "Portals_Void_Event_Intense", "Blood_Moon", "Terror_Weather", "Void"
    };

    private AtmosphereService() {
    }

    /** Lock the world to a frozen dark midnight. Safe to call from any thread. */
    public static void lock(@Nonnull World world) {
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Midnight + freeze the clock.
                WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
                time.setDayTime(MIDNIGHT, world, store);
                WorldConfig cfg = world.getWorldConfig();
                cfg.setGameTimePaused(true);

                // Force the first dark weather that resolves (validate before setting).
                String chosen = firstValidWeather();
                if (chosen != null) {
                    WeatherResource weather = store.getResource(WeatherResource.getResourceType());
                    weather.setForcedWeather(chosen);
                    cfg.setForcedWeather(chosen);
                }

                cfg.markChanged();
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] atmosphere lock failed: " + t.getMessage());
            }
        });
    }

    private static String firstValidWeather() {
        for (String id : DARK_WEATHER_CANDIDATES) {
            try {
                if (Weather.getAssetMap().getIndex(id) != Integer.MIN_VALUE) {
                    return id;
                }
            } catch (Throwable ignored) {
                // asset map not ready / id missing - try the next
            }
        }
        return null;
    }
}
