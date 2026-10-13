package com.ziggfreed.kweebec.command;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.ziggfreed.kweebec.i18n.Lang;

/**
 * The {@code /kweebec} subcommands, each with every name it answers to, so an alias runs as its subcommand
 * and is gated with it. A plain enum beside the command class, so the gate is testable without a server.
 * {@link KweebecCommand} registers one engine subcommand per value: its first name, the rest as aliases,
 * its description key, and, where it takes one, a usage variant with one positional argument
 * ({@code /kweebec start hardcore}, {@code /kweebec give 10}).
 *
 * <p>An admin subcommand ({@code give}'s free Moonbloom, {@code endall} ending every round on the server,
 * {@code clashhost} placing the Duelmaster, {@code clash} and {@code domination} starting a match with every
 * eligible player in the caller's world) runs only for a sender with {@link #ADMIN_PERMISSION}, the node the
 * manifest declares; OP or the root {@code *} always passes, as in the family's other admin checks. An
 * operator has it by default ({@code /op} puts a player in {@code hytale:Admin}, whose built-in node is
 * {@code *}), and the console holds every node. The engine gates an admin subcommand on that node before it
 * runs or lists it ({@link KweebecCommand}), and {@link #mayRun} checks again. The rest are open to every
 * player, as the command is: a player starts their own chase with {@code start} or the diegetic triggers.
 */
enum KweebecSubcommand {
    START(false, Lang.CMD_START_DESC, "preset", Lang.ARG_PRESET_DESC, "start"),
    CLASH(true, Lang.CMD_CLASH_DESC, "preset", Lang.ARG_CLASH_PRESET_DESC, "clash"),
    DOMINATION(true, Lang.CMD_DOMINATION_DESC, "preset", Lang.ARG_DOMINATION_PRESET_DESC, "domination", "dom"),
    EXIT(false, Lang.CMD_EXIT_DESC, null, null, "exit", "leave"),
    END_ALL(true, Lang.CMD_END_ALL_DESC, null, null, "endall", "end"),
    GIVE(true, Lang.CMD_GIVE_DESC, "count", Lang.ARG_COUNT_DESC, "give"),
    SCORE(false, Lang.CMD_SCORE_DESC, null, null, "score"),
    LEADERBOARD(false, Lang.CMD_LEADERBOARD_DESC, null, null, "leaderboard", "lb"),
    PARTY(false, Lang.CMD_PARTY_DESC, null, null, "party"),
    CLASH_HOST(true, Lang.CMD_CLASH_HOST_DESC, null, null, "clashhost", "host");

    /** The node an admin subcommand needs: the manifest's {@code kweebecnightmare.admin}, default op. */
    static final String ADMIN_PERMISSION = "kweebecnightmare.admin";

    private final boolean admin;
    private final String descKey;
    @Nullable
    private final String argName;
    @Nullable
    private final String argDescKey;
    private final List<String> names;

    KweebecSubcommand(boolean admin, @Nonnull String descKey, @Nullable String argName,
                      @Nullable String argDescKey, @Nonnull String... names) {
        this.admin = admin;
        this.descKey = descKey;
        this.argName = argName;
        this.argDescKey = argDescKey;
        this.names = List.of(names);
    }

    /** Whether only an admin may run this subcommand ({@link #ADMIN_PERMISSION}, an operator or the console). */
    boolean admin() {
        return admin;
    }

    /** Every name this subcommand answers to, its own first. */
    @Nonnull
    List<String> names() {
        return names;
    }

    /** The lang key of this subcommand's description, which the engine's help lists. */
    @Nonnull
    String descKey() {
        return descKey;
    }

    /** The name of the one positional argument this subcommand takes, or null when it takes none. */
    @Nullable
    String argName() {
        return argName;
    }

    /** The lang key describing {@link #argName()}, or null when the subcommand takes no argument. */
    @Nullable
    String argDescKey() {
        return argDescKey;
    }

    /** Whether {@code sender} may run this subcommand: a player one asks nothing, an admin one asks for admin. */
    boolean mayRun(@Nonnull PermissionHolder sender) {
        return !admin || sender.hasPermission("OP") || sender.hasPermission("*")
                || sender.hasPermission(ADMIN_PERMISSION);
    }
}
