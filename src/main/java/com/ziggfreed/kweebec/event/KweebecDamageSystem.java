package com.ziggfreed.kweebec.event;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
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

    /** Resolved index of the Moonbloom damage cause; cached ONLY once positive (the asset loads after this class). */
    private volatile int moonbloomCauseIndex = -1;

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
            if (isMoonbloom) {
                return; // a friendly Moonbloom splash on a teammate - do not count it as damage taken
            }
            PlayerRoundState st = round.playerState(uuid);
            if (st != null && damage.getAmount() > 0f) {
                st.addDamageTaken(damage.getAmount());
            }
        } catch (Throwable t) {
            SafeLog.fine("[Kweebec] damage observe failed: " + t.getMessage());
        }
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
