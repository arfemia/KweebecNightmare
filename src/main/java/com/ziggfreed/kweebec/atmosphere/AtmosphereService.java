package com.ziggfreed.kweebec.atmosphere;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Locks a round world into a frozen, dark midnight under a forced dark weather. This is the
 * mod-SPECIFIC POLICY layer (the dark-weather candidate list + which weather to choose); the
 * time + weather ENGINE mechanism (set+pause the clock, validate + force the weather) is
 * delegated to ziggfreed-common's {@link com.ziggfreed.common.world.AtmosphereService}.
 * Kweebec owns WHICH weather to force; common owns HOW.
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

    /**
     * Lock the world to a frozen dark midnight under the first dark weather that resolves.
     * Safe to call from any thread (the common service self-hops to the world thread).
     * Kweebec picks the weather id from {@link #DARK_WEATHER_CANDIDATES}; common's
     * {@link com.ziggfreed.common.world.AtmosphereService#lock} pins + pauses the clock at
     * {@link #MIDNIGHT} and validates + forces the chosen weather (a {@code null} choice
     * leaves the weather untouched).
     */
    public static void lock(@Nonnull World world) {
        String chosen = firstValidWeather();
        com.ziggfreed.common.world.AtmosphereService.lock(world, MIDNIGHT, chosen);
    }

    @Nullable
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
