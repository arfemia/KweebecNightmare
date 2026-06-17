package com.ziggfreed.kweebec.hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.narwhals.perfectutils.api.AggroAPI;
import com.ziggfreed.common.world.SurfaceProbe;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;

/**
 * AI-driven hunter: spawns the pack's hostile Blighted-Kweebec role. The RELENTLESS chase is the
 * role's OWN Hostile AI, given a huge sight/alerted/hearing range (see the Blight role Variant) so it
 * pursues the nearest survivor across the whole arena without dropping the target or being pulled back
 * by its leash. That is the smooth, native pursuit; there is no per-tick target re-stamp.
 *
 * <p><b>Aggro override (Perfect Utils, a hard dependency, kept for criteria-based aggro switching).</b>
 * On top of the natural chase we use {@code AggroAPI.taunt} ONLY to FORCE a target the natural AI would
 * not pick on its own: the gate-alert hard-lock, or the loudest shrine channeller when it is not already
 * the nearest survivor. {@code chooseTarget} ranks gate-alert &gt; channeller &gt; nearest; if the choice
 * equals the natural nearest (always true in solo play) we issue NO taunt, so no {@code AggroComponent}
 * lands on the lone survivor - which is what crashed teardown (Perfect Utils removing the component from
 * a just-killed player's changed archetype) and ejected them dead. When an override IS needed, the taunt
 * puts an {@code AggroComponent} on that survivor and Perfect Utils' {@code redirectAggro} re-points the
 * hunter to them; {@link #clearTaunt} releases it (guarded by {@code isTaunting} so we never remove a
 * component the archetype has already lost).
 *
 * <p><b>Corruption-scaled SPEED is unchanged.</b> Each tick {@code applySpeed} snaps
 * {@code ChaseState.hunterSpeed(ruleSet)} to a pre-authored "HunterPace" EntityEffect and swaps it on
 * every hunter when the band changes; the engine folds the effect's {@code HorizontalSpeedMultiplier}
 * into the per-tick walk speed. The role's baked {@code MaxSpeed} is the 1.0x baseline.
 */
public final class AiHunterController implements HunterController {

    /**
     * Taunt burst length. We taunt ONLY when the target changes, never on a stable target - so
     * Perfect Utils' {@code redirectAggro} (which re-points the NPC every engine tick while a taunt is
     * live) runs as a short ~2.5s burst to hand the target to the role's combat AI, then EXPIRES, and
     * the role's own Hostile sensors carry the pursuit. That is the opposite of the old "set the target
     * every tick" behavior. (If a long stable chase ever drops the target we can add a low-frequency
     * re-taunt, but the default is no per-tick churn.)
     */
    private static final long TAUNT_MS = 2500L;
    /** Taunt reach (blocks). Generous so the burst always covers the hunter anywhere in the arena. */
    private static final double TAUNT_RADIUS = 256.0;

    /** The pack hostile Kweebec role id. */
    private final String roleName;

    private final List<Ref<EntityStore>> hunters = new ArrayList<>();
    /** The survivor currently taunted (so we clear the old one and re-issue only on a change). World-thread only. */
    @Nullable
    private UUID currentTaunt;
    @Nullable
    private volatile UUID alertTarget;
    /** Logged once if Perfect Utils is unavailable, so we do not spam the degrade notice. */
    private boolean warnedNoAggro;

    /**
     * Corruption-scaled speed bands (see class javadoc). {@link #SPEED_BANDS} are the multipliers we
     * snap to; {@link #BAND_EFFECT_IDS} are the parallel pack effect ids ({@code null} = 1.0x baseline).
     */
    private static final double[] SPEED_BANDS = {0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
    private static final String[] BAND_EFFECT_IDS = {
            "KweebecNightmare_HunterPace_090",
            null, // 1.0x: role baseline, no effect
            "KweebecNightmare_HunterPace_110",
            "KweebecNightmare_HunterPace_120",
            "KweebecNightmare_HunterPace_130",
            "KweebecNightmare_HunterPace_140",
            "KweebecNightmare_HunterPace_150",
    };

    /** Band index currently applied to all hunters; {@code -1} = none applied yet. */
    private int appliedBand = -1;
    /** Asset-map index of the currently-applied band effect (for removal); {@code MIN_VALUE} = none. */
    private int appliedEffectIndex = Integer.MIN_VALUE;

    public AiHunterController(@Nonnull String roleName) {
        this.roleName = roleName;
    }

    @Override
    public void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] NPCPlugin unavailable; no hunter.");
            return;
        }
        int roleIndex = npc.getIndex(roleName);
        if (roleIndex < 0) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] hunter role '" + roleName + "' not registered; no hunter will spawn.");
            return;
        }

        int count = Math.max(1, round.ruleSet().hunterCount());
        Anchor den = ArenaLayout.HUNTER_DEN;
        int denZ = (int) Math.floor(den.z());
        for (int i = 0; i < count; i++) {
            double offset = (i - (count - 1) / 2.0) * 2.0;
            double hx = den.x() + offset;
            // Floor-snap the den to the rolling grove surface (the flat disc is gone) so the hunter
            // spawns ON the ground, never buried in a hill or floating over a valley. World thread
            // (spawn runs in the round tick), so the column is queryable; degrade to the authored stand Y.
            int standY = SurfaceProbe.standableY(world, (int) Math.floor(hx), denZ, (int) ArenaLayout.STAND_Y);
            Vector3d pos = new Vector3d(hx, standY, den.z());
            Rotation3f rot = new Rotation3f(0f, den.yaw(), 0f);
            try {
                // No spawn-time target lock: the first tick's taunt directs the hunter (the aggro
                // system carries targeting; there is no marked-target seam to seed anymore).
                var spawned = npc.spawnEntity(store, roleIndex, pos, rot, null,
                        (npcEntity, npcRef, st) -> { });
                if (spawned != null) {
                    hunters.add(spawned.first());
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] hunter spawn failed: " + t.getMessage());
            }
        }
        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] spawned " + hunters.size() + " hunter(s) in " + round.roundId());
    }

    @Override
    public void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        hunters.removeIf(ref -> ref == null || !ref.isValid());
        // Speed ramp is independent of targeting and always runs.
        applySpeed(round, store);
        if (hunters.isEmpty()) {
            return;
        }

        AggroAPI api = AggroAPI.get();
        if (api == null) {
            if (!warnedNoAggro) {
                warnedNoAggro = true;
                KweebecNightmarePlugin.LOGGER.atWarning().log(
                        "[Kweebec] Perfect Utils AggroAPI unavailable; hunter relies on its role's natural sensors.");
            }
            return;
        }

        // OVERRIDE-ONLY aggro. The role's natural Hostile AI (huge sight/alerted/hearing range) already
        // pursues the nearest survivor relentlessly, so we call Perfect Utils ONLY to FORCE a target the
        // natural AI would NOT pick on its own - the gate-alert lock, or the loudest shrine channeller
        // when that is not already the nearest survivor. Two wins: (1) no per-tick redirect churn on the
        // common case, and (2) in solo play the only survivor IS the natural target, so no AggroComponent
        // is ever placed on them - which is what made a caught player crash teardown (Perfect Utils
        // removing the component from the dying player's changed archetype) and eject them dead.
        UUID natural = nearestSurvivorToAnyHunter(round, store);
        UUID desired = chooseTarget(round, store);
        UUID override = (desired != null && !desired.equals(natural)) ? desired : null;
        if (override != null) {
            Ref<EntityStore> overrideRef = survivorRef(override);
            if (overrideRef != null && overrideRef.isValid() && !override.equals(currentTaunt)) {
                clearTaunt(api, store);
                api.taunt(store, overrideRef, TAUNT_MS, TAUNT_RADIUS);
                currentTaunt = override;
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec] hunter aggro override -> " + override.toString().substring(0, 8));
            }
        } else {
            // No override needed: release the hunter back to its natural nearest-survivor pursuit.
            clearTaunt(api, store);
        }
    }

    /**
     * Release the current aggro override, if any. Guarded by {@code isTaunting} so we never ask
     * Perfect Utils to remove an {@code AggroComponent} from a survivor whose archetype no longer has
     * it (e.g. one who just died) - the {@code removeComponent} that crashed teardown.
     */
    private void clearTaunt(@Nonnull AggroAPI api, @Nonnull Store<EntityStore> store) {
        if (currentTaunt == null) {
            return;
        }
        Ref<EntityStore> prev = survivorRef(currentTaunt);
        if (prev != null && prev.isValid() && api.isTaunting(store, prev)) {
            api.clear(store, prev);
        }
        currentTaunt = null;
    }

    @Override
    public void onAlert(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        // Hard-lock onto the single nearest active survivor to the gate; the next tick taunts them.
        this.alertTarget = nearestSurvivorToGate(round, store);
        if (alertTarget != null) {
            tick(round, world, store);
        }
    }

    @Override
    public void despawnAll(@Nonnull World world, @Nonnull Store<EntityStore> store) {
        AggroAPI api = AggroAPI.get();
        if (api != null) {
            try {
                clearTaunt(api, store);
            } catch (Throwable ignored) {
                // best effort
            }
        }
        for (Ref<EntityStore> ref : hunters) {
            if (ref != null && ref.isValid()) {
                try {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        hunters.clear();
        currentTaunt = null;
        alertTarget = null;
        appliedBand = -1;
        appliedEffectIndex = Integer.MIN_VALUE;
    }

    // --- target selection ---

    /**
     * Who the hunter hunts this tick: the gate-alert lock first; else the loudest active shrine
     * channeller (channelling noise draws the hunter); else the active survivor nearest to any hunter.
     */
    @Nullable
    private UUID chooseTarget(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        UUID forced = alertTarget;
        if (forced != null && isActiveSurvivor(round, forced)) {
            return forced;
        }
        ChaseState chase = round.chaseState();
        if (chase != null) {
            UUID channeller = chase.loudestChanneller();
            if (channeller != null && isActiveSurvivor(round, channeller)) {
                return channeller;
            }
        }
        return nearestSurvivorToAnyHunter(round, store);
    }

    private boolean isActiveSurvivor(@Nonnull RoundInstance round, @Nonnull UUID uuid) {
        PlayerRoundState st = round.playerState(uuid);
        return st != null && st.isActive();
    }

    @Nullable
    private UUID nearestSurvivorToAnyHunter(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        UUID best = null;
        double bestSq = Double.MAX_VALUE;
        boolean anyHunterPos = false;
        for (Ref<EntityStore> hunterRef : hunters) {
            Vector3d from = positionOf(store, hunterRef);
            if (from == null) {
                continue;
            }
            anyHunterPos = true;
            for (PlayerRoundState st : round.playerStates()) {
                if (!st.isActive()) {
                    continue;
                }
                Vector3d p = positionOf(store, survivorRef(st.playerId()));
                if (p == null) {
                    continue;
                }
                double dx = p.x() - from.x();
                double dz = p.z() - from.z();
                double sq = dx * dx + dz * dz;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = st.playerId();
                }
            }
        }
        if (!anyHunterPos) {
            for (PlayerRoundState st : round.playerStates()) {
                if (st.isActive() && survivorRef(st.playerId()) != null) {
                    return st.playerId();
                }
            }
        }
        return best;
    }

    @Nullable
    private UUID nearestSurvivorToGate(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        Anchor gate = ArenaLayout.GATE;
        UUID best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Vector3d p = positionOf(store, survivorRef(st.playerId()));
            if (p == null) {
                continue;
            }
            double sq = gate.horizontalDistanceSq(p.x(), p.z());
            if (sq < bestSq) {
                bestSq = sq;
                best = st.playerId();
            }
        }
        return best;
    }

    @Nullable
    private static Ref<EntityStore> survivorRef(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerRef pr = Universe.get().getPlayer(uuid);
        return pr == null ? null : pr.getReference();
    }

    @Nullable
    private static Vector3d positionOf(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        return tc == null ? null : tc.getPosition();
    }

    // --- corruption-scaled speed ramp (unchanged) ---

    /**
     * Snap {@link ChaseState#hunterSpeed} to a speed band and, when the band changes, swap the
     * "HunterPace" EntityEffect on every hunter. Best-effort: a missing effect asset (or a hunter
     * without an {@code EffectControllerComponent}) just leaves that hunter at the role baseline.
     */
    private void applySpeed(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        int band = nearestBandIndex(chase.hunterSpeed(round.ruleSet()));
        if (band == appliedBand) {
            return; // unchanged - the common case; no per-tick effect churn
        }

        var assetMap = EntityEffect.getAssetMap();
        String newId = BAND_EFFECT_IDS[band];
        int newEffectIndex = newId == null ? Integer.MIN_VALUE : assetMap.getIndex(newId);
        EntityEffect newEffect = newEffectIndex == Integer.MIN_VALUE ? null : assetMap.getAsset(newEffectIndex);
        if (newId != null && newEffect == null) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] hunter pace effect '" + newId + "' not registered; hunter stays at role baseline.");
        }

        for (Ref<EntityStore> hunterRef : hunters) {
            if (hunterRef == null || !hunterRef.isValid()) {
                continue;
            }
            EffectControllerComponent effects = store.getComponent(hunterRef, EffectControllerComponent.getComponentType());
            if (effects == null) {
                continue;
            }
            try {
                if (appliedEffectIndex != Integer.MIN_VALUE) {
                    effects.removeEffect(hunterRef, appliedEffectIndex, store);
                }
                if (newEffect != null) {
                    effects.addEffect(hunterRef, newEffect, store);
                }
            } catch (Throwable t) {
                KweebecNightmarePlugin.LOGGER.atFine().log(
                        "[Kweebec] hunter pace swap failed: " + t.getMessage());
            }
        }
        appliedBand = band;
        appliedEffectIndex = newEffect == null ? Integer.MIN_VALUE : newEffectIndex;
    }

    /** Index of the {@link #SPEED_BANDS} entry nearest {@code mult} (ties pick the slower band). */
    private static int nearestBandIndex(double mult) {
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < SPEED_BANDS.length; i++) {
            double diff = Math.abs(SPEED_BANDS[i] - mult);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }
}
