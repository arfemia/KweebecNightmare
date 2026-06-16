package com.ziggfreed.kweebec.round;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Thread-safe registry of live rounds + the player-to-round binding. The round
 * engine owns this because the Hytale engine has no player-party API.
 */
public final class RoundRegistry {

    private final Map<String, RoundInstance> byId = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToRound = new ConcurrentHashMap<>();

    public void add(@Nonnull RoundInstance inst) {
        byId.put(inst.roundId(), inst);
    }

    public void remove(@Nonnull String roundId) {
        RoundInstance inst = byId.remove(roundId);
        if (inst != null) {
            for (UUID p : inst.participants()) {
                playerToRound.remove(p, roundId);
            }
        }
    }

    public void bindPlayer(@Nonnull UUID playerId, @Nonnull String roundId) {
        playerToRound.put(playerId, roundId);
    }

    public void unbindPlayer(@Nonnull UUID playerId) {
        playerToRound.remove(playerId);
    }

    @Nullable
    public RoundInstance byId(@Nonnull String roundId) {
        return byId.get(roundId);
    }

    @Nullable
    public RoundInstance forPlayer(@Nonnull UUID playerId) {
        String id = playerToRound.get(playerId);
        return id == null ? null : byId.get(id);
    }

    public boolean isInRound(@Nonnull UUID playerId) {
        return playerToRound.containsKey(playerId);
    }

    @Nonnull
    public Collection<RoundInstance> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int activeRoundCount() {
        return byId.size();
    }
}
