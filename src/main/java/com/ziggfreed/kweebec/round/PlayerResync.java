package com.ziggfreed.kweebec.round;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Helpers that heal client/server position desync around teleports.
 *
 * <p>Two distinct problems, two helpers:
 *
 * <ul>
 *   <li>{@link #resync} - the engine never resyncs position on a game-mode change
 *       ({@code Player.setGameMode} only sends a {@code SetGameMode} packet), so flying in
 *       Creative then dropping to Adventure leaves the client drifted. Adding a {@link Teleport}
 *       to the CURRENT transform sends a real {@code ClientTeleport} the client snaps to and
 *       ACKS (the drift makes it a non-trivial move), clearing the resulting {@code PendingTeleport}.
 *   <li>{@link #clearPendingTeleport} - the inverse hazard. An OUTSTANDING {@link PendingTeleport}
 *       FREEZES the server-side position: {@code GamePacketHandler} drops every client movement
 *       packet until the client acks, while the client keeps moving by prediction (terrain renders,
 *       walking works) - so block interaction, which reads {@code TransformComponent.getPosition()},
 *       is locked to wherever the server is frozen. The cross-world instance exit does NOT clear a
 *       pending teleport (verified: {@code World} never touches it; only a valid ack removes it), so
 *       a same-world teleport queued just before the exit (the death respawn's teleport to the
 *       instance spawn) rides into the overworld and freezes the player there until relog. The fix
 *       is to REMOVE the stale component after the player lands, which lets the next movement packet
 *       resync the server to the client. NEVER add a fresh no-op teleport to "fix" this - the client
 *       will not ack a teleport to where it already is, leaving the pending teleport outstanding
 *       forever (that was a self-inflicted freeze on every exit type).
 * </ul>
 *
 * <p>THREADING: every method must run on the player's CURRENT world thread.
 */
public final class PlayerResync {

    private PlayerResync() {
    }

    /**
     * Snap the client to the server position (a real, ack-able teleport). Use ONLY where the
     * client is known to have drifted from the server (e.g. after a Creative flight), so the
     * teleport is a genuine move the client acknowledges. No-op if the ref/transform is
     * unavailable. Never throws.
     */
    public static void resync(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            if (!ref.isValid()) {
                return;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                return;
            }
            store.addComponent(ref, Teleport.getComponentType(),
                    Teleport.createForPlayer(tc.getTransform().clone()));
        } catch (Throwable ignored) {
            // best effort - a resync failure must never break the round flow
        }
    }

    /**
     * Remove any outstanding {@link PendingTeleport} so the server stops dropping the player's
     * movement packets (which otherwise freezes the server position and locks block interaction
     * to the landing area). Safe no-op when none is present; removing the component never
     * disconnects (only a mismatched client ACK does). Never throws.
     */
    public static void clearPendingTeleport(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        try {
            if (!ref.isValid()) {
                return;
            }
            if (store.getComponent(ref, PendingTeleport.getComponentType()) != null) {
                store.removeComponent(ref, PendingTeleport.getComponentType());
            }
        } catch (Throwable ignored) {
            // best effort
        }
    }
}
