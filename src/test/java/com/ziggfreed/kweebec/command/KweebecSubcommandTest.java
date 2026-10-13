package com.ziggfreed.kweebec.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

/**
 * Who may run each {@code /kweebec} subcommand (the maintainer's rulings, M381 and M408): {@code give},
 * {@code endall}, {@code clashhost}, {@code clash} and {@code domination}, with the aliases that follow them,
 * need the admin permission, which an operator holds by default; the rest, {@code start} among them, stay open
 * to every player and never ask. The command puts every call through
 * {@code mayRun} before it dispatches, so a refused call runs nothing. An operator is a sender holding the
 * engine's root node: {@code /op} puts a player in {@code hytale:Admin}, whose only built-in node it is, and
 * a holder of it has every node. The engine's own provider and resolver log through {@code HytaleLogger},
 * which cannot start in this test JVM, so the senders here are the test's own.
 */
class KweebecSubcommandTest {

    /** The node the manifest declares for administering the mod; server owners grant it by this name. */
    private static final String ADMIN_NODE = "kweebecnightmare.admin";

    /** The node the engine derives for {@code /kweebec} itself, which it opens to every player. */
    private static final String COMMAND_NODE = "ziggfreed.kweebecnightmare.command.kweebec";

    private static final List<String> ADMIN_NAMES = List.of(
            "give", "endall", "end", "clashhost", "host", "clash", "domination", "dom");

    private static final List<String> PLAYER_NAMES = List.of(
            "start", "exit", "leave", "score", "leaderboard", "lb", "party");

    /** The mod's one shipped lang file; the file name is the namespace the engine puts before every key. */
    private static final Path LANG = Path.of("src", "main", "resources", "Server", "Languages", "en-US",
            "kweebecnightmare.lang");

    @Test
    void everyAdminNameRefusesAPlayerWithoutTheAdminPermission() {
        PermissionHolder player = holding(Set.of(COMMAND_NODE));

        for (String name : ADMIN_NAMES) {
            assertFalse(sub(name).mayRun(player), () -> "/kweebec " + name + " refuses a player without " + ADMIN_NODE);
        }
    }

    @Test
    void everyAdminNameRunsForASenderGrantedTheAdminPermission() {
        PermissionHolder granted = holding(Set.of(COMMAND_NODE, ADMIN_NODE));

        for (String name : ADMIN_NAMES) {
            assertTrue(sub(name).mayRun(granted), () -> "/kweebec " + name + " runs for a sender granted " + ADMIN_NODE);
        }
    }

    @Test
    void anOperatorRunsEveryAdminNameByDefault() {
        PermissionHolder operator = holding(Set.of(PermissionsModule.ROOT));

        for (String name : ADMIN_NAMES) {
            assertTrue(sub(name).mayRun(operator), () -> "an operator runs /kweebec " + name + " with no grant of its own");
        }
    }

    @Test
    void theServerConsoleRunsEveryAdminName() {
        for (String name : ADMIN_NAMES) {
            assertTrue(sub(name).mayRun(ConsoleSender.INSTANCE), () -> "the server console runs /kweebec " + name);
        }
    }

    @Test
    void thePlayerSubcommandsNeverAskForAPermission() {
        for (String name : PLAYER_NAMES) {
            List<String> asked = new ArrayList<>();

            assertTrue(sub(name).mayRun(asking(asked)), () -> "/kweebec " + name + " stays open to every player");
            assertTrue(asked.isEmpty(), () -> "/kweebec " + name + " asked for " + asked);
        }
    }

    @Test
    void everyNameTheCommandAnswersToIsRuledOnOnce() {
        List<String> answered = new ArrayList<>();
        for (KweebecSubcommand sub : KweebecSubcommand.values()) {
            answered.addAll(sub.names());
        }
        List<String> ruled = new ArrayList<>(ADMIN_NAMES);
        ruled.addAll(PLAYER_NAMES);
        Collections.sort(answered);
        Collections.sort(ruled);

        assertEquals(ruled, answered, "a new subcommand or alias is ruled admin or player here, never left out");
    }

    /**
     * The command registers one engine subcommand per value, described by {@code descKey}, with a usage
     * variant whose argument {@code argDescKey} describes: the engine's help prints each as a lang key, so a
     * key with no en-US line shows raw. This mod ships en-US only.
     */
    @Test
    void everySubcommandDescriptionAndArgumentIsAShippedLangKey() throws IOException {
        Set<String> keys = enUsKeys();

        for (KweebecSubcommand sub : KweebecSubcommand.values()) {
            String name = sub.names().get(0);
            assertTrue(keys.contains(sub.descKey()), () -> "/kweebec " + name + "'s description " + sub.descKey()
                    + " has an en-US line");
            if (sub.argName() != null) {
                String argDescKey = sub.argDescKey();
                assertNotNull(argDescKey, () -> "/kweebec " + name + "'s argument " + sub.argName() + " is described");
                assertTrue(keys.contains(argDescKey), () -> "/kweebec " + name + "'s argument description " + argDescKey
                        + " has an en-US line");
            }
        }
    }

    /** Every key the en-US lang file defines, with the namespace its file name gives it. */
    private static Set<String> enUsKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(LANG, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                continue;
            }
            keys.add("kweebecnightmare." + trimmed.substring(0, eq).trim());
        }
        assertFalse(keys.isEmpty(), () -> "no keys read from " + LANG.toAbsolutePath());
        return keys;
    }

    /** The subcommand that answers to {@code name}, its own name or an alias. */
    private static KweebecSubcommand sub(String name) {
        KweebecSubcommand found = null;
        for (KweebecSubcommand sub : KweebecSubcommand.values()) {
            if (sub.names().contains(name)) {
                found = sub;
            }
        }
        assertNotNull(found, () -> "/kweebec answers to " + name);
        return found;
    }

    /** A sender holding {@code nodes}: one it holds says yes, and the root node says yes to every node. */
    private static PermissionHolder holding(Set<String> nodes) {
        return new PermissionHolder() {
            @Override
            public boolean hasPermission(@Nonnull String id) {
                return nodes.contains(id) || nodes.contains(PermissionsModule.ROOT);
            }

            @Override
            public boolean hasPermission(@Nonnull String id, boolean def) {
                return hasPermission(id) || def;
            }
        };
    }

    /** A sender holding nothing that writes down every node it is asked about. */
    private static PermissionHolder asking(List<String> asked) {
        return new PermissionHolder() {
            @Override
            public boolean hasPermission(@Nonnull String id) {
                asked.add(id);
                return false;
            }

            @Override
            public boolean hasPermission(@Nonnull String id, boolean def) {
                asked.add(id);
                return def;
            }
        };
    }
}
