package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;

/**
 * Every instance template names its discovery title's look the way Update 7 reads it. Update 7's
 * instance discovery takes {@code "Style"}, one of the engine's event-title styles spelled exactly as
 * the engine names them: the codec matches the name case-sensitively, and a name it does not know fails
 * the whole template, so the instance cannot spawn and the round never starts. It still reads
 * {@code "Major"}, but warns that the field is deprecated each time an instance loads, which is every
 * round. So no template here carries {@code Major}, and every {@code Style} is a name the engine knows.
 * Which style a template picks is content and is not checked here; the fixture test pins the checker.
 */
class InstanceDiscoveryStyleTest {

    private static final Path INSTANCES = Path.of("src", "main", "resources", "Server", "Instances");

    /**
     * The names the engine's own {@code Style} codec accepts, spelled exactly as it matches them. The
     * engine's {@code ProtocolCodecs.EVENT_TITLE_STYLE_CODEC} is {@code new EnumCodec<>(EventTitleStyle.class)}
     * with documentation added, and an {@code EnumCodec}'s keys come from its enum alone, so this codec
     * holds the same keys. It is built here because {@code ProtocolCodecs}' class init builds validators
     * that need the server's log manager, which a test JVM does not have.
     */
    private static final List<String> STYLE_NAMES =
            Arrays.asList(new EnumCodec<>(EventTitleStyle.class).getEnumKeys());

    @Test
    void everyInstanceNamesItsDiscoveryStyleTheWayUpdate7ReadsIt() throws IOException {
        List<String> findings = new ArrayList<>();
        int discoveries = 0;
        for (Path file : instanceTemplates()) {
            JsonObject discovery = discoveryOf(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
            if (discovery == null) {
                continue;
            }
            discoveries++;
            for (String problem : problems(discovery)) {
                findings.add(INSTANCES.relativize(file) + ": " + problem);
            }
        }
        assertTrue(discoveries > 0, "no instance template under " + INSTANCES
                + " carries a Plugin.Instance.Discovery block, so the scan is looking in the wrong place");
        assertTrue(findings.isEmpty(), "An instance's Discovery must read cleanly on Update 7:\n"
                + String.join("\n", findings));
    }

    @Test
    void theDeprecatedFlagAndAStyleTheEngineCannotReadAreCaught() {
        assertEquals(List.of(deprecatedMajor()), problems(object("{\"TitleKey\": \"t\", \"Major\": true}")));
        assertEquals(List.of(deprecatedMajor()),
                problems(object("{\"TitleKey\": \"t\", \"Major\": false, \"Style\": \"Default\"}")));
        assertEquals(List.of(unknownStyle("\"major\"")), problems(object("{\"TitleKey\": \"t\", \"Style\": \"major\"}")));
        assertEquals(List.of(unknownStyle("true")), problems(object("{\"TitleKey\": \"t\", \"Style\": true}")));
        assertEquals(List.of(), problems(object("{\"TitleKey\": \"t\", \"Style\": \"Major\"}")));
        assertEquals(List.of(), problems(object("{\"TitleKey\": \"t\", \"Style\": \"VoidEviction\"}")));
        assertEquals(List.of(), problems(object("{\"TitleKey\": \"t\"}")),
                "a Discovery with no Style takes the engine's Default style, which reads without a warning");
    }

    /** What a Discovery block gets wrong for Update 7, in a fixed order; empty when it reads cleanly. */
    @Nonnull
    static List<String> problems(@Nonnull JsonObject discovery) {
        List<String> out = new ArrayList<>();
        if (discovery.has("Major")) {
            out.add(deprecatedMajor());
        }
        JsonElement style = discovery.get("Style");
        if (style != null && !(style.isJsonPrimitive() && style.getAsJsonPrimitive().isString()
                && STYLE_NAMES.contains(style.getAsString()))) {
            out.add(unknownStyle(style.toString()));
        }
        return out;
    }

    @Nonnull
    static String deprecatedMajor() {
        return "authors \"Major\", which Update 7 reads with a deprecation warning each time the instance loads:"
                + " write \"Style\" instead";
    }

    @Nonnull
    static String unknownStyle(@Nonnull String shown) {
        return "names \"Style\": " + shown + ", which is not one of " + STYLE_NAMES
                + " (exact case): the template fails to load, so the round never starts";
    }

    /** {@code Plugin.Instance.Discovery}, or null when the template has none. */
    @Nullable
    static JsonObject discoveryOf(@Nonnull JsonElement root) {
        JsonElement at = root;
        for (String key : List.of("Plugin", "Instance", "Discovery")) {
            if (!at.isJsonObject() || !at.getAsJsonObject().has(key)) {
                return null;
            }
            at = at.getAsJsonObject().get(key);
        }
        return at.isJsonObject() ? at.getAsJsonObject() : null;
    }

    @Nonnull
    private static List<Path> instanceTemplates() throws IOException {
        try (Stream<Path> walk = Files.walk(INSTANCES)) {
            return walk.filter(f -> f.getFileName().toString().equals("instance.bson")).sorted().toList();
        }
    }

    @Nonnull
    private static JsonObject object(@Nonnull String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
