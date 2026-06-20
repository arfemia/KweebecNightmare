package com.ziggfreed.kweebec.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.narwhals.perfectutils.api.StunMobAPI;
import com.ziggfreed.common.instance.effect.EntityEffectService;
import com.ziggfreed.common.sound.Sound3D;
import com.ziggfreed.common.util.EntityIdentifierUtil;
import com.ziggfreed.kweebec.hunter.OnHitConfig;
import com.ziggfreed.kweebec.mode.clash.ClashState;
import com.ziggfreed.kweebec.moonbloom.GlowThrowables;
import com.ziggfreed.kweebec.round.KweebecMode;
import com.ziggfreed.kweebec.round.PlayerRoundState;
import com.ziggfreed.kweebec.round.RoundInstance;
import com.ziggfreed.kweebec.round.RoundService;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * The mod's single damage observer (a {@link DamageEventSystem} in the filter group,
 * so it sees ALL server damage). It NEVER initiates damage; it does two things:
 *
 * <ol>
 *   <li><b>Throw-to-stun (world-agnostic, Moonbloom-only).</b> A thrown Moonbloom's burst
 *       deals damage tagged with a custom {@link DamageCause} {@value #MOONBLOOM_CAUSE}
 *       (authored at {@code Server/Entity/Damage/KweebecNightmare_Moonbloom.json}), so a
 *       Moonbloom hit is identified by {@code damage.getDamageCauseIndex()} ALONE -
 *       independent of the damage source and the world. When such damage lands on a
 *       NON-player entity ANYWHERE (base world OR a round), it freezes that mob via
 *       Perfect Utils {@code StunMobAPI} + the vanilla {@code Stun} visual. Only a
 *       Moonbloom stuns (a melee/other hit on the hunter does nothing); players caught in
 *       the splash are skipped (no teammate friendly-stun). The thrower (the burst
 *       damage's owning entity) is credited {@code mobsStunned} when they are in a round.
 *       An {@code isStunned} guard makes it fire ONCE per stun window.</li>
 *   <li><b>Damage-taken scoring.</b> Real damage to an in-round survivor is accumulated
 *       onto their {@link PlayerRoundState} (a friendly Moonbloom splash is not counted).</li>
 * </ol>
 *
 * <p>Runs on the world thread; the {@link PlayerRoundState} mutations honor the
 * world-thread contract and the {@code StunMobAPI} enqueue is its own thread-safe queue.
 * Whole body is try-guarded.
 */
public final class KweebecDamageSystem extends DamageEventSystem {

    /** Custom damage cause the Moonbloom burst deals, used to identify a Moonbloom hit (authored in the pack). */
    private static final String MOONBLOOM_CAUSE = "KweebecNightmare_Moonbloom";
    /** Vanilla entity effect carrying the "stunned" particle + tint (the Perfect Utils freeze is particle-less). */
    private static final String STUN_VISUAL_EFFECT = "Stun";
    /** Stun length for a Moonbloom hit thrown OUTSIDE a round (no rule-set to read). */
    private static final long DEFAULT_STUN_MS = 2500L;

    /** Model-asset-id prefix shared by every hunter appearance (KweebecNightmare_HunterCorrupted), used to ID a hunter melee hit. */
    private static final String HUNTER_MODEL_PREFIX = "KweebecNightmare";
    /** Chance a hunter SNICKERS when it lands a hit on a survivor (the on-hit sibling of the proximity snicker in ScareDirector). */
    private static final double SNICKER_ON_HIT_CHANCE = 0.25;
    /** Vanilla eerie SFX used for the snicker (no custom audio); retune here. */
    private static final String SNICKER_SOUND_ID = "SFX_Emit_Temple_Wisps";

    /** Resolved index of the Moonbloom damage cause; cached ONLY once positive (the asset loads after this class). */
    private volatile int moonbloomCauseIndex = -1;
    /** Resolved index of the Emberbloom damage cause; cached ONLY once positive (used for the friendly-fire guard). */
    private volatile int emberCauseIndex = -1;

    /** Minimum gap (ms) between re-applying the on-hit slow to the SAME victim (so rapid hits do not thrash it). */
    private static final long SLOW_REAPPLY_COOLDOWN_MS = 500L;
    /**
     * Per-victim on-hit punishment state, STATIC so {@code ChaseMode.lightShrine} can clear it without
     * holding a reference to the single registered observer instance. Keyed by victim UUID; written on the
     * world thread but {@link ConcurrentHashMap} for the cross-call clear. {@code lastSlowAppliedMs} is the
     * slow re-apply cooldown gate; {@code proximityStack} is the 1..cap stack level; {@code lastHitMs} is
     * the stack-window anchor (the time of the last hunter hit on that victim).
     */
    private static final Map<UUID, Long> lastSlowAppliedMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> proximityStack = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastHitMs = new ConcurrentHashMap<>();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        try {
            if (damage.isCancelled()) {
                return;
            }
            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
            if (targetRef == null || !targetRef.isValid()) {
                return;
            }
            boolean isMoonbloom = isMoonbloomCause(damage);
            PlayerRef targetPlayer = store.getComponent(targetRef, PlayerRef.getComponentType());

            if (targetPlayer == null) {
                // Non-player victim: ONLY a thrown Moonbloom stuns it (a melee / other hit does nothing).
                if (!isMoonbloom) {
                    return;
                }
                UUID thrower = throwerOf(store, damage);
                RoundInstance round = thrower != null
                        ? RoundService.getInstance().registry().forPlayer(thrower) : null;
                stunMob(store, targetRef, commandBuffer, round, thrower);
                return;
            }

            // Player victim: damage-taken scoring (in-round only); never stun a player, never count a splash.
            UUID uuid = targetPlayer.getUuid();
            if (uuid == null) {
                return;
            }
            RoundInstance round = RoundService.getInstance().registry().forPlayer(uuid);
            if (round == null) {
                return;
            }
            // PvP rounds (Clash / Domination): friendly-fire guard + enemy hit/stun tracking, then done
            // (they do not use the chase hunter on-hit / damage-taken-scoring path).
            if (round.mode() == KweebecMode.CLASH || round.mode() == KweebecMode.DOMINATION) {
                handleClashDamage(store, targetRef, commandBuffer, damage, uuid, round);
                return;
            }
            if (isMoonbloom) {
                return; // a friendly Moonbloom splash on a teammate - do not count it as damage taken
            }
            if (isEmberCause(damage)) {
                // An Emberbloom burst is the boss-damage throwable; it must NEVER hurt a fellow survivor
                // (the AoE has no asset-level attitude filter), so null the damage on a player victim.
                damage.setAmount(0f);
                return;
            }
            // HUNTER ON-HIT PUNISHMENT: when the attacker is a live hunter, scale its outgoing damage and
            // slap a proximity-escalating slow on the victim BEFORE scoring, so the score reflects the
            // punished amount. resolveOnHitConfigFor returns null for a non-hunter (or stale) attacker.
            Ref<EntityStore> hunterAttacker = hunterAttackerOf(store, damage);
            OnHitConfig cfg = hunterAttacker != null && round.hunterController() != null
                    ? round.hunterController().resolveOnHitConfigFor(hunterAttacker) : null;
            if (cfg != null) {
                applyOnHitPunishment(store, targetRef, commandBuffer, damage, uuid, cfg, hunterAttacker, round);
            }

            PlayerRoundState st = round.playerState(uuid);
            if (st != null && damage.getAmount() > 0f) {
                st.addDamageTaken(damage.getAmount());
            }
            // A hunter that lands a hit has a chance to snicker, private to the victim.
            maybeSnickerOnHit(store, targetRef, damage);
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] damage observe failed: " + t.getMessage());
        }
    }

    /**
     * When the attacker is a hunter (identified by its model asset id, read off the
     * damage source ref - the same self-contained technique the MMO uses for kill/XP
     * attribution), roll the on-hit snicker and play the eerie cue at the hunter's own
     * position, private to the victim. Best-effort: a non-hunter attacker / failed roll
     * / missing sound is a silent no-op. {@code victimRef} is the player who was hit.
     */
    private void maybeSnickerOnHit(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> victimRef,
                                   @Nonnull Damage damage) {
        if (ThreadLocalRandom.current().nextDouble() >= SNICKER_ON_HIT_CHANCE) {
            return;
        }
        Ref<EntityStore> attacker = hunterAttackerOf(store, damage);
        if (attacker == null) {
            return; // not one of our hunters
        }
        Sound3D.playAt(SNICKER_SOUND_ID, SoundCategory.SFX, attacker,
                Sound3D.onlyEntity(victimRef), store, "SNICKER_HIT", false);
    }

    /**
     * The hunter that dealt this damage, identified by its model asset id prefix (the same self-contained
     * technique the snicker uses), or {@code null} when the source is not an entity / not one of our
     * hunters. World-thread only.
     */
    @Nullable
    private Ref<EntityStore> hunterAttackerOf(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource es)) {
            return null;
        }
        Ref<EntityStore> attacker = es.getRef();
        if (attacker == null || !attacker.isValid()) {
            return null;
        }
        String mobId = EntityIdentifierUtil.getMobId(store, attacker);
        return (mobId != null && mobId.startsWith(HUNTER_MODEL_PREFIX)) ? attacker : null;
    }

    /**
     * Apply the hunter on-hit punishment to a survivor victim, BEFORE the hit is scored:
     *
     * <ol>
     *   <li><b>Bonus damage</b>: scale the hit by {@code cfg.damageMult() + cfg.damageFlat()} (the enrage
     *       bonus is already folded into {@code damageMult()} when the hunter is enraged).</li>
     *   <li><b>Proximity stacking</b>: escalate the victim's stack level while repeated hits land within
     *       {@code cfg.stackWindowSeconds()} (capped at {@code cfg.stackCap()}); a hit after the window
     *       lapses resets to tier 1. The slow effect id is the base id with the stack tier suffix
     *       ({@code KweebecNightmare_HunterSlow_<tier>}).</li>
     *   <li><b>Slow</b>: apply that tiered slow for {@code cfg.slowSeconds()} (OVERWRITE), gated by a
     *       per-victim {@value #SLOW_REAPPLY_COOLDOWN_MS} ms cooldown so rapid hits do not thrash it.</li>
     *   <li><b>Enrage idle reset</b>: tell the controller this hunter connected, so its desperation timer
     *       restarts.</li>
     * </ol>
     *
     * World-thread only; best-effort (a missing effect asset degrades to no slow).
     */
    private void applyOnHitPunishment(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> victimRef,
                                      @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Damage damage,
                                      @Nonnull UUID victimId, @Nonnull OnHitConfig cfg,
                                      @Nonnull Ref<EntityStore> hunterAttacker, @Nonnull RoundInstance round) {
        long now = System.currentTimeMillis();

        // 1) Bonus damage (mult already carries the enrage bonus when this hunter is enraged).
        float base = damage.getAmount();
        if (base > 0f) {
            double scaled = base * cfg.damageMult() + cfg.damageFlat();
            damage.setAmount((float) Math.max(0.0, scaled));
        }

        // 2) Proximity stacking: escalate within the window, reset if the window lapsed.
        int cap = Math.max(1, cfg.stackCap());
        long windowMs = (long) (Math.max(0.0, cfg.stackWindowSeconds()) * 1000.0);
        Long prevHit = lastHitMs.get(victimId);
        int tier;
        if (prevHit != null && windowMs > 0L && (now - prevHit) <= windowMs) {
            tier = Math.min(cap, proximityStack.getOrDefault(victimId, 0) + 1);
        } else {
            tier = 1; // first hit, or the window lapsed -> reset to tier 1
        }
        proximityStack.put(victimId, tier);
        lastHitMs.put(victimId, now);

        // 3) Tiered slow, gated by the per-victim re-apply cooldown.
        String baseId = cfg.slowEffectId();
        if (baseId != null && !baseId.isBlank()) {
            Long lastApplied = lastSlowAppliedMs.get(victimId);
            if (lastApplied == null || (now - lastApplied) >= SLOW_REAPPLY_COOLDOWN_MS) {
                String tieredId = tieredSlowId(baseId, tier);
                boolean applied = EntityEffectService.applyTimed(victimRef, tieredId,
                        (float) Math.max(0.0, cfg.slowSeconds()), OverlapBehavior.OVERWRITE, cb);
                if (applied) {
                    lastSlowAppliedMs.put(victimId, now);
                }
            }
        }

        // 4) Reset this hunter's desperation idle timer (it connected).
        if (round.hunterController() != null) {
            round.hunterController().noteHunterLandedHit(hunterAttacker, now);
        }
    }

    /**
     * The stack-tiered slow effect id for a base id and a tier. Tier 1 -> the base id as-authored
     * ({@code KweebecNightmare_HunterSlow_1}); higher tiers swap the trailing {@code _N} suffix for the
     * tier ({@code _2/_3/_4}). A base id with no numeric suffix appends {@code _<tier>}. The pack ships
     * {@code KweebecNightmare_HunterSlow_1..4}.
     */
    @Nonnull
    private static String tieredSlowId(@Nonnull String baseId, int tier) {
        int us = baseId.lastIndexOf('_');
        if (us > 0 && us < baseId.length() - 1 && isAllDigits(baseId.substring(us + 1))) {
            return baseId.substring(0, us + 1) + tier;
        }
        return baseId + "_" + tier;
    }

    private static boolean isAllDigits(@Nonnull String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    /**
     * Clear a victim's proximity stack (called when a shrine is lit - the party catches its breath). The
     * stack and its window anchor reset so the next hit starts fresh at tier 1. Thread-safe; safe to call
     * for a player with no live stack.
     */
    public static void clearProximityStack(@Nonnull UUID victimId) {
        proximityStack.remove(victimId);
        lastHitMs.remove(victimId);
        lastSlowAppliedMs.remove(victimId);
    }

    /** Clear every victim's proximity stack (a shrine light relieves the whole party). Thread-safe. */
    public static void clearAllProximityStacks() {
        proximityStack.clear();
        lastHitMs.clear();
        lastSlowAppliedMs.clear();
    }

    /** Whether this damage carries the custom Moonbloom cause (resolved + cached lazily). */
    private boolean isMoonbloomCause(@Nonnull Damage damage) {
        int idx = moonbloomCauseIndex;
        if (idx < 0) {
            try {
                idx = DamageCause.getAssetMap().getIndex(MOONBLOOM_CAUSE);
            } catch (Throwable ignored) {
                idx = -1;
            }
            if (idx >= 0) {
                moonbloomCauseIndex = idx; // cache only a real index (the asset registers after this class)
            }
        }
        return idx >= 0 && damage.getDamageCauseIndex() == idx;
    }

    /** Whether this damage carries the custom Emberbloom cause (resolved + cached lazily, like Moonbloom). */
    private boolean isEmberCause(@Nonnull Damage damage) {
        int idx = emberCauseIndex;
        if (idx < 0) {
            try {
                idx = DamageCause.getAssetMap().getIndex(GlowThrowables.EMBER_DAMAGE_CAUSE);
            } catch (Throwable ignored) {
                idx = -1;
            }
            if (idx >= 0) {
                emberCauseIndex = idx; // cache only a real index (the asset registers after this class)
            }
        }
        return idx >= 0 && damage.getDamageCauseIndex() == idx;
    }

    /**
     * PvP (Clash / Domination) damage handling for a player victim: friendly-fire cancel (same team +
     * FriendlyFire off), enemy hit + last-attacker recording (kill credit on death), and a Moonbloom hit
     * stunning an enemy player (zeroing its damage, since it is the stun utility). The attacker is the
     * damage source's owning entity = the melee striker OR the thrower of a mushroom (the source is always
     * the shooter, never the projectile). Unresolved-team damage is allowed (fail-safe). World thread.
     */
    private void handleClashDamage(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> victimRef,
                                   @Nonnull CommandBuffer<EntityStore> cb, @Nonnull Damage damage,
                                   @Nonnull UUID victimId, @Nonnull RoundInstance round) {
        // Emberbloom is a co-op boss throwable; it has no place hurting a PvP player - null it defensively.
        if (isEmberCause(damage)) {
            damage.setAmount(0f);
            return;
        }
        UUID attacker = throwerOf(store, damage); // EntitySource player uuid (striker or thrower)
        if (attacker == null) {
            return; // environmental / unattributable - let it apply (e.g. a fall, a pit)
        }
        int victimTeam = round.teamOf(victimId);
        int attackerTeam = round.teamOf(attacker);
        boolean sameTeam = attackerTeam >= 0 && attackerTeam == victimTeam;
        if (sameTeam && !round.ruleSet().friendlyFire()) {
            damage.setCancelled(true); // cancels the hit AND any asset knockback (e.g. a Gustbloom)
            return;
        }
        if (attacker.equals(victimId) || sameTeam) {
            return; // self or (friendly-fire-on) teammate hit: do not score it
        }
        // Enemy hit: record it (hits drive MOST_HITS) + remember the attacker for kill credit on death.
        // Only Clash tracks per-player hits/kills (Domination wins by zones); guard the state type.
        if (round.modeState() instanceof ClashState cs) {
            cs.recordHit(attacker);
            cs.setLastAttacker(victimId, attacker);
        }
        // A Moonbloom hit on an enemy player STUNS them; it is a utility, not a damage source here.
        if (isMoonbloomCause(damage)) {
            stunPlayer(store, victimRef, cb, round, attacker);
            damage.setAmount(0f);
        }
    }

    /** Stun an enemy PLAYER via Perfect Utils (the pipeline is entity-agnostic) + the vanilla Stun visual. */
    private void stunPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef,
                            @Nonnull CommandBuffer<EntityStore> cb, @Nonnull RoundInstance round,
                            @Nullable UUID attacker) {
        StunMobAPI api = StunMobAPI.get();
        if (api == null) {
            return;
        }
        try {
            if (api.isStunned(store, targetRef)) {
                return;
            }
        } catch (Throwable ignored) {
            // best effort
        }
        long stunMs = round.ruleSet().stunDurationMs();
        Ref<EntityStore> attackerRef = attacker != null ? playerRef(attacker) : null;
        api.stunEntity(store, targetRef, stunMs, attackerRef);
        applyStunVisual(targetRef, cb, stunMs);
    }

    /** The player who threw the Moonbloom: the burst damage's owning entity (the source). {@code null} if not a player. */
    @Nullable
    private UUID throwerOf(@Nonnull Store<EntityStore> store, @Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource es)) {
            return null;
        }
        Ref<EntityStore> ref = es.getRef();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        return pr == null ? null : pr.getUuid();
    }

    /** Freeze a mob once via Perfect Utils + the vanilla Stun visual; credit the thrower if they are in a round. */
    private void stunMob(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> mobRef,
                         @Nonnull CommandBuffer<EntityStore> cb, @Nullable RoundInstance round,
                         @Nullable UUID thrower) {
        StunMobAPI api = StunMobAPI.get();
        if (api != null && api.isStunned(store, mobRef)) {
            return; // already stunned - do not re-apply, re-visual, or re-credit
        }
        long stunMs = round != null ? round.ruleSet().stunDurationMs() : DEFAULT_STUN_MS;
        Ref<EntityStore> sourceRef = thrower != null ? playerRef(thrower) : null;
        if (api != null) {
            api.applyStun(store, mobRef, stunMs, sourceRef);
        }
        applyStunVisual(mobRef, cb, stunMs);
        if (round != null && thrower != null) {
            PlayerRoundState st = round.playerState(thrower);
            if (st != null) {
                st.incrementMobsStunned();
            }
        }
    }

    /** Apply the vanilla Stun entity effect (the Stunned stars + tint) so the freeze is visible. */
    private void applyStunVisual(@Nonnull Ref<EntityStore> mobRef, @Nonnull CommandBuffer<EntityStore> cb,
                                 long stunMs) {
        try {
            int idx = EntityEffect.getAssetMap().getIndex(STUN_VISUAL_EFFECT);
            if (idx == Integer.MIN_VALUE) {
                return;
            }
            EntityEffect fx = EntityEffect.getAssetMap().getAsset(idx);
            EffectControllerComponent ctrl = cb.getComponent(mobRef, EffectControllerComponent.getComponentType());
            if (fx != null && ctrl != null) {
                ctrl.addEffect(mobRef, fx, Math.max(0.5f, stunMs / 1000f), OverlapBehavior.OVERWRITE, cb);
            }
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] stun visual failed: " + t.getMessage());
        }
    }

    @Nullable
    private static Ref<EntityStore> playerRef(@Nonnull UUID uuid) {
        PlayerRef pr = Universe.get().getPlayer(uuid);
        return pr == null ? null : pr.getReference();
    }
}
