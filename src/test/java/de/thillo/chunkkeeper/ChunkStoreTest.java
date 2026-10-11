package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkStoreTest {

    private static final Logger LOGGER = Logger.getAnonymousLogger();

    @TempDir
    Path folder;

    private ChunkStore newStore() {
        return new ChunkStore(new File(folder.toFile(), "chunks.yml"), LOGGER);
    }

    @Test
    void savedChunksComeBackAfterLoading() {
        ChunkStore store = newStore();
        store.add(new KeptChunk("world", 1, 2));
        store.add(new KeptChunk("world", -3, 4));
        store.add(new KeptChunk("world_nether", 0, 0));
        store.save();

        ChunkStore reloaded = newStore();
        reloaded.load();
        assertEquals(store.all(), reloaded.all());
    }

    @Test
    void addAndRemoveReportWhetherSomethingChanged() {
        ChunkStore store = newStore();
        KeptChunk chunk = new KeptChunk("world", 5, 5);
        assertTrue(store.add(chunk));
        assertFalse(store.add(chunk));
        assertTrue(store.remove(chunk));
        assertFalse(store.remove(chunk));
    }

    @Test
    void unreadableLinesAreSkipped() throws IOException {
        Files.writeString(folder.resolve("chunks.yml"), "chunks:\n- 1,2,world\n- nonsense\n- 3,4,world\n");
        ChunkStore store = newStore();
        store.load();
        assertEquals(Set.of(new KeptChunk("world", 1, 2), new KeptChunk("world", 3, 4)), store.all());
    }

    @Test
    void groupsByWorld() {
        ChunkStore store = newStore();
        store.add(new KeptChunk("b", 0, 0));
        store.add(new KeptChunk("a", 0, 0));
        store.add(new KeptChunk("a", 1, 0));
        assertEquals(List.of("a", "b"), List.copyOf(store.worlds()));
        assertEquals(2, store.inWorld("a").size());
        assertTrue(store.hasWorld("b"));
        assertFalse(store.hasWorld("c"));
    }

    @Test
    void knowsWhetherTheFileExists() {
        ChunkStore store = newStore();
        assertFalse(store.fileExists());
        store.save();
        assertTrue(store.fileExists());
    }
}
