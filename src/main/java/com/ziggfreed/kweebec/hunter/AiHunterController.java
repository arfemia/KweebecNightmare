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
import com.ziggfreed.kweebec.asset.HunterArchetypeAsset;
import com.ziggfreed.kweebec.asset.HunterArchetypeConfig;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * AI-driven hunter: spawns a WEIGHTED ROSTER of pack hostile-Kweebec archetypes read from
 * {@link HunterArchetypeConfig} (no hardcoded ids - a pack can add Lunger/Spitter/Ambusher variants).
 * Each archetype binds its own NPC role + per-archetype corruption-scaled speed bands. The RELENTLESS
 * chase is each role's OWN Hostile AI, given a huge sight/alerted/hearing range (see the Blight role
 * Variant) so it pursues the nearest survivor across the whole arena without dropping the target or
 * being pulled back by its leash. That is the smooth, native pursuit; there is no per-tick target
 * re-stamp.
 *
 * <p><b>Roster selection.</b> {@code spawn} builds the roster off the round: the rule-set's
 * {@code hunterArchetype()} (if set) seeds a primary archetype, then weighted picks fill out the rest;
 * each archetype's eligibility is gated by its {@code spawnTier()} against the current corruption tier,
 * and the total hunter count scales modestly with {@code hunterCount()} + party size (capped by
 * {@link #MAX_HUNTERS}). As corruption rises past a higher {@code spawnTier} mid-round, {@code tick}
 * may spawn ONE extra archetype hunter (hard-capped, so it can never runaway-spawn). Each spawned
 * hunter is tracked as a {@link HunterUnit} carrying its ref + its archetype + its own applied speed
 * band, so every archetype paces on its OWN {@code SpeedBands}/{@code BandEffectIds} ladder.
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
 * <p><b>Corruption-scaled SPEED is now PER-HUNTER.</b> Each tick {@code applySpeed} snaps
 * {@code ChaseState.hunterSpeed(ruleSet)} to a band on EACH hunter's OWN archetype ladder and swaps the
 * pre-authored "HunterPace" EntityEffect on that hunter when its band changes; the engine folds the
 * effect's {@code HorizontalSpeedMultiplier} into the per-tick walk speed. The role's baked
 * {@code MaxSpeed} is the 1.0x baseline. The {@code AiHunterController(String)} fallback constructor is
 * unchanged so {@code RoundService} still compiles; the roster is read from the round inside
 * {@code spawn} (the fallback role only spawns when the config yields no eligible archetype).
 */
public final class AiHunterController implements HunterController {

    /**
     * One live hunter: its entity ref, the archetype it spawned from (so it paces on its OWN
     * {@code SpeedBands}/{@code BandEffectIds}), and the band/effect currently applied to it. PER-HUNTER
     * state replaces the old single shared {@code appliedBand}/{@code appliedEffectIndex} scalars so a
     * mixed roster paces distinctly. World-thread only.
     */
    private static final class HunterUnit {
        final Ref<EntityStore> ref;
        final HunterArchetypeAsset archetype;
        /** Band index currently applied to this hunter; {@code -1} = none applied yet. */
        int appliedBand = -1;
        /** Asset-map index of this hunter's currently-applied band effect; {@code MIN_VALUE} = none. */
        int appliedEffectIndex = Integer.MIN_VALUE;

        HunterUnit(@Nonnull Ref<EntityStore> ref, @Nonnull HunterArchetypeAsset archetype) {
            this.ref = ref;
            this.archetype = archetype;
        }
    }

    /** Hard ceiling on total live hunters, so corruption escalation can never runaway-spawn. */
    private static final int MAX_HUNTERS = 6;
    /** Total hunters cannot exceed {@code base hunterCount + this * (partySize - 1)} (modest party scaling). */
    private static final int EXTRA_PER_EXTRA_PLAYER = 1;

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

    /**
     * The fallback pack hostile-Kweebec role id (the {@code AiHunterController(String)} ctor seed). Used
     * ONLY when the {@link HunterArchetypeConfig} roster yields no eligible archetype, so the round still
     * spawns a hunter. The normal path spawns the config roster, ignoring this.
     */
    private final String fallbackRoleName;

    /** Live roster of spawned hunters, each with its own archetype + band state. World-thread only. */
    private final List<HunterUnit> hunters = new ArrayList<>();
    /** The archetypes that spawned this round (for mid-round corruption escalation). World-thread only. */
    private final List<HunterArchetypeAsset> rosterPlan = new ArrayList<>();
    /** Ceiling on total hunters for this round (computed in {@code spawn} from party size). */
    private int hunterCap = 1;
    /** The survivor currently taunted (so we clear the old one and re-issue only on a change). World-thread only. */
    @Nullable
    private UUID currentTaunt;
    @Nullable
    private volatile UUID alertTarget;
    /** Logged once if Perfect Utils is unavailable, so we do not spam the degrade notice. */
    private boolean warnedNoAggro;

    /**
     * Fallback speed bands when an archetype authored none (mirrors the historical hardcoded ladder).
     * {@link #FALLBACK_SPEED_BANDS} are the multipliers; {@link #FALLBACK_BAND_EFFECT_IDS} are the
     * parallel pace-effect ids (empty / {@code null} = 1.0x baseline, no effect).
     */
    private static final double[] FALLBACK_SPEED_BANDS = {0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
    private static final String[] FALLBACK_BAND_EFFECT_IDS = {
            "KweebecNightmare_HunterPace_090",
            "", // 1.0x: role baseline, no effect
            "KweebecNightmare_HunterPace_110",
            "KweebecNightmare_HunterPace_120",
            "KweebecNightmare_HunterPace_130",
            "KweebecNightmare_HunterPace_140",
            "KweebecNightmare_HunterPace_150",
    };

    public AiHunterController(@Nonnull String roleName) {
        this.fallbackRoleName = roleName;
    }

    @Override
    public void spawn(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            KweebecNightmarePlugin.LOGGER.atWarning().log("[Kweebec] NPCPlugin unavailable; no hunter.");
            return;
        }

        int partySize = Math.max(1, round.partySize());
        int desired = Math.max(1, round.ruleSet().hunterCount())
                + EXTRA_PER_EXTRA_PLAYER * Math.max(0, partySize - 1);
        this.hunterCap = Math.min(MAX_HUNTERS, desired);

        int tier = currentTier(round);
        // Build the roster plan: archetypes eligible at the current corruption tier, the rule-set's
        // primary archetype first (if any/eligible), then weighted picks fill to the cap. No hardcoded
        // ids - everything is read from the config.
        rosterPlan.clear();
        rosterPlan.addAll(planRoster(round, tier, this.hunterCap));

        Anchor den = ArenaLayout.HUNTER_DEN;
        int denZ = (int) Math.floor(den.z());
        for (int i = 0; i < rosterPlan.size(); i++) {
            HunterArchetypeAsset a = rosterPlan.get(i);
            spawnArchetypeAt(npc, store, world, a, a.roleName(), i, rosterPlan.size(), den, denZ);
        }

        if (hunters.isEmpty()) {
            // Roster yielded nothing spawnable (no eligible archetype, or every role unregistered):
            // fall back to the ctor role so a round always has a hunter. Bands come from the config's
            // default archetype (resolve(null) is always non-null), but the SPAWNED role is the ctor's.
            HunterArchetypeAsset fallbackBands = HunterArchetypeConfig.getInstance().resolve(null);
            spawnArchetypeAt(npc, store, world, fallbackBands, fallbackRoleName, 0, 1, den, denZ);
        }

        KweebecNightmarePlugin.LOGGER.atInfo().log(
                "[Kweebec] spawned " + hunters.size() + " hunter(s) in " + round.roundId()
                        + " (cap=" + hunterCap + ", tier=" + tier + ")");
    }

    /**
     * Spawn one hunter at the den (floor-snapped + offset-spread, like the historical loop), tracking it
     * as a {@link HunterUnit} that paces on {@code bandSource}'s ladder. {@code roleName} is the role to
     * actually spawn (usually {@code bandSource.roleName()}, but the fallback path spawns the ctor role
     * with the default archetype's bands). Best-effort: an unregistered role or a spawn failure is logged
     * and skipped, never thrown into the round loop.
     */
    private void spawnArchetypeAt(@Nonnull NPCPlugin npc, @Nonnull Store<EntityStore> store,
                                  @Nonnull World world, @Nonnull HunterArchetypeAsset bandSource,
                                  @Nullable String roleName, int index, int total,
                                  @Nonnull Anchor den, int denZ) {
        if (roleName == null || roleName.isBlank()) {
            SafeLog.warn("[Kweebec] hunter archetype '" + bandSource.getId()
                    + "' has no RoleName; skipped.");
            return;
        }
        int roleIndex = npc.getIndex(roleName);
        if (roleIndex < 0) {
            SafeLog.warn("[Kweebec] hunter role '" + roleName + "' (archetype '" + bandSource.getId()
                    + "') not registered; skipped.");
            return;
        }
        double offset = (index - (total - 1) / 2.0) * 2.0;
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
                hunters.add(new HunterUnit(spawned.first(), bandSource));
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] hunter spawn failed (archetype '" + bandSource.getId()
                    + "', role '" + roleName + "'): " + t.getMessage());
        }
    }

    /**
     * Plan a roster of up to {@code cap} archetypes eligible at the given corruption {@code tier}: the
     * rule-set's primary archetype first (when set + eligible), then weighted picks (with replacement,
     * honoring each archetype's {@code count()} as how many that pick adds) fill to the cap. Reads only
     * {@link HunterArchetypeConfig} - no hardcoded ids; an empty result means the caller falls back.
     */
    @Nonnull
    private List<HunterArchetypeAsset> planRoster(@Nonnull RoundInstance round, int tier, int cap) {
        HunterArchetypeConfig cfg = HunterArchetypeConfig.getInstance();
        List<HunterArchetypeAsset> eligible = new ArrayList<>();
        for (HunterArchetypeAsset a : cfg.getArchetypes().values()) {
            if (a.spawnTier() <= tier && a.roleName() != null && !a.roleName().isBlank()) {
                eligible.add(a);
            }
        }
        List<HunterArchetypeAsset> plan = new ArrayList<>();
        if (eligible.isEmpty()) {
            return plan;
        }
        // Deterministic per-round RNG (off the world seed) so a given round is reproducible.
        java.util.Random rng = new java.util.Random(round.worldSeed() ^ 0x4B57_4545L);

        // Primary archetype (rule-set hunterArchetype), if it is eligible at this tier.
        HunterArchetypeAsset primary = cfg.byId(round.ruleSet().hunterArchetype());
        if (primary != null && primary.spawnTier() <= tier
                && primary.roleName() != null && !primary.roleName().isBlank()) {
            addArchetype(plan, primary, cap);
        }

        int guard = 0;
        while (plan.size() < cap && guard++ < cap * 8) {
            HunterArchetypeAsset pick = weightedPick(eligible, rng);
            if (pick == null) {
                break;
            }
            addArchetype(plan, pick, cap);
        }
        return plan;
    }

    /** Add an archetype's {@code count()} copies to the plan, never exceeding {@code cap}. */
    private static void addArchetype(@Nonnull List<HunterArchetypeAsset> plan,
                                     @Nonnull HunterArchetypeAsset a, int cap) {
        int copies = Math.max(1, a.count());
        for (int i = 0; i < copies && plan.size() < cap; i++) {
            plan.add(a);
        }
    }

    /** Weighted pick over the eligible archetypes; {@code null} only if the list is empty. */
    @Nullable
    private static HunterArchetypeAsset weightedPick(@Nonnull List<HunterArchetypeAsset> eligible,
                                                     @Nonnull java.util.Random rng) {
        if (eligible.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (HunterArchetypeAsset a : eligible) {
            total += Math.max(0.0, a.weight());
        }
        if (total <= 0.0) {
            return eligible.get(rng.nextInt(eligible.size()));
        }
        double r = rng.nextDouble() * total;
        for (HunterArchetypeAsset a : eligible) {
            r -= Math.max(0.0, a.weight());
            if (r <= 0.0) {
                return a;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /** Current corruption tier (0/1/2), or 0 before chase state exists. */
    private static int currentTier(@Nonnull RoundInstance round) {
        ChaseState chase = round.chaseState();
        return chase == null ? 0 : chase.corruptionTier();
    }

    @Override
    public void tick(@Nonnull RoundInstance round, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        hunters.removeIf(u -> u.ref == null || !u.ref.isValid());
        // Corruption-tier escalation: as the round rots, a higher-tier archetype may join (hard-capped).
        maybeEscalate(round, world, store);
        // Speed ramp is independent of targeting and always runs (now per-hunter).
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
     * Corruption-tier escalation: when the tier has risen so that a higher-tier archetype is now
     * eligible and the roster is below its cap, spawn ONE extra archetype hunter (a weighted pick over
     * the newly-eligible archetypes). Hard-capped by {@link #hunterCap} (itself bounded by
     * {@link #MAX_HUNTERS}) and rate-limited to one add per tick, so escalation can never runaway-spawn.
     */
    private void maybeEscalate(@Nonnull RoundInstance round, @Nonnull World world,
                               @Nonnull Store<EntityStore> store) {
        if (hunters.size() >= hunterCap) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        int tier = currentTier(round);
        // The minimum spawnTier already present; only escalate to something STRICTLY scarier than that.
        int spawnedMaxTier = -1;
        for (HunterUnit u : hunters) {
            spawnedMaxTier = Math.max(spawnedMaxTier, u.archetype.spawnTier());
        }
        List<HunterArchetypeAsset> newlyEligible = new ArrayList<>();
        for (HunterArchetypeAsset a : HunterArchetypeConfig.getInstance().getArchetypes().values()) {
            if (a.spawnTier() <= tier && a.spawnTier() > spawnedMaxTier
                    && a.roleName() != null && !a.roleName().isBlank()) {
                newlyEligible.add(a);
            }
        }
        if (newlyEligible.isEmpty()) {
            return;
        }
        java.util.Random rng = new java.util.Random(
                round.worldSeed() ^ (0x5CA1_E000L + hunters.size()));
        HunterArchetypeAsset pick = weightedPick(newlyEligible, rng);
        if (pick == null) {
            return;
        }
        Anchor den = ArenaLayout.HUNTER_DEN;
        int denZ = (int) Math.floor(den.z());
        int before = hunters.size();
        spawnArchetypeAt(npc, store, world, pick, pick.roleName(), hunters.size(), hunterCap, den, denZ);
        if (hunters.size() > before) {
            SafeLog.info("[Kweebec] corruption escalation spawned a '" + pick.getId()
                    + "' hunter (tier=" + tier + ", now " + hunters.size() + "/" + hunterCap + ")");
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
        for (HunterUnit u : hunters) {
            if (u.ref != null && u.ref.isValid()) {
                try {
                    store.removeEntity(u.ref, RemoveReason.REMOVE);
                } catch (Throwable ignored) {
                    // best effort
                }
            }
        }
        hunters.clear();
        rosterPlan.clear();
        hunterCap = 1;
        currentTaunt = null;
        alertTarget = null;
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
        for (HunterUnit u : hunters) {
            Vector3d from = positionOf(store, u.ref);
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

    // --- cross-owner contract: live hunter positions for the ScareDirector ---

    @Override
    @Nonnull
    public List<Vector3d> hunterPositions(@Nonnull Store<EntityStore> store) {
        List<Vector3d> out = new ArrayList<>(hunters.size());
        for (HunterUnit u : hunters) {
            Vector3d p = positionOf(store, u.ref);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    // --- corruption-scaled speed ramp (now PER-HUNTER, per-archetype bands) ---

    /**
     * Snap {@link ChaseState#hunterSpeed} to a band on EACH hunter's OWN archetype ladder and, when that
     * hunter's band changes, swap its "HunterPace" EntityEffect. Best-effort: a missing effect asset (or
     * a hunter without an {@code EffectControllerComponent}) just leaves that hunter at the role baseline.
     */
    private void applySpeed(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        ChaseState chase = round.chaseState();
        if (chase == null) {
            return;
        }
        double mult = chase.hunterSpeed(round.ruleSet());
        var assetMap = EntityEffect.getAssetMap();
        for (HunterUnit u : hunters) {
            if (u.ref == null || !u.ref.isValid()) {
                continue;
            }
            double[] bands = bandsFor(u.archetype);
            String[] effectIds = effectIdsFor(u.archetype);
            int band = nearestBandIndex(bands, mult);
            if (band == u.appliedBand) {
                continue; // unchanged for this hunter - the common case; no per-tick effect churn
            }
            String newId = band < effectIds.length ? effectIds[band] : null;
            int newEffectIndex = (newId == null || newId.isBlank())
                    ? Integer.MIN_VALUE : assetMap.getIndex(newId);
            EntityEffect newEffect = newEffectIndex == Integer.MIN_VALUE
                    ? null : assetMap.getAsset(newEffectIndex);
            if (newId != null && !newId.isBlank() && newEffect == null) {
                SafeLog.fine("[Kweebec] hunter pace effect '" + newId
                        + "' not registered; '" + u.archetype.getId() + "' stays at role baseline.");
            }
            EffectControllerComponent effects =
                    store.getComponent(u.ref, EffectControllerComponent.getComponentType());
            if (effects == null) {
                // No controller: still record the band so we do not churn the lookup every tick.
                u.appliedBand = band;
                u.appliedEffectIndex = Integer.MIN_VALUE;
                continue;
            }
            try {
                if (u.appliedEffectIndex != Integer.MIN_VALUE) {
                    effects.removeEffect(u.ref, u.appliedEffectIndex, store);
                }
                if (newEffect != null) {
                    effects.addEffect(u.ref, newEffect, store);
                }
            } catch (Throwable t) {
                SafeLog.fine("[Kweebec] hunter pace swap failed ('" + u.archetype.getId()
                        + "'): " + t.getMessage());
            }
            u.appliedBand = band;
            u.appliedEffectIndex = newEffect == null ? Integer.MIN_VALUE : newEffectIndex;
        }
    }

    /** This archetype's speed bands, falling back to the historical ladder if it authored none. */
    @Nonnull
    private static double[] bandsFor(@Nonnull HunterArchetypeAsset archetype) {
        double[] bands = archetype.speedBands();
        return (bands != null && bands.length > 0) ? bands : FALLBACK_SPEED_BANDS;
    }

    /** This archetype's pace-effect ids (parallel to its bands), falling back to the historical ids. */
    @Nonnull
    private static String[] effectIdsFor(@Nonnull HunterArchetypeAsset archetype) {
        String[] ids = archetype.bandEffectIds();
        return (ids != null && ids.length > 0) ? ids : FALLBACK_BAND_EFFECT_IDS;
    }

    /** Index of the {@code bands} entry nearest {@code mult} (ties pick the earlier/slower band). */
    private static int nearestBandIndex(@Nonnull double[] bands, double mult) {
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < bands.length; i++) {
            double diff = Math.abs(bands[i] - mult);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }
}
