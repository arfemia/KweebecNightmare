package com.ziggfreed.kweebec.npc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.ziggfreed.kweebec.util.SafeLog;

/**
 * Retires the two owner files the Grove Warden's own spawner kept, and tells the server owner
 * where the settings went.
 *
 * <p>A retired file is the one moment an owner's customization can vanish without anybody
 * noticing, so this never reads either file, never deletes either file, and says once at boot
 * what replaced them. Each is renamed in place to {@code <name>.legacy}, beside the folder the
 * owner already knows, so the values are there to copy across by hand; a name already taken is
 * left alone and a numbered suffix used instead, so a second boot cannot overwrite the first copy.
 *
 * <p>Nothing is carried across automatically. The two files describe a world list, an offset and
 * a yaw in a shape the placement asset does not share, and quietly guessing a placement out of
 * them would put the Warden somewhere the owner never asked for.
 *
 * <p>Pure {@code java.nio.file} I/O, so it is safe to call from plugin setup before the asset
 * pipeline exists. It never throws: an I/O failure logs and moves on, so a locked or read-only
 * file can never block a server from starting.
 */
public final class LegacyGuideFiles {

    /** The Grove Warden's old auto-spawn settings (world list, offset, yaw). */
    private static final String GUIDE_CONFIG = "guide.json";

    /** The old per-world "already placed" marker plus the recorded guide UUID. */
    private static final String GUIDE_MARKER = "guide-placements.json";

    private static final String LEGACY_SUFFIX = ".legacy";

    private LegacyGuideFiles() {
    }

    /**
     * Retire both files under {@code dataDir} and log ONE notice naming whichever were found. A
     * data dir with neither of them present is silent, so a fresh server says nothing.
     */
    public static void retire(@Nullable Path dataDir) {
        if (dataDir == null) {
            return;
        }
        List<String> retired = new ArrayList<>();
        for (String fileName : List.of(GUIDE_CONFIG, GUIDE_MARKER)) {
            String note = rename(dataDir, fileName);
            if (note != null) {
                retired.add(note);
            }
        }
        if (!retired.isEmpty()) {
            announce(retired);
        }
    }

    /** Rename one file in place, or null when it is absent or could not be moved. */
    @Nullable
    private static String rename(@Nonnull Path dataDir, @Nonnull String fileName) {
        Path source = dataDir.resolve(fileName);
        if (!Files.exists(source)) {
            return null;
        }
        try {
            Path dest = firstFree(dataDir, fileName);
            Files.move(source, dest);
            return fileName + " -> " + dest.getFileName();
        } catch (IOException e) {
            SafeLog.severe("[Kweebec] could not retire legacy file '" + source + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * {@code <name>.legacy}, then {@code <name>.legacy.2}, {@code .3}, ... until one is free, so a
     * re-created file on a later boot never overwrites the copy already kept.
     */
    @Nonnull
    private static Path firstFree(@Nonnull Path dataDir, @Nonnull String fileName) {
        Path candidate = dataDir.resolve(fileName + LEGACY_SUFFIX);
        int n = 2;
        while (Files.exists(candidate) && n < 1000) {
            candidate = dataDir.resolve(fileName + LEGACY_SUFFIX + "." + n);
            n++;
        }
        return candidate;
    }

    private static void announce(@Nonnull List<String> retired) {
        SafeLog.warn("===== The Grove Warden is placed by the NPC placement engine now =====\n"
                + "Retired (never read, never deleted): " + String.join(", ", retired) + "\n"
                + "Where the Warden stands is authored at "
                + "Server/ZiggfreedCommon/NpcPlacements/Kweebec_Grove_Warden.json, and the switch "
                + "to stop them appearing is mods/ziggfreedcommon/npc-placements.json.");
    }
}
