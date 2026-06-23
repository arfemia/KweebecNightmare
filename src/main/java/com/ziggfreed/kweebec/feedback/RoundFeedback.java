package com.ziggfreed.kweebec.feedback;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ziggfreed.common.feedback.EventTitles;
import com.ziggfreed.common.feedback.Notify;
import com.ziggfreed.common.ui.CustomHudHelper;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The single fan-out point for round feedback: event-title banners, danger/status
 * toasts, and custom-HUD install/strip. This is now the mod-SPECIFIC POLICY layer
 * (the {@code .lang} keying via {@link Lang} + the kept-HUD set) over the generic
 * ziggfreed-common engine wrappers - it delegates the actual engine plumbing to
 * {@code common.feedback.Notify} (toasts), {@code common.feedback.EventTitles} (banners),
 * and {@code common.ui.CustomHudHelper} (HUD install / strip / restore), which already
 * try-guard every channel so a missing asset never throws into the round loop. All calls
 * run on the instance world thread.
 */
public final class RoundFeedback {

    private RoundFeedback() {
    }

    // --- custom HUD lifecycle ---

    /**
     * Install the nightmare HUD and strip the native HUD down to the minigame kept set.
     * Returns the installed HUD (for later {@code pushState}) or null on failure.
     */
    @Nullable
    public static NightmareHud installHud(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return installCustomHud(store, ref, NightmareHud::new);
    }

    /**
     * Install ANY {@link CustomUIHud} for a player and strip the native HUD down to the
     * minigame kept set, the generic form of {@link #installHud} the PvP modes reuse (Clash
     * {@link ClashHud}, Domination {@link DominationHud}). The {@code factory} builds the HUD
     * from the resolved {@link PlayerRef} (the HUD constructors take only the ref). Returns the
     * installed HUD (for later {@code pushState}) or null on failure. World thread; the install
     * itself is try-guarded inside {@link CustomHudHelper#install} so a missing asset degrades
     * to a no-op.
     *
     * @param store   the entity store
     * @param ref     the player's entity ref
     * @param factory builds the concrete HUD from the player's {@link PlayerRef}
     * @param <T>     the concrete {@link CustomUIHud} subtype
     */
    @Nullable
    public static <T extends CustomUIHud> T installCustomHud(@Nonnull Store<EntityStore> store,
                                                             @Nonnull Ref<EntityStore> ref,
                                                             @Nonnull Function<PlayerRef, T> factory) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return null;
        }
        T customHud = factory.apply(playerRef);
        // The Kweebec kept-HUD set is byte-identical to common's minigame default, so we
        // delegate the register + native-HUD strip to CustomHudHelper.install(... defaultKeptHud()).
        boolean installed = CustomHudHelper.install(store, ref, customHud, CustomHudHelper.defaultKeptHud());
        return installed ? customHud : null;
    }

    /** Restore the default native HUD and drop the custom HUD (call on exit/round-end). */
    public static void restoreHud(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        CustomHudHelper.restore(store, ref);
    }

    // --- titles ---

    public static void title(@Nonnull PlayerRef playerRef, @Nonnull String primaryKey,
                             @Nonnull String secondaryKey, boolean major) {
        EventTitles.show(playerRef, Lang.msg(primaryKey), Lang.msg(secondaryKey), major);
    }

    // --- toasts ---

    public static void dangerToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        Notify.danger(playerRef, Lang.msg(key));
    }

    public static void successToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        Notify.success(playerRef, Lang.msg(key));
    }

    public static void warningToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        Notify.warning(playerRef, Lang.msg(key));
    }

    /** A warning toast carrying a PRE-BUILT (possibly parameterized) message. */
    public static void warningToast(@Nonnull PlayerRef playerRef, @Nonnull Message message) {
        Notify.warning(playerRef, message);
    }

    /** A neutral progress toast carrying a PRE-BUILT (possibly parameterized) message. */
    public static void infoToast(@Nonnull PlayerRef playerRef, @Nonnull Message message) {
        Notify.def(playerRef, message);
    }
}
