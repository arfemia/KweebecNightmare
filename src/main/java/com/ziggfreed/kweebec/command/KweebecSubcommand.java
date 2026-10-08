package com.ziggfreed.kweebec.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;

/**
 * The {@code /kweebec} subcommands, each with every name it answers to, so an alias runs as its subcommand
 * and is gated with it. A plain enum beside the command class, so the gate is testable without a server.
 *
 * <p>An admin subcommand ({@code give}'s free Moonbloom, {@code endall} ending every round on the server,
 * {@code clashhost} placing the Duelmaster, {@code clash} and {@code domination} starting a match with every
 * eligible player in the caller's world) runs only for a sender with {@link #ADMIN_PERMISSION}, the node the
 * manifest declares; OP or the root {@code *} always passes, as in the family's other admin checks. An
 * operator has it by default ({@code /op} puts a player in {@code hytale:Admin}, whose built-in node is
 * {@code *}), and the console holds every node. The rest are open to every player, as the command is: a
 * player starts their own chase with {@code start} or the diegetic triggers.
 */
enum KweebecSubcommand {
    START(false, "start"),
    CLASH(true, "clash"),
    DOMINATION(true, "domination", "dom"),
    EXIT(false, "exit", "leave"),
    END_ALL(true, "endall", "end"),
    GIVE(true, "give"),
    SCORE(false, "score"),
    LEADERBOARD(false, "leaderboard", "lb"),
    PARTY(false, "party"),
    CLASH_HOST(true, "clashhost", "host");

    /** The node an admin subcommand needs: the manifest's {@code kweebecnightmare.admin}, default op. */
    static final String ADMIN_PERMISSION = "kweebecnightmare.admin";

    private static final Map<String, KweebecSubcommand> BY_NAME = new HashMap<>();

    static {
        for (KweebecSubcommand sub : values()) {
            for (String name : sub.names) {
                BY_NAME.put(name, sub);
            }
        }
    }

    private final boolean admin;
    private final List<String> names;

    KweebecSubcommand(boolean admin, @Nonnull String... names) {
        this.admin = admin;
        this.names = List.of(names);
    }

    /** The subcommand {@code name} (lower-cased) calls, or null when the command answers to no such name. */
    @Nullable
    static KweebecSubcommand byName(@Nonnull String name) {
        return BY_NAME.get(name);
    }

    /** Every name this subcommand answers to, its own first. */
    @Nonnull
    List<String> names() {
        return names;
    }

    /** Whether {@code sender} may run this subcommand: a player one asks nothing, an admin one asks for admin. */
    boolean mayRun(@Nonnull PermissionHolder sender) {
        return !admin || sender.hasPermission("OP") || sender.hasPermission("*")
                || sender.hasPermission(ADMIN_PERMISSION);
    }
}
