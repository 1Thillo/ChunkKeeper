package de.thillo.chunkkeeper.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.thillo.chunkkeeper.KeptChunk;

class KeepChunksImporterTest {

    @TempDir
    Path plugins;

    @Test
    void nothingToImportWithoutDataFile() {
        assertTrue(new KeepChunksImporter(plugins.toFile()).read().isEmpty());
    }

    @Test
    void readsChunksAndReportsBrokenEntries() throws IOException {
        Path folder = Files.createDirectories(plugins.resolve("KeepChunks"));
        Files.writeString(folder.resolve("data.yml"), "chunks:\n- 10#10#world\n- -3#-4#world\n- kaputt\n");

        KeepChunksImporter.Result result = new KeepChunksImporter(plugins.toFile()).read().orElseThrow();
        assertEquals(List.of(new KeptChunk("world", 10, 10), new KeptChunk("world", -3, -4)), result.chunks());
        assertEquals(List.of("kaputt"), result.unreadable());
        assertEquals(Optional.empty(), result.releaseAllProtection());
    }

    @Test
    void readsReleaseAllProtection() throws IOException {
        Path folder = Files.createDirectories(plugins.resolve("KeepChunks"));
        Files.writeString(folder.resolve("data.yml"), "chunks: []\n");
        Files.writeString(folder.resolve("config.yml"), "general:\n  releaseallprotection: false\n");

        KeepChunksImporter.Result result = new KeepChunksImporter(plugins.toFile()).read().orElseThrow();
        assertTrue(result.chunks().isEmpty());
        assertEquals(Optional.of(false), result.releaseAllProtection());
    }
}
