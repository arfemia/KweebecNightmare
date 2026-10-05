package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Every {@code Charge} body motion this mod's NPC roles author reads whole on Update 7. Update 7's
 * {@code Charge} has no {@code ChargeAcceleration}: it ramps a charge up and down over two distances in
 * blocks, {@code ChargeAccelerationDistance} and {@code ChargeDecelerationDistance}, each 3 when left
 * out. The server skips a key it does not read, with an "Unknown JSON attribute" warning on every load,
 * so a charge carrying the dropped key would quietly take the default ramp instead. Each charge here
 * names both distances itself and never the dropped key. The values are content and are not checked
 * here; the two fixture tests pin the checker, so the shipped scan cannot pass by looking at nothing.
 */
class ChargeMotionKeysTest {

    private static final Path ROLES = Path.of("src", "main", "resources", "Server", "NPC", "Roles");

    /** The 0.6.8 ramp key Update 7's {@code Charge} does not read. */
    private static final String DROPPED_KEY = "ChargeAcceleration";

    /** Update 7's two ramp distances, in blocks, in the order a finding names them. */
    private static final List<String> RAMP_KEYS = List.of("ChargeAccelerationDistance", "ChargeDecelerationDistance");

    @Test
    void everyChargeNamesUpdate7sRampAndNeverTheDroppedKey() throws IOException {
        List<String> findings = new ArrayList<>();
        int charges = 0;
        for (Path file : roleFiles()) {
            List<JsonObject> found = new ArrayList<>();
            collectCharges(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)), found);
            for (JsonObject charge : found) {
                charges++;
                for (String problem : problems(charge)) {
                    findings.add(ROLES.relativize(file) + ": " + problem);
                }
            }
        }
        assertTrue(charges > 0, "no role or template under " + ROLES
                + " authors a Charge body motion, so the scan is looking in the wrong place");
        assertTrue(findings.isEmpty(), "A Charge body motion must read whole on Update 7:\n"
                + String.join("\n", findings));
    }

    @Test
    void theDroppedKeyAndAMissingRampAreCaught() {
        assertEquals(List.of(droppedKey(), missingRamp("ChargeAccelerationDistance"),
                        missingRamp("ChargeDecelerationDistance")),
                problems(object("{\"Type\": \"Charge\", \"ChargeAbsoluteSpeed\": 16, \"ChargeAcceleration\": 1000}")));
        assertEquals(List.of(missingRamp("ChargeDecelerationDistance")),
                problems(object("{\"Type\": \"Charge\", \"ChargeAccelerationDistance\": 2}")));
        assertEquals(List.of(),
                problems(object("{\"Type\": \"Charge\", \"ChargeAccelerationDistance\": 0, \"ChargeDecelerationDistance\": 0}")));
    }

    @Test
    void chargesAreFoundAtAnyDepthButNeverInAChargeStateSensor() {
        List<JsonObject> found = new ArrayList<>();
        collectCharges(JsonParser.parseString("""
                {
                  "Instructions": [
                    { "Sensor": { "Type": "ChargeState", "States": [ "WindingUp" ] } },
                    { "Instructions": [ { "BodyMotion": { "Type": "Charge", "ChargeAbsoluteSpeed": 16 } } ] },
                    { "BodyMotion": { "Type": "Seek" } }
                  ]
                }
                """), found);
        assertEquals(1, found.size(), "exactly the one nested Charge body motion");
        assertEquals(16, found.get(0).get("ChargeAbsoluteSpeed").getAsInt());
    }

    /** What a Charge body motion gets wrong for Update 7, in a fixed order; empty when it reads whole. */
    @Nonnull
    static List<String> problems(@Nonnull JsonObject charge) {
        List<String> out = new ArrayList<>();
        if (charge.has(DROPPED_KEY)) {
            out.add(droppedKey());
        }
        for (String key : RAMP_KEYS) {
            if (!charge.has(key)) {
                out.add(missingRamp(key));
            }
        }
        return out;
    }

    @Nonnull
    static String droppedKey() {
        return "authors \"" + DROPPED_KEY + "\", which Update 7's Charge does not read (an \"Unknown JSON attribute\""
                + " warning on every load)";
    }

    @Nonnull
    static String missingRamp(@Nonnull String key) {
        return "names no \"" + key + "\", so Update 7 ramps that end of the charge over its 3-block default";
    }

    /** Every object whose {@code Type} is exactly {@code Charge}, at any depth. */
    static void collectCharges(@Nonnull JsonElement element, @Nonnull List<JsonObject> out) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement type = object.get("Type");
            if (type != null && type.isJsonPrimitive() && "Charge".equals(type.getAsString())) {
                out.add(object);
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectCharges(entry.getValue(), out);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectCharges(child, out);
            }
        }
    }

    @Nonnull
    private static List<Path> roleFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(ROLES)) {
            return walk.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Nonnull
    private static JsonObject object(@Nonnull String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
