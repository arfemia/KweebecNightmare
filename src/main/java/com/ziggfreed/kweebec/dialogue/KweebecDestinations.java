package com.ziggfreed.kweebec.dialogue;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.ziggfreed.common.instance.leaderboard.LeaderboardPage;
import com.ziggfreed.common.party.page.PartyInvitePage;
import com.ziggfreed.common.ui.route.Destination;
import com.ziggfreed.common.ui.route.DestinationContext;
import com.ziggfreed.common.ui.route.DestinationType;
import com.ziggfreed.common.ui.route.Destinations;
import com.ziggfreed.kweebec.experience.KweebecExperience;

/**
 * Kweebec's own two screens the retired {@code DialoguePageRouter} used to open by a bare routing
 * string ({@code "leaderboard"} / {@code "party"}): the round leaderboard and the party invite
 * screen. Each is now a registered, parameterless {@link Destination} type an option authors bare
 * ({@code "Open": "Kweebec_Leaderboard"}, {@code "Open": "Kweebec_Party"}), so the shared engine
 * opens it through {@link Destinations} with no router of kweebec's own in the path.
 *
 * <p><b>Registration timing (Pattern A), like every {@link Destinations} registration.</b> Content
 * is read once, right after every plugin's {@code setup()} returns, so {@link #register()} MUST be
 * called from {@link com.ziggfreed.kweebec.KweebecNightmarePlugin#setup()} before assets load - a
 * dialogue file naming either type before it is registered fails to load.
 */
public final class KweebecDestinations {

    /** The owner every registration here is attributed to. */
    private static final String OWNER = "kweebec";

    /** Bare-string {@code Open} target for the round leaderboard. */
    public static final String LEADERBOARD_TYPE = "Kweebec_Leaderboard";

    /** Bare-string {@code Open} target for the party invite screen. */
    public static final String PARTY_TYPE = "Kweebec_Party";

    private KweebecDestinations() {
    }

    /** Seed both types into the shared routing vocabulary. Called once from plugin {@code setup()}. */
    public static void register() {
        Destinations.register(OWNER, DestinationType.of(
                LEADERBOARD_TYPE, Leaderboard.class,
                BuilderCodec.builder(Leaderboard.class, Leaderboard::new).build(),
                KweebecDestinations::openLeaderboard));
        Destinations.register(OWNER, DestinationType.of(
                PARTY_TYPE, Party.class,
                BuilderCodec.builder(Party.class, Party::new).build(),
                KweebecDestinations::openParty));
    }

    /** Put the round leaderboard on screen, the same page the old router opened for {@code "leaderboard"}. */
    private static boolean openLeaderboard(@Nonnull Leaderboard destination, @Nonnull DestinationContext ctx) {
        PlayerRef playerRef = ctx.playerRef();
        if (playerRef == null) {
            return false;
        }
        ctx.player().getPageManager().openCustomPage(ctx.playerReference(), ctx.store(),
                new LeaderboardPage(playerRef, KweebecExperience.leaderboardDeps()));
        return true;
    }

    /** Put the party invite screen on screen, the same page the old router opened for {@code "party"}. */
    private static boolean openParty(@Nonnull Party destination, @Nonnull DestinationContext ctx) {
        PlayerRef playerRef = ctx.playerRef();
        if (playerRef == null) {
            return false;
        }
        ctx.player().getPageManager().openCustomPage(ctx.playerReference(), ctx.store(),
                new PartyInvitePage(playerRef, KweebecExperience.partyDeps()));
        return true;
    }

    /** A destination with no fields of its own: open the round leaderboard. */
    public static final class Leaderboard extends Destination {
    }

    /** A destination with no fields of its own: open the party invite screen. */
    public static final class Party extends Destination {
    }
}
