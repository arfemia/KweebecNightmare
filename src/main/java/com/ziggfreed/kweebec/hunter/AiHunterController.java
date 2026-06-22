package com.ziggfreed.kweebec.hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.narwhals.perfectutils.api.AggroAPI;
import com.ziggfreed.common.instance.effect.EntityEffectService;
import com.ziggfreed.common.instance.encounter.EncounterDirector;
import com.ziggfreed.common.instance.encounter.SpawnRoster;
import com.ziggfreed.common.instance.encounter.SpawnUnit;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.world.BlockTypeLists;
import com.ziggfreed.common.world.SpawnPlacement;
import com.ziggfreed.common.world.SurfaceProbe;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.arena.Anchor;
import com.ziggfreed.kweebec.arena.ArenaLayout;
import com.ziggfreed.kweebec.asset.HunterArchetypeAsset;
import com.ziggfreed.kweebec.asset.HunterArchetypeConfig;
import com.ziggfreed.common.instance.encounter.EncounterRuleAsset;
import com.ziggfreed.kweebec.integration.KweebecNightmareAPI;
import com.ziggfreed.kweebec.mode.chase.ChaseState;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RuleSet;
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
        /**
         * Wall-clock time this hunter last LANDED a hit on a survivor. Seeded to spawn time so the
         * desperation idle timer counts from when the hunter went live. The enrage trigger fires after
         * {@code enrageAfterSeconds} elapse from this without a connect. World-thread only.
         */
        long lastHitMs;
        /** Wall-clock time this hunter's current enrage ENDS; {@code 0} = not enraged. World-thread only. */
        long enrageUntilMs = 0L;
        /**
         * Wall-clock time before which this hunter may not re-enrage (so a single failed-to-connect hunter
         * does not enrage every tick). Set to the enrage END time so a fresh idle window must elapse after an
         * enrage expires. {@code 0} = no cooldown. World-thread only.
         */
        long enrageCooldownUntilMs = 0L;

        HunterUnit(@Nonnull Ref<EntityStore> ref, @Nonnull HunterArchetypeAsset archetype, long spawnedAtMs) {
            this.ref = ref;
            this.archetype = archetype;
            this.lastHitMs = spawnedAtMs;
        }
    }

    /**
     * Absolute hard backstop on total live hunters, so a misconfigured preset / runtime override
     * can never runaway-spawn. The REAL per-round ceiling is difficulty-driven
     * ({@code RuleSet.maxHunters()} + party scaling, computed into {@link #maxLiveHunters} each
     * round); this only caps that.
     */
    private static final int MAX_HUNTERS = 200;
    /**
     * Blocks within the gate corridor a survivor must be for a PLAYER_PROXIMITY spawn rule to fire.
     * STABLE engine constant (arena gate geometry), NOT an author-tunable difficulty knob; if a future
     * variant needs a different gate trigger radius, promote it to a {@code RuleSet} field rather than
     * editing this constant.
     */
    private static final double GATE_NEAR_RADIUS = 16.0;
    /**
     * Total hunters cannot exceed {@code base hunterCount + this * (partySize - 1)} (modest party scaling).
     * STABLE engine constant (the party-scaling budget), NOT an author-tunable difficulty knob; promote to a
     * {@code RuleSet} field (alongside {@code hunterCount}) if a future variant needs different party scaling.
     */
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

    /**
     * Engine {@code BlockTypeList} ids whose blocks are SURFACE DECORATION the worldgen scatters ON TOP of
     * the terrain (dead trees + ground scatter), passed to the foliage-skipping {@link SpawnPlacement}
     * overloads so a runtime extra-hunter spawn floor-snaps to the genuine GROUND under the canopy instead
     * of onto a trunk/branch/leaf block. Asset-driven (resolved via {@link BlockTypeLists#keys(String...)}),
     * so new tree/scatter blocks are skipped automatically. Mirrors {@code ArenaBuilder}'s own list.
     */
    private static final String[] SURFACE_DECORATION_LISTS = {"TreeWoodAndLeaves", "AllScatter"};

    /** Live roster of spawned hunters, each with its own archetype + band state. World-thread only. */
    private final List<HunterUnit> hunters = new ArrayList<>();
    /**
     * Mid-round wave bookkeeping for the asset-driven spawn rules (cooldown + max-per-round gating), keyed
     * per rule id. Reset at {@code despawnAll}/{@code spawn}. World-thread only (the round tick owns it).
     */
    private final EncounterDirector encounterDirector = new EncounterDirector();
    /** The archetypes that spawned this round (for mid-round corruption escalation). World-thread only. */
    private final List<HunterArchetypeAsset> rosterPlan = new ArrayList<>();
    /** Ceiling on the INITIAL roster size for this round (computed in {@code spawn} from party size). */
    private int hunterCap = 1;
    /**
     * The per-round LIVE hunter ceiling: {@code RuleSet.maxHunters()} + per-extra-player scaling,
     * clamped to {@link #MAX_HUNTERS}. Escalation + extra-spawn waves fill up toward this (NOT the
     * smaller initial {@link #hunterCap}). Computed in {@code spawn}.
     */
    private int maxLiveHunters = MAX_HUNTERS;
    /**
     * The rule-set of the round this controller serves, cached on {@code spawn} so the on-hit config seam
     * ({@link #resolveOnHitConfigFor}) can fold archetype overrides over the baseline without a
     * {@code RoundInstance} field. A controller serves exactly one round for its lifetime. World-thread only.
     */
    @Nullable
    private RuleSet activeRuleSet;
    /** The survivor currently taunted (so we clear the old one and re-issue only on a change). World-thread only. */
    @Nullable
    private UUID currentTaunt;
    @Nullable
    private volatile UUID alertTarget;
    /** Logged once if Perfect Utils is unavailable, so we do not spam the degrade notice. */
    private boolean warnedNoAggro;

    /**
     * Fallback speed ladder when an archetype authored none (mirrors the historical hardcoded ladder).
     * Two distinct concepts, kept as parallel arrays by index:
     * <ul>
     *   <li>{@link #FALLBACK_SPEED_BANDS} = the SPEED MULTIPLIERS (0.9x, 1.0x, ..., 1.5x) the controller
     *       picks a tier from;</li>
     *   <li>{@link #FALLBACK_BAND_EFFECT_IDS} = the PACE EFFECTS (the {@code EntityEffect} ids that VISUALIZE
     *       each multiplier); an empty / {@code null} entry is the 1.0x role baseline (no effect applied).</li>
     * </ul>
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

    /**
     * The pace EntityEffect applied as the enrage SPEED bump (the fastest authored pace, 1.5x). The enrage
     * is delivered as a TIMED effect over the per-hunter band swap (OVERWRITE, expires on its own), so it
     * supersedes the band's pace for the enrage window and {@code applySpeed} re-asserts the corruption band
     * once it lapses (its {@code appliedBand == band} guard is invalidated below so it re-applies). The
     * archetype's authored {@code EnrageSpeedMult} drives the DAMAGE/balance intent; the visible speed step
     * uses this top pace rung so no extra asset is needed.
     */
    private static final String ENRAGE_PACE_EFFECT_ID = "KweebecNightmare_HunterPace_150";

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

        this.activeRuleSet = round.ruleSet();
        // Fresh per-round wave bookkeeping (cooldowns + max-per-round fire counts) for the spawn rules.
        encounterDirector.reset();
        int partySize = Math.max(1, round.partySize());
        int extraForParty = EXTRA_PER_EXTRA_PLAYER * Math.max(0, partySize - 1);
        // The per-round LIVE ceiling is difficulty-driven (RuleSet.maxHunters()) plus per-extra-player
        // scaling, clamped to the absolute backstop. Waves + escalation fill toward this.
        this.maxLiveHunters = Math.min(MAX_HUNTERS,
                Math.max(1, round.ruleSet().maxHunters()) + extraForParty);
        // The INITIAL roster stays driven by hunterCount (small); never above the live ceiling.
        int desired = Math.max(1, round.ruleSet().hunterCount()) + extraForParty;
        this.hunterCap = Math.min(this.maxLiveHunters, desired);

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
        double offset = (index - (total - 1) / 2.0) * 2.0;
        double hx = den.x() + offset;
        // Floor-snap the den to the rolling grove surface (the flat disc is gone) so the hunter
        // spawns ON the ground, never buried in a hill or floating over a valley. World thread
        // (spawn runs in the round tick), so the column is queryable; degrade to the authored stand Y.
        int standY = SurfaceProbe.standableY(world, (int) Math.floor(hx), denZ, (int) ArenaLayout.STAND_Y);
        Vector3d pos = new Vector3d(hx, standY, den.z());
        spawnArchetypeAtPos(npc, store, bandSource, roleName, pos, den.yaw());
    }

    /**
     * Spawn one hunter at an explicit (already floor-snapped) world {@code pos}, tracking it as a
     * {@link HunterUnit} that paces on {@code bandSource}'s ladder. This is the generalized seam the
     * extra-spawn rules use to place a reinforcement NEAR the survivors (via {@link SpawnPlacement}),
     * while the den-based {@code spawnArchetypeAt} delegates here. Best-effort: an unregistered role or a
     * spawn failure is logged and skipped, never thrown into the round loop. World-thread only.
     */
    private void spawnArchetypeAtPos(@Nonnull NPCPlugin npc, @Nonnull Store<EntityStore> store,
                                     @Nonnull HunterArchetypeAsset bandSource, @Nullable String roleName,
                                     @Nonnull Vector3d pos, float yaw) {
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
        Rotation3f rot = new Rotation3f(0f, yaw, 0f);
        try {
            // No spawn-time target lock: the first tick's taunt directs the hunter (the aggro
            // system carries targeting; there is no marked-target seam to seed anymore).
            var spawned = npc.spawnEntity(store, roleIndex, pos, rot, null,
                    (npcEntity, npcRef, st) -> { });
            if (spawned != null) {
                hunters.add(new HunterUnit(spawned.first(), bandSource, System.currentTimeMillis()));
            }
        } catch (Throwable t) {
            SafeLog.warn("[Kweebec] hunter spawn failed (archetype '" + bandSource.getId()
                    + "', role '" + roleName + "'): " + t.getMessage());
        }
    }

    /**
     * Plan a roster of up to {@code cap} archetypes eligible at the given corruption {@code tier}: the
     * rule-set's primary archetype first (when set + eligible), then the shared, deterministic
     * {@link SpawnRoster} fills the rest by weighted pick (honoring each archetype's {@code count()}).
     * Reads only {@link HunterArchetypeConfig} - no hardcoded ids; an empty result means the caller
     * falls back. Determinism: the roster is seeded off the round world seed XOR a per-call salt.
     */
    @Nonnull
    private List<HunterArchetypeAsset> planRoster(@Nonnull RoundInstance round, int tier, int cap) {
        List<HunterArchetypeAsset> plan = new ArrayList<>();
        if (cap <= 0) {
            return plan;
        }
        HunterArchetypeConfig cfg = HunterArchetypeConfig.getInstance();

        // Primary archetype (rule-set hunterArchetype) first, if it is eligible at this tier.
        HunterArchetypeAsset primary = cfg.byId(round.ruleSet().hunterArchetype());
        if (primary != null && primary.spawnTier() <= tier
                && primary.roleName() != null && !primary.roleName().isBlank()) {
            addArchetype(plan, primary, cap);
        }

        // The shared roster fills the rest by deterministic weighted pick over the eligible archetypes.
        SpawnRoster<HunterArchetypeAsset> roster = buildRoster();
        long seed = round.worldSeed() ^ 0x4B57_4545L;
        for (HunterArchetypeAsset a : roster.planRoster(tier, cap, seed)) {
            if (plan.size() >= cap) {
                break;
            }
            plan.add(a);
        }
        return plan;
    }

    /**
     * Build a {@link SpawnRoster} over the SPAWNABLE archetypes (those with a non-blank role). Each
     * archetype becomes a {@link SpawnUnit} carrying its {@code weight()}/{@code spawnTier()}/{@code count()}
     * so the shared planner reproduces the historical eligibility + weighting. Reads only
     * {@link HunterArchetypeConfig}; never null (an empty roster yields an empty plan).
     */
    @Nonnull
    private static SpawnRoster<HunterArchetypeAsset> buildRoster() {
        List<SpawnUnit<HunterArchetypeAsset>> units = new ArrayList<>();
        for (HunterArchetypeAsset a : HunterArchetypeConfig.getInstance().getArchetypes().values()) {
            if (a.roleName() != null && !a.roleName().isBlank()) {
                units.add(new SpawnUnit<>(a, a.weight(), a.spawnTier(), Math.max(1, a.count())));
            }
        }
        return new SpawnRoster<>(units);
    }

    /** Add an archetype's {@code count()} copies to the plan, never exceeding {@code cap}. */
    private static void addArchetype(@Nonnull List<HunterArchetypeAsset> plan,
                                     @Nonnull HunterArchetypeAsset a, int cap) {
        int copies = Math.max(1, a.count());
        for (int i = 0; i < copies && plan.size() < cap; i++) {
            plan.add(a);
        }
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
        // Asset-driven spawn rules supersede this legacy escalation; it runs ONLY as the zero-rules fallback
        // so a deployment without any SpawnRule asset keeps the historical mid-round escalation behavior.
        if (!hasSpawnRules()) {
            maybeEscalate(round, world, store);
        }
        // Speed ramp is independent of targeting and always runs (now per-hunter).
        applySpeed(round, store);
        // Desperation enrage: a hunter that has not connected in enrageAfterSeconds gets a speed+damage
        // burst (and a cue), so a juked hunter does not stay harmless. Always runs (independent of aggro).
        applyEnrage(round, store);
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
     * eligible and the roster is below the live ceiling, spawn ONE extra archetype hunter (a weighted
     * pick over the newly-eligible archetypes). Hard-capped by {@link #maxLiveHunters} (itself bounded
     * by {@link #MAX_HUNTERS}) and rate-limited to one add per tick, so escalation can never
     * runaway-spawn.
     */
    private void maybeEscalate(@Nonnull RoundInstance round, @Nonnull World world,
                               @Nonnull Store<EntityStore> store) {
        if (hunters.size() >= maxLiveHunters) {
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
        List<SpawnUnit<HunterArchetypeAsset>> newlyEligible = new ArrayList<>();
        for (HunterArchetypeAsset a : HunterArchetypeConfig.getInstance().getArchetypes().values()) {
            if (a.spawnTier() <= tier && a.spawnTier() > spawnedMaxTier
                    && a.roleName() != null && !a.roleName().isBlank()) {
                newlyEligible.add(new SpawnUnit<>(a, a.weight(), a.spawnTier(), Math.max(1, a.count())));
            }
        }
        if (newlyEligible.isEmpty()) {
            return;
        }
        // Deterministic weighted pick over the newly-eligible (strictly scarier) set via the shared roster.
        long seed = round.worldSeed() ^ (0x5CA1_E000L + hunters.size());
        HunterArchetypeAsset pick = new SpawnRoster<>(newlyEligible).weightedPick(tier, seed);
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

    // --- asset-driven extra-spawn rules (NEAR the survivors, on triggers) ---

    /** True when at least one extra-spawn rule is resolved (the runtime tier over the static fold). */
    private static boolean hasSpawnRules() {
        return !KweebecNightmareAPI.resolveSpawnRules().isEmpty();
    }

    @Override
    public void evaluateSpawnRules(@Nonnull RoundInstance round, @Nonnull World world,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull SpawnTrigger trigger, int tierOrSeconds) {
        List<EncounterRuleAsset> rules = KweebecNightmareAPI.resolveSpawnRules();
        if (rules.isEmpty()) {
            return;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return;
        }
        int tier = currentTier(round);
        long now = System.currentTimeMillis();
        for (EncounterRuleAsset rule : rules) {
            if (rule == null || SpawnTrigger.fromString(rule.trigger()) != trigger) {
                continue;
            }
            fireRuleIfReady(round, world, store, npc, rule, tier, tierOrSeconds, now);
        }
    }

    /**
     * Fire one rule if every gate passes: its corruption-tier floor, its per-trigger context match
     * (CORRUPTION_TIER's {@code AtTier}, TIME_ELAPSED's {@code AtSeconds}, PLAYER_PROXIMITY's gate-near
     * check), and the shared {@link EncounterDirector}'s cooldown + max-per-round gate. The wave size is
     * the rule's {@code Count} scaled by party size (Count per survivor), then clamped to the room under
     * the rule's cap and the per-round {@link #maxLiveHunters}, then each extra hunter
     * is placed NEAR the survivors per the rule's {@link SpawnPlacementKind}. World-thread only.
     */
    private void fireRuleIfReady(@Nonnull RoundInstance round, @Nonnull World world,
                                 @Nonnull Store<EntityStore> store, @Nonnull NPCPlugin npc,
                                 @Nonnull EncounterRuleAsset rule, int tier, int tierOrSeconds, long now) {
        if (tier < rule.minTier()) {
            return;
        }
        switch (SpawnTrigger.fromString(rule.trigger())) {
            case CORRUPTION_TIER -> {
                if (rule.atTier() > 0 && tierOrSeconds != rule.atTier()) {
                    return; // this rule only fires on a specific tier crossing
                }
            }
            case TIME_ELAPSED -> {
                if (tierOrSeconds < rule.atSeconds()) {
                    return; // the elapsed time has not reached this rule's threshold yet
                }
            }
            case PLAYER_PROXIMITY -> {
                if (!anySurvivorNearGate(round, store)) {
                    return; // no survivor is closing on the gate corridor
                }
            }
            default -> { /* ROUND_START / SHRINE_LIT fire unconditionally once gates below pass */ }
        }

        long cooldownMs = (long) (Math.max(0.0, rule.cooldownSeconds()) * 1000.0);
        if (!encounterDirector.canFire(rule.getId(), now, cooldownMs, rule.maxPerRound())) {
            return;
        }
        // Per-rule cap LOWERS the ceiling but never raises it above the per-round live ceiling.
        int ruleCap = rule.cap() > 0 ? Math.min(rule.cap(), maxLiveHunters) : maxLiveHunters;
        // Wave size scales with party size: each fire spawns the rule's Count PER survivor in the round.
        int requested = Math.max(1, rule.count()) * Math.max(1, round.partySize());
        int allowed = encounterDirector.allowedToSpawn(hunters.size(), ruleCap, requested);
        if (allowed <= 0) {
            return;
        }

        List<Vector3d> targets = placementTargets(round, world, store, rule, allowed, now);
        if (targets.isEmpty()) {
            return;
        }
        HunterArchetypeAsset primary = HunterArchetypeConfig.getInstance().byId(rule.unitId());
        int before = hunters.size();
        for (Vector3d pos : targets) {
            HunterArchetypeAsset a = pickRuleArchetype(round, rule, tier, primary, now);
            if (a == null || a.roleName() == null || a.roleName().isBlank()) {
                continue;
            }
            spawnArchetypeAtPos(npc, store, a, a.roleName(), pos, ArenaLayout.HUNTER_DEN.yaw());
        }
        if (hunters.size() > before) {
            encounterDirector.recordFire(rule.getId(), now);
            SafeLog.info("[Kweebec] spawn rule '" + rule.getId() + "' (" + rule.trigger()
                    + "/" + rule.placement() + ") spawned " + (hunters.size() - before)
                    + " hunter(s) (now " + hunters.size() + "/" + maxLiveHunters + ")");
        }
    }

    /**
     * Choose the archetype a rule's extra hunter spawns: the rule's authored {@code ArchetypeId} (when
     * set + role-bound + tier-eligible) WINS as the primary, else a deterministic weighted pick over the
     * tier-eligible roster. Determinism: seeded off the round world seed XOR the rule id + the fire clock,
     * so each fire varies while a given round is reproducible.
     */
    @Nullable
    private HunterArchetypeAsset pickRuleArchetype(@Nonnull RoundInstance round, @Nonnull EncounterRuleAsset rule,
                                                   int tier, @Nullable HunterArchetypeAsset primary, long now) {
        if (primary != null && primary.spawnTier() <= tier
                && primary.roleName() != null && !primary.roleName().isBlank()) {
            return primary;
        }
        long seed = round.worldSeed() ^ ((long) rule.getId().hashCode() << 16) ^ now;
        return buildRoster().weightedPick(tier, seed);
    }

    /**
     * Resolve {@code count} floor-snapped spawn positions for a rule, NEAR the survivors per its
     * {@link SpawnPlacementKind}: a ring band around one random active survivor, a surrounding ring
     * around the survivors' centroid, a scatter around the centroid, or the fixed den. All player-relative
     * placements use the shared {@link SpawnPlacement} (foliage-skipping so a spawn lands on the genuine
     * ground under the grove canopy) and are seeded off the round world seed XOR the rule id + the fire
     * clock for determinism. Empty when no active survivor exists. World-thread only.
     */
    @Nonnull
    private List<Vector3d> placementTargets(@Nonnull RoundInstance round, @Nonnull World world,
                                            @Nonnull Store<EntityStore> store, @Nonnull EncounterRuleAsset rule,
                                            int count, long now) {
        List<Vector3d> out = new ArrayList<>(count);
        long seed = round.worldSeed() ^ ((long) rule.getId().hashCode() << 8) ^ now;
        Set<String> skip = BlockTypeLists.keys(SURFACE_DECORATION_LISTS);
        int fallbackY = (int) ArenaLayout.STAND_Y;
        double radius = Math.max(2.0, rule.ringRadius());

        switch (SpawnPlacementKind.fromString(rule.placement())) {
            case DEN -> {
                Anchor den = ArenaLayout.HUNTER_DEN;
                int denZ = (int) Math.floor(den.z());
                for (int i = 0; i < count; i++) {
                    double offset = (i - (count - 1) / 2.0) * 2.0;
                    double hx = den.x() + offset;
                    int y = SurfaceProbe.standableY(world, (int) Math.floor(hx), denZ, fallbackY, skip);
                    out.add(new Vector3d(hx, y, den.z()));
                }
            }
            case NEAR_RANDOM_PLAYER -> {
                Vector3d player = randomActiveSurvivorPos(round, store, seed);
                if (player == null) {
                    return out;
                }
                // A ring band [radius*0.6, radius] around the chosen survivor; each point seeded distinctly.
                // The 0.6 (and the SCATTER 0.4 below) are FIXED ring-band geometry, NOT author-tunable
                // difficulty knobs: they shape the inner edge of the scatter pattern relative to the
                // rule-defined ringRadius. If a future pack needs per-rule placement geometry, add
                // minRadiusFraction()/maxRadiusFraction() to EncounterRuleAsset rather than editing these.
                double minR = radius * 0.6;
                for (int i = 0; i < count; i++) {
                    out.add(SpawnPlacement.nearPlayer(world, player.x(), player.z(),
                            minR, radius, seed + i * 0x9E3779B1L, fallbackY, skip));
                }
            }
            case RING_AROUND_PLAYERS -> {
                Vector3d c = survivorCentroid(round, store);
                if (c == null) {
                    return out;
                }
                out.addAll(SpawnPlacement.ringAround(world, c.x(), c.z(), radius, count, fallbackY, skip));
            }
            case SCATTER -> {
                Vector3d c = survivorCentroid(round, store);
                if (c == null) {
                    return out;
                }
                for (int i = 0; i < count; i++) {
                    out.add(SpawnPlacement.nearPlayer(world, c.x(), c.z(),
                            radius * 0.4, radius, seed + i * 0x85EBCA77L, fallbackY, skip));
                }
            }
            default -> { /* unreachable */ }
        }
        return out;
    }

    /** A deterministic random active survivor's position, or {@code null} if none are active/located. */
    @Nullable
    private Vector3d randomActiveSurvivorPos(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store,
                                            long seed) {
        List<Vector3d> positions = new ArrayList<>();
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Vector3d p = positionOf(store, survivorRef(st.playerId()));
            if (p != null) {
                positions.add(p);
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        int idx = (int) Math.floorMod(seed, positions.size());
        return positions.get(idx);
    }

    /** The XZ centroid (with a representative Y) of every active, located survivor, or {@code null} if none. */
    @Nullable
    private Vector3d survivorCentroid(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        int n = 0;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Vector3d p = positionOf(store, survivorRef(st.playerId()));
            if (p == null) {
                continue;
            }
            sx += p.x();
            sy += p.y();
            sz += p.z();
            n++;
        }
        if (n == 0) {
            return null;
        }
        return new Vector3d(sx / n, sy / n, sz / n);
    }

    /** True when any active survivor is within {@link #GATE_NEAR_RADIUS} of the gate corridor (XZ). */
    private boolean anySurvivorNearGate(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        Anchor gate = ArenaLayout.GATE;
        for (PlayerRoundState st : round.playerStates()) {
            if (!st.isActive()) {
                continue;
            }
            Vector3d p = positionOf(store, survivorRef(st.playerId()));
            if (p != null && gate.horizontalDistanceSq(p.x(), p.z()) <= GATE_NEAR_RADIUS * GATE_NEAR_RADIUS) {
                return true;
            }
        }
        return false;
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
        encounterDirector.reset();
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

    // --- desperation enrage (per-hunter idle -> speed+damage burst) ---

    /**
     * Per-tick desperation-enrage sweep: for each live hunter whose enrage is configured and whose idle
     * gap (now - {@code lastHitMs}) has exceeded its {@code enrageAfterSeconds} while off cooldown, START an
     * enrage - apply the top pace effect for the enrage window (the visible speed bump), set
     * {@code enrageUntilMs} so {@link #resolveOnHitConfigFor} folds the enrage damage multiplier in while
     * live, play the archetype's {@code EnrageSoundId} (if any) at the hunter, and arm the cooldown so it
     * cannot re-enrage until a fresh idle window elapses after this one ends. Best-effort, world-thread.
     */
    private void applyEnrage(@Nonnull RoundInstance round, @Nonnull Store<EntityStore> store) {
        if (hunters.isEmpty()) {
            return;
        }
        RuleSet rules = round.ruleSet();
        long now = System.currentTimeMillis();
        for (HunterUnit u : hunters) {
            if (u.ref == null || !u.ref.isValid()) {
                continue;
            }
            // Resolve this hunter's enrage knobs (archetype over the rule-set baseline). The enraged flag is
            // false here - we want the BASE config to read enrageAfter/Speed/Duration, not a doubled damage.
            OnHitConfig cfg = OnHitConfig.resolve(rules, u.archetype, false);
            double enrageAfterSec = cfg.enrageAfterSeconds();
            if (enrageAfterSec <= 0.0) {
                continue; // enrage disabled for this hunter
            }
            // While already enraged, just let it ride; clear the flag (re-assert band) once it lapses.
            if (u.enrageUntilMs != 0L) {
                if (now >= u.enrageUntilMs) {
                    u.enrageUntilMs = 0L;
                    // Force applySpeed to re-apply the corruption band next tick (the enrage pace overwrote it).
                    u.appliedBand = -1;
                }
                continue;
            }
            if (now < u.enrageCooldownUntilMs) {
                continue; // still cooling down from a prior enrage
            }
            long idleMs = now - u.lastHitMs;
            if (idleMs < (long) (enrageAfterSec * 1000.0)) {
                continue; // not idle long enough yet
            }
            startEnrage(u, cfg, now, store);
        }
    }

    /** Begin one hunter's enrage: timed speed effect + enrage window + cue + cooldown arm. World-thread. */
    private void startEnrage(@Nonnull HunterUnit u, @Nonnull OnHitConfig cfg, long now,
                             @Nonnull Store<EntityStore> store) {
        double durSec = Math.max(0.5, cfg.enrageDurationSeconds());
        u.enrageUntilMs = now + (long) (durSec * 1000.0);
        // Cooldown: a fresh full idle window must elapse AFTER the enrage ends before it can re-fire.
        u.enrageCooldownUntilMs = u.enrageUntilMs + (long) (cfg.enrageAfterSeconds() * 1000.0);
        // Visible speed bump: apply the top pace effect for the enrage window (OVERWRITE so it expires on
        // its own). We deliberately do NOT touch applySpeed's tracked band here - the band swap manages its
        // own effect index, and disturbing it mid-enrage would let the per-tick band swap rip the enrage
        // pace back off. Instead, applyEnrage invalidates the band ONCE the enrage lapses so applySpeed
        // re-asserts the corruption band cleanly on the next tick. Best-effort; missing id is a no-op.
        EntityEffectService.applyTimed(u.ref, ENRAGE_PACE_EFFECT_ID, (float) durSec,
                OverlapBehavior.OVERWRITE, store);
        // Optional enrage cue at the hunter (heard by everyone in range). Best-effort; missing id is a no-op.
        String soundId = cfg.enrageSoundId();
        if (soundId != null && !soundId.isBlank()) {
            Sound3D.playAt(soundId, SoundCategory.SFX, u.ref, store, "HUNTER_ENRAGE", false);
        }
        SafeLog.fine("[Kweebec] hunter '" + u.archetype.getId() + "' ENRAGED for "
                + durSec + "s (idle " + ((now - u.lastHitMs) / 1000L) + "s)");
    }

    // --- on-hit punishment contract (consumed by KweebecDamageSystem on the world thread) ---

    @Override
    @Nullable
    public OnHitConfig resolveOnHitConfigFor(@Nullable Ref<EntityStore> attacker) {
        HunterUnit u = findUnit(attacker);
        if (u == null) {
            return null; // not one of our live hunters
        }
        boolean enraged = u.enrageUntilMs != 0L && System.currentTimeMillis() < u.enrageUntilMs;
        return OnHitConfig.resolve(ruleSetOf(), u.archetype, enraged);
    }

    @Override
    public void noteHunterLandedHit(@Nullable Ref<EntityStore> attacker, long nowMs) {
        HunterUnit u = findUnit(attacker);
        if (u != null) {
            u.lastHitMs = nowMs;
        }
    }

    /** The live hunter matching {@code ref} (by ref identity), or {@code null}. World-thread only. */
    @Nullable
    private HunterUnit findUnit(@Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        for (HunterUnit u : hunters) {
            if (u.ref != null && u.ref.equals(ref)) {
                return u;
            }
        }
        return null;
    }

    /**
     * The rule-set of the round this controller serves, cached on {@link #spawn} ({@link #activeRuleSet}),
     * so the on-hit config seam folds archetype overrides over the baseline without retaining a
     * {@code RoundInstance}. A controller serves exactly one round for its lifetime.
     */
    @Nonnull
    private RuleSet ruleSetOf() {
        RuleSet rs = activeRuleSet;
        // Defensive: if the controller was somehow consulted before spawn(), fall back to a default baseline
        // so resolveOnHitConfigFor never NPEs (it will simply yield the zero-pack on-hit defaults).
        return rs != null ? rs : RuleSet.builder("nightmare").build();
    }
}
