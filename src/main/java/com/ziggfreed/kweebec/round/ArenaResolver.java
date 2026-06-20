package com.ziggfreed.kweebec.round;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.common.instance.arena.ArenaDefinitionAsset;
import com.ziggfreed.common.instance.arena.ArenaDefinitionConfig;

/**
 * The ONE authority for which instance world a round spawns into, resolved in
 * {@code RoundService.startRound} BEFORE {@code spawnInstance} (you cannot pick the instance after you
 * have already spawned it). Chase keeps its per-difficulty worldStructure-suffix mapping; the PvP modes
 * pick a pack-authored {@link ArenaDefinitionAsset} from the {@code ziggfreed-common} arena catalog (by
 * pinned id, else deterministically among the mode-tagged arenas), so multiple themed arenas - and a
 * data-only 3-point Domination variant with its own worldgen - are pure JSON.
 */
public final class ArenaResolver {

    private ArenaResolver() {
    }

    /**
     * The spawn target: the {@code instance.bson} asset name plus the chosen arena definition (null for
     * Chase, which runs off {@code ArenaLayout} constants rather than authored anchors).
     */
    public record Resolved(@Nonnull String instanceName, @Nullable ArenaDefinitionAsset arena) {
    }

    @Nonnull
    public static Resolved resolve(@Nonnull RoundInstance round) {
        KweebecMode mode = round.mode();
        if (mode == KweebecMode.CHASE || mode == KweebecMode.SURVIVAL) {
            return new Resolved(chaseInstanceName(round), null);
        }
        ArenaDefinitionAsset arena = pickArena(round);
        String instanceName = mode.instanceName();
        if (arena != null && arena.instanceName() != null && !arena.instanceName().isBlank()) {
            instanceName = arena.instanceName();
        }
        return new Resolved(instanceName, arena);
    }

    /**
     * Chase / Survival: derive the instance from the rule-set's {@code worldStructure} (the per-difficulty
     * biome), mirroring the worldgen suffix on the mode's base instance ({@code _Grove_Dread} ->
     * {@code _Chase_Dread}). A worldStructure not following the convention falls back to the base instance.
     */
    @Nonnull
    private static String chaseInstanceName(@Nonnull RoundInstance round) {
        String base = round.mode().instanceName();
        String ws = round.ruleSet().worldStructure();
        if (ws == null || ws.equals(RuleSet.DEFAULT_WORLD_STRUCTURE)
                || !ws.startsWith(RuleSet.DEFAULT_WORLD_STRUCTURE)) {
            return base;
        }
        return base + ws.substring(RuleSet.DEFAULT_WORLD_STRUCTURE.length());
    }

    /**
     * Pick the PvP arena: a pinned {@code arenaId} wins; else choose deterministically (by round id, so a
     * given round is stable but distinct rounds vary) among the arenas carrying {@code arenaTag} (defaulting
     * to the mode id, e.g. {@code "clash"} / {@code "domination"}); {@code null} when the catalog has none
     * (the resolver then falls back to the mode's base instance + the mode builds a default layout).
     */
    @Nullable
    private static ArenaDefinitionAsset pickArena(@Nonnull RoundInstance round) {
        ArenaDefinitionConfig cfg = ArenaDefinitionConfig.getInstance();
        RuleSet rs = round.ruleSet();
        if (rs.arenaId() != null && !rs.arenaId().isBlank()) {
            ArenaDefinitionAsset pinned = cfg.byId(rs.arenaId());
            if (pinned != null) {
                return pinned;
            }
        }
        String tag = (rs.arenaTag() != null && !rs.arenaTag().isBlank())
                ? rs.arenaTag() : round.mode().id();
        List<ArenaDefinitionAsset> candidates = cfg.byTag(tag);
        if (candidates.isEmpty()) {
            return null;
        }
        int idx = Math.floorMod(round.roundId().hashCode(), candidates.size());
        return candidates.get(idx);
    }
}
