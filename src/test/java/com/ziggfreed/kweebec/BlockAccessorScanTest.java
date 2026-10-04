package com.ziggfreed.kweebec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

/**
 * This mod reaches blocks only through Ziggfreed Common's {@code BlockOps} (and the engine's static
 * {@code BlockOperations}, which {@code BlockOps} wraps), never through the 0.6.8 accessor family: the
 * block and chunk accessors {@code World} carries ({@code getBlockType} and its kin, from
 * {@code IChunkAccessorSync}, {@code ChunkAccessor} and {@code IWorldChunks}) and the {@code WorldChunk}
 * and {@code BlockChunk} types. Update 7 deletes those interfaces, so a jar built on 0.6.8 that calls one
 * throws {@code NoSuchMethodError} on a 0.7.0 server, where the caller's catch swallows it: the shrine's
 * lit state never showed there.
 *
 * <p>The rule reads names, not types, because a {@code World} can sit behind any variable name
 * ({@code w}, {@code thread}): a call or method reference by one of these names on any receiver but
 * {@code BlockOps} or {@code BlockOperations} fails, and so does any mention of the chunk types.
 * Comments, string literals and text blocks are not code. The fixture tests pin each rule, so the source
 * scan cannot pass by looking at nothing.
 */
class BlockAccessorScanTest {

    /** The main source roots, relative to the project directory Gradle runs tests in. */
    private static final List<Path> MAIN_ROOTS = List.of(
            Path.of("src", "main", "java"), Path.of("api", "src", "main", "java"));

    /** Every block or chunk accessor name 0.6.8's {@code World} carries; Update 7's {@code World} has none. */
    private static final String ACCESSOR_NAMES = "getBlock|getBlockType|getBaseBlock|setBlock|breakBlock"
            + "|setBlockInteractionState|getBlockComponentHolder|getBlockBulkRelative|getFluidId|performBlockUpdate"
            + "|getChunk|getChunkIfInMemory|getChunkIfLoaded|getChunkIfNonTicking|getNonTickingChunk"
            + "|loadChunkIfInMemory|getChunkAsync|getNonTickingChunkAsync";

    /** A call ({@code .name(}) or a method reference ({@code ::name}) by one of those names, on any receiver but the two allowed. */
    private static final Pattern ACCESSOR_CALL = Pattern.compile("(?<!\\bBlockOps)(?<!\\bBlockOperations)"
            + "(?:\\.\\s*(?:" + ACCESSOR_NAMES + ")\\s*\\(|::\\s*(?:" + ACCESSOR_NAMES + ")\\b)");

    /** The 0.6.8 chunk types and the accessor interfaces themselves. */
    private static final Pattern ACCESSOR_TYPE = Pattern.compile(
            "\\b(?:WorldChunk|BlockChunk|IChunkAccessorSync|ChunkAccessor|IWorldChunks|IWorldChunksAsync)\\b");

    @Test
    void theMainSourceReachesBlocksOnlyThroughBlockOps() throws IOException {
        List<String> findings = new ArrayList<>();
        List<Path> scanned = new ArrayList<>();
        for (Path root : MAIN_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(root)) {
                sources = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
            }
            for (Path source : sources) {
                scanned.add(source);
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int number : offendingLines(lines)) {
                    findings.add(source + ":" + number + ": " + lines.get(number - 1).trim());
                }
            }
        }
        assertTrue(scanned.stream().anyMatch(f -> f.endsWith(Path.of("mode", "chase", "ChaseMode.java"))),
                "the scan never reached ChaseMode.java, so it is looking in the wrong place");
        assertTrue(findings.isEmpty(), "Read and write blocks through Ziggfreed Common's BlockOps (blockItemIdAt, "
                + "setInteractionState, setBlock). World's block and chunk accessors, WorldChunk and BlockChunk are "
                + "gone on Update 7, where a jar built on 0.6.8 that calls one fails inside its catch:\n"
                + String.join("\n", findings));
    }

    @Test
    void everyOldReadOrWriteIsCaughtWhateverTheWorldIsCalled() {
        List<String> fixture = List.of(
                "BlockType bt = world.getBlockType(p.x(), p.y(), p.z());",
                "int id = w.getBlock(x, y, z);",
                "round.world().setBlock(x, y, z, \"Rock\");",
                "thread.breakBlock(x, y, z, 0);",
                "var chunk = instWorld.getChunkIfInMemory(index);",
                "instanceWorld.getNonTickingChunkAsync(index).thenAccept(c -> { });",
                "Function<Vector3i, BlockType> read = world::getBlockType;",
                "WorldChunk chunk = null;",
                "import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;");

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), offendingLines(fixture));
    }

    @Test
    void theBlockOpsRouteCommentsAndStringsPass() {
        List<String> fixture = List.of(
                "ChunkStore chunkStore = world.getChunkStore();",
                "String id = BlockOps.blockItemIdAt(chunkStore, x, y, z);",
                "BlockType bt = id != null ? BlockType.getAssetMap().getAsset(id) : null;",
                "boolean lit = BlockOps.setInteractionState(chunkStore, x, y, z, \"Lit\", false);",
                "BlockOps.setBlock(chunkStore, x, y, z, \"Rock\");",
                "BlockOperations.setBlockInteractionState(chunkStore, ref, x, y, z, bt, \"Lit\", false);",
                "Ref<ChunkStore> ref = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);",
                "// world.getBlockType(x, y, z) was the 0.6.8 read",
                "/** Never {@code World.getBlock}: Update 7 deletes it.",
                " *  Nor world.setBlock(x, y, z, id). */",
                "LOGGER.atFine().log(\"world.getBlockType( failed\");",
                "String doc = \"\"\"",
                "    world.getBlockType(x, y, z)",
                "    \"\"\";");

        assertEquals(List.of(), offendingLines(fixture));
    }

    /**
     * The 1-based numbers of the lines that reach blocks the old way. Comments, string and char literals
     * and text blocks are blanked first; a block comment and a text block carry across lines.
     */
    @Nonnull
    static List<Integer> offendingLines(@Nonnull List<String> lines) {
        List<Integer> out = new ArrayList<>();
        boolean inBlockComment = false;
        boolean inTextBlock = false;
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            StringBuilder code = new StringBuilder(line.length());
            int i = 0;
            while (i < line.length()) {
                if (inBlockComment) {
                    int end = line.indexOf("*/", i);
                    inBlockComment = end < 0;
                    i = end < 0 ? line.length() : end + 2;
                } else if (inTextBlock) {
                    int end = line.indexOf("\"\"\"", i);
                    inTextBlock = end < 0;
                    i = end < 0 ? line.length() : end + 3;
                } else if (line.startsWith("/*", i)) {
                    inBlockComment = true;
                    i += 2;
                } else if (line.startsWith("//", i)) {
                    i = line.length();
                } else if (line.startsWith("\"\"\"", i)) {
                    inTextBlock = true;
                    i += 3;
                } else if (line.charAt(i) == '"' || line.charAt(i) == '\'') {
                    i = endOfLiteral(line, i);
                    code.append(' ');
                } else {
                    code.append(line.charAt(i));
                    i++;
                }
            }
            String text = code.toString();
            if (ACCESSOR_CALL.matcher(text).find() || ACCESSOR_TYPE.matcher(text).find()) {
                out.add(n + 1);
            }
        }
        return out;
    }

    /** The index just past the string or char literal that opens at {@code start}, past any escape. */
    private static int endOfLiteral(@Nonnull String line, int start) {
        char quote = line.charAt(start);
        int i = start + 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return line.length();
    }
}
