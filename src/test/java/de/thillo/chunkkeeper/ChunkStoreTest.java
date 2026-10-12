package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
    void areasComeBackAfterLoading() {
        ChunkStore store = newStore();
        store.add(new KeptChunk("world", 1, 1));
        NamedArea area = new NamedArea("halloween", "event", Set.of(new KeptChunk("event", 0, 0), new KeptChunk("event", -1, 2)),
                "WorldGuard region 'halloween' in 'event'");
        assertTrue(store.addArea(area));
        store.save();

        ChunkStore reloaded = newStore();
        reloaded.load();
        assertEquals(Optional.of(area), reloaded.area("halloween"));
        assertEquals(Set.of(new KeptChunk("world", 1, 1)), reloaded.all());
    }

    @Test
    void areaNamesAreUnique() {
        ChunkStore store = newStore();
        assertTrue(store.addArea(new NamedArea("a", "w", Set.of(), "")));
        assertFalse(store.addArea(new NamedArea("a", "w", Set.of(new KeptChunk("w", 1, 1)), "")));
    }

    @Test
    void singleChunksAndAreasCountTogether() {
        ChunkStore store = newStore();
        KeptChunk single = new KeptChunk("w", 1, 1);
        KeptChunk inArea = new KeptChunk("w", 5, 5);
        store.add(single);
        store.addArea(new NamedArea("farm", "w", Set.of(inArea, single), ""));

        assertTrue(store.isKept(single));
        assertTrue(store.isKept(inArea));
        assertFalse(store.contains(inArea));
        assertFalse(store.isKept(new KeptChunk("w", 9, 9)));
        assertEquals(Set.of(single, inArea), store.allKept());
        assertEquals(List.of("farm"), store.areasContaining(single));
    }

    @Test
    void worldWithOnlyAnAreaCounts() {
        ChunkStore store = newStore();
        store.addArea(new NamedArea("farm", "nether", Set.of(new KeptChunk("nether", 0, 0)), ""));
        assertTrue(store.hasWorld("nether"));
    }

    @Test
    void unreadableAreasAreSkipped() throws IOException {
        Files.writeString(folder.resolve("chunks.yml"), """
                areas:
                  Bad.Name:
                    world: w
                  nowWorld:
                    chunks: ['1,2']
                  good:
                    world: w
                    chunks: ['1,2', 'nonsense']
                """);
        ChunkStore store = newStore();
        store.load();
        assertEquals(List.of("good"), store.areas().stream().map(NamedArea::name).toList());
        assertEquals(Set.of(new KeptChunk("w", 1, 2)), store.area("good").orElseThrow().chunks());
    }

    @Test
    void knowsWhetherTheFileExists() {
        ChunkStore store = newStore();
        assertFalse(store.fileExists());
        store.save();
        assertTrue(store.fileExists());
    }
}
