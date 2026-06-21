package com.ziggfreed.kweebec.feedback;

import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.ziggfreed.kweebec.KweebecNightmarePlugin;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The single fan-out point for round feedback: event-title banners, danger/status
 * toasts, and custom-HUD install/strip. Every channel is try-guarded so a missing
 * asset never throws into the round loop. All calls run on the instance world
 * thread (HUD + title + toast all write packets via the player's handler).
 */
public final class RoundFeedback {

    /** The minimal native HUD set kept visible during a round (plus our custom HUD). */
    private static final Set<HudComponent> KEPT_HUD = Set.of(
            HudComponent.Hotbar,
            HudComponent.Health,
            HudComponent.Mana,
            HudComponent.Stamina,
            HudComponent.Reticle,
            HudComponent.StatusIcons,
            HudComponent.Notifications,
            HudComponent.EventTitle);

    private RoundFeedback() {
    }

    // --- custom HUD lifecycle ---

    /**
     * Install the nightmare HUD and strip the native HUD down to {@link #KEPT_HUD}.
     * Returns the installed HUD (for later {@code pushState}) or null on failure.
     */
    @Nullable
    public static NightmareHud installHud(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return installCustomHud(store, ref, NightmareHud::new);
    }

    /**
     * Install ANY {@link CustomUIHud} for a player and strip the native HUD down to {@link #KEPT_HUD}, the
     * generic form of {@link #installHud} the PvP modes reuse (Clash {@link ClashHud}, Domination
     * {@link DominationHud}). The {@code factory} builds the HUD from the resolved {@link PlayerRef} (the
     * HUD constructors take only the ref). Returns the installed HUD (for later {@code pushState}) or null on
     * failure. World thread; every step try-guarded so a missing asset degrades to a no-op.
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
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return null;
            }
            HudManager hud = player.getHudManager();
            T customHud = factory.apply(playerRef);
            hud.addCustomHud(playerRef, customHud);
            hud.setVisibleHudComponents(playerRef, KEPT_HUD);
            return customHud;
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atWarning().log(
                    "[Kweebec] installCustomHud failed: " + t.getMessage());
            return null;
        }
    }

    /** Restore the default native HUD and drop the custom HUD (call on exit/round-end). */
    public static void restoreHud(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRef == null) {
                return;
            }
            player.getHudManager().resetHud(playerRef);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] restoreHud failed: " + t.getMessage());
        }
    }

    // --- titles ---

    public static void title(@Nonnull PlayerRef playerRef, @Nonnull String primaryKey,
                             @Nonnull String secondaryKey, boolean major) {
        try {
            EventTitleUtil.showEventTitleToPlayer(
                    playerRef, Lang.msg(primaryKey), Lang.msg(secondaryKey), major);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] title failed: " + t.getMessage());
        }
    }

    // --- toasts ---

    public static void dangerToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        toast(playerRef, Lang.msg(key), NotificationStyle.Danger);
    }

    public static void successToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        toast(playerRef, Lang.msg(key), NotificationStyle.Success);
    }

    public static void warningToast(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        toast(playerRef, Lang.msg(key), NotificationStyle.Warning);
    }

    /** A warning toast carrying a PRE-BUILT (possibly parameterized) message. */
    public static void warningToast(@Nonnull PlayerRef playerRef, @Nonnull Message message) {
        toast(playerRef, message, NotificationStyle.Warning);
    }

    /** A neutral progress toast carrying a PRE-BUILT (possibly parameterized) message. */
    public static void infoToast(@Nonnull PlayerRef playerRef, @Nonnull Message message) {
        toast(playerRef, message, NotificationStyle.Default);
    }

    public static void toast(@Nonnull PlayerRef playerRef, @Nonnull Message message, @Nonnull NotificationStyle style) {
        try {
            PacketHandler handler = playerRef.getPacketHandler();
            NotificationUtil.sendNotification(handler, message, style);
        } catch (Throwable t) {
            KweebecNightmarePlugin.LOGGER.atFine().log(
                    "[Kweebec] toast failed: " + t.getMessage());
        }
    }
}
