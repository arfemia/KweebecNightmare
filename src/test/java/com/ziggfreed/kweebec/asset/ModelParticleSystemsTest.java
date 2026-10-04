package com.ziggfreed.kweebec.asset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.common.util.StringUtil;

/**
 * The particle systems this mod's models wear are this mod's own. The engine checks a model's
 * {@code Particles[].SystemId} against the loaded particle systems when the model decodes; one that names
 * a missing system fails the model, then every role whose appearance it is, and the engine's asset
 * validation then ends the boot. Update 7 renamed the base game's {@code Spectre_Void_Hands}, which the
 * corrupted hunters and the Warden wore, and Kweebec 1.2.0 stopped a 0.7.0 server that way. So every
 * system a model names ships in this jar under {@code Server/Particles/} (matched exactly: the shipped
 * models spell each id as its file does), and each shipped system is one the engine accepts: at least one
 * spawner, each with an id, under a system id in the engine's key format (every {@code _}-separated word
 * capitalized, or the store warns that the key "has incorrect format").
 */
class ModelParticleSystemsTest {

    private static final Path SERVER = Path.of("src", "main", "resources", "Server");
    private static final Path MODELS = SERVER.resolve("Models");
    private static final Path PARTICLES = SERVER.resolve("Particles");
    private static final String SYSTEM_SUFFIX = ".particlesystem";

    @Test
    void everyParticleSystemAModelNamesShipsInThisJar() throws IOException {
        Map<String, Path> shipped = shippedSystems();
        List<String> foreign = new ArrayList<>();
        int named = 0;
        for (Path model : files(MODELS, ".json")) {
            JsonObject root = read(model);
            if (!root.has("Particles")) {
                continue;
            }
            for (JsonElement particle : root.getAsJsonArray("Particles")) {
                String systemId = particle.getAsJsonObject().get("SystemId").getAsString();
                named++;
                if (!shipped.containsKey(systemId)) {
                    foreign.add(MODELS.relativize(model) + " names '" + systemId + "'");
                }
            }
        }
        assertTrue(named > 0, "no model names a particle system, so the scan is looking in the wrong place");
        assertTrue(foreign.isEmpty(), "a model names a particle system this jar does not ship; a game update that "
                + "renames it fails the model, its roles and the boot: " + foreign);
    }

    @Test
    void everyShippedParticleSystemIsOneTheEngineAccepts() throws IOException {
        Map<String, Path> shipped = shippedSystems();
        assertFalse(shipped.isEmpty(), "this jar ships no particle system under " + PARTICLES);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Path> system : shipped.entrySet()) {
            String id = system.getKey();
            if (!StringUtil.isCapitalized(id, '_')) {
                problems.add(id + ": not in the engine's key format (each '_'-separated word capitalized)");
            }
            JsonObject root = read(system.getValue());
            JsonArray spawners = root.has("Spawners") ? root.getAsJsonArray("Spawners") : new JsonArray();
            if (spawners.size() == 0) {
                problems.add(id + ": no Spawners, which the engine refuses");
            }
            for (JsonElement spawner : spawners) {
                JsonElement spawnerId = spawner.getAsJsonObject().get("SpawnerId");
                if (spawnerId == null || spawnerId.getAsString().isBlank()) {
                    problems.add(id + ": a Spawners entry names no SpawnerId");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }

    /** Every particle system this jar ships, by id: its file name without the extension. */
    @Nonnull
    private static Map<String, Path> shippedSystems() throws IOException {
        Map<String, Path> out = new TreeMap<>();
        if (!Files.isDirectory(PARTICLES)) {
            return out;
        }
        for (Path file : files(PARTICLES, SYSTEM_SUFFIX)) {
            String name = file.getFileName().toString();
            out.put(name.substring(0, name.length() - SYSTEM_SUFFIX.length()), file);
        }
        return out;
    }

    @Nonnull
    private static List<Path> files(@Nonnull Path root, @Nonnull String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(f -> f.toString().endsWith(suffix)).sorted().toList();
        }
    }

    @Nonnull
    private static JsonObject read(@Nonnull Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
