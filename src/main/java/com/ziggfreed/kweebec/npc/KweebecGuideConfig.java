package com.ziggfreed.kweebec.npc;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Settings for the auto-spawned "Grove Warden" guide NPC, the kweebec mirror of
 * MMO Skill Tree's {@code spawn-hub.json} ({@code SpawnHubConfig}). Lives at
 * {@code <data dir>/guide.json}; a slim Gson schema, unknown keys ignored so
 * future fields can be added without breaking existing files. Written with
 * defaults on first run.
 *
 * <p>
 * The auto-spawn (in {@link KweebecGuideSpawn}, on {@code PlayerReadyEvent}) is
 * restricted to the worlds named in {@code worlds}. The default
 * {@code ["default"]} keeps the guide in the main overworld only - NOT in
 * secondary worlds, round instances, or the creative hub (which also fire
 * {@code PlayerReadyEvent}). A single {@code "*"} entry spawns it in every
 * world; an empty list disables the auto-spawn entirely (place it yourself via
 * {@code /kweebec spawnguide}). World names match {@code World.getName()}.
 * {@code enabled=false} disables it outright.
 */
public final class KweebecGuideConfig {

    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static KweebecGuideConfig instance;

    public static synchronized KweebecGuideConfig getInstance() {
        if (instance == null) {
            instance = new KweebecGuideConfig();
        }
        return instance;
    }

    private KweebecGuideConfig() {
    }

    @Nullable
    private Path configPath;
    private volatile boolean enabled = true;
    private volatile String role = KweebecGuideSpawn.GUIDE_ROLE;
    private volatile List<String> worlds = new ArrayList<>(List.of("default"));
    private volatile double offsetX = 5.0;
    private volatile double offsetY = 0.0;
    private volatile double offsetZ = 0.0;
    private volatile float yaw = 90.0f;

    /**
     * Load {@code <dataDir>/guide.json} (writing defaults if absent). A null
     * dir keeps the defaults.
     */
    public void load(@Nullable Path dataDir) {
        if (dataDir == null) {
            SafeLog.warn("[Kweebec] guide config: no data dir, using defaults");
            return;
        }
        this.configPath = dataDir.resolve("guide.json");
        if (!Files.exists(configPath)) {
            writeDefaults();
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("enabled")) {
                enabled = root.get("enabled").getAsBoolean();
            }
            if (root.has("role")) {
                role = root.get("role").getAsString();
            }
            if (root.has("worlds") && root.get("worlds").isJsonArray()) {
                List<String> parsed = new ArrayList<>();
                for (JsonElement el : root.getAsJsonArray("worlds")) {
                    if (el.isJsonPrimitive()) {
                        parsed.add(el.getAsString());
                    }
                }
                worlds = parsed; // empty stays empty (= spawn nowhere), by design
            }
            if (root.has("offset") && root.get("offset").isJsonObject()) {
                JsonObject off = root.getAsJsonObject("offset");
                if (off.has("x")) {
                    offsetX = off.get("x").getAsDouble();
                }
                if (off.has("y")) {
                    offsetY = off.get("y").getAsDouble();
                }
                if (off.has("z")) {
                    offsetZ = off.get("z").getAsDouble();
                }
            }
            if (root.has("yaw")) {
                yaw = root.get("yaw").getAsFloat();
            }
            SafeLog.info("[Kweebec] guide config loaded (enabled=" + enabled + ", worlds=" + worlds + ")");
        } catch (IOException e) {
            SafeLog.warn("[Kweebec] guide config: failed to read " + configPath + ", using defaults: " + e.getMessage());
        } catch (Exception e) {
            SafeLog.warn("[Kweebec] guide config: malformed JSON in " + configPath + ", using defaults: " + e.getMessage());
        }
    }

    private void writeDefaults() {
        if (configPath == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException ignored) {
            // best-effort; the write below will surface a real failure
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("enabled", enabled);
        root.addProperty("role", role);
        JsonArray worldsArr = new JsonArray();
        for (String w : worlds) {
            worldsArr.add(w);
        }
        root.add("worlds", worldsArr);
        JsonObject off = new JsonObject();
        off.addProperty("x", offsetX);
        off.addProperty("y", offsetY);
        off.addProperty("z", offsetZ);
        root.add("offset", off);
        root.addProperty("yaw", yaw);
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            SafeLog.warn("[Kweebec] guide config: failed to write defaults to " + configPath + ": " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Nonnull
    public String getRole() {
        return role;
    }

    /**
     * The worlds the guide auto-spawns in (a single {@code "*"} = all worlds;
     * empty = none).
     */
    @Nonnull
    public List<String> getWorlds() {
        return Collections.unmodifiableList(worlds);
    }

    /**
     * Whether the guide may auto-spawn in {@code worldName} per the
     * {@code worlds} list.
     */
    public boolean shouldSpawnInWorld(@Nonnull String worldName) {
        return worlds.contains("*") || worlds.contains(worldName);
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public float getYaw() {
        return yaw;
    }
}
