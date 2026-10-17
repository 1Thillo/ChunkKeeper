package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The rules for single chunks and named areas that overlap. */
class KeeperTest {

    /** Remembers which chunks are force-loaded, like the server would. */
    private static final class FakeLoader implements ForceLoader {
        final Set<KeptChunk> forced = new HashSet<>();

        @Override
        public void keepLoaded(Collection<KeptChunk> chunks) {
            forced.addAll(chunks);
        }

        @Override
        public void release(Collection<KeptChunk> chunks) {
            forced.removeAll(chunks);
        }
    }

    private static final Origin TESTER = Origin.now("tester");

    private static final KeptChunk A = new KeptChunk("w", 0, 0);
    private static final KeptChunk B = new KeptChunk("w", 1, 0);
    private static final KeptChunk C = new KeptChunk("w", 2, 0);

    @TempDir
    Path folder;

    private ChunkStore store;
    private FakeLoader loader;
    private Keeper keeper;

    @BeforeEach
    void setUp() {
        store = new ChunkStore(new File(folder.toFile(), "chunks.yml"), Logger.getAnonymousLogger());
        loader = new FakeLoader();
        keeper = new Keeper(store, loader);
    }

    private static NamedArea area(String name, KeptChunk... chunks) {
        return new NamedArea(name, "w", Set.of(chunks), "test", TESTER);
    }

    @Test
    void releasingASingleChunkKeepsItLoadedWhileAnAreaHasIt() {
        keeper.keep(List.of(A), TESTER);
        keeper.createArea(area("farm", A, B));
        assertEquals(1, keeper.release(List.of(A)));
        assertTrue(loader.forced.contains(A));
        assertFalse(store.contains(A));
    }

    @Test
    void releasingAnAreaKeepsSingleChunksLoaded() {
        keeper.keep(List.of(A), TESTER);
        keeper.createArea(area("farm", A, B));
        Optional<Keeper.AreaRelease> released = keeper.releaseArea("farm");
        assertEquals(1, released.orElseThrow().unloaded());
        assertEquals(Set.of(A), loader.forced);
    }

    @Test
    void overlappingAreasKeepSharedChunksUntilBothAreGone() {
        keeper.createArea(area("one", A, B));
        assertEquals(Optional.of(1), keeper.createArea(area("two", B, C)));
        keeper.releaseArea("one");
        assertEquals(Set.of(B, C), loader.forced);
        keeper.releaseArea("two");
        assertTrue(loader.forced.isEmpty());
    }

    @Test
    void areaNamesCannotBeUsedTwice() {
        keeper.createArea(area("farm", A));
        assertEquals(Optional.empty(), keeper.createArea(area("farm", B)));
        assertFalse(loader.forced.contains(B));
    }

    @Test
    void releasingAnUnknownAreaDoesNothing() {
        assertEquals(Optional.empty(), keeper.releaseArea("nope"));
    }

    @Test
    void renameKeepsTheChunks() {
        keeper.createArea(area("old", A, B));
        assertTrue(keeper.renameArea("old", "new"));
        assertEquals(Set.of(A, B), store.area("new").orElseThrow().chunks());
        assertTrue(store.area("old").isEmpty());
        assertFalse(keeper.renameArea("missing", "other"));
    }

    @Test
    void convertMovesOnlySingleChunksAndUnloadsNothing() {
        keeper.keep(List.of(A, B), TESTER);
        Set<KeptChunk> forcedBefore = Set.copyOf(loader.forced);
        // the selection also covers C, which is not kept, so it must not end up in the area
        assertEquals(Optional.of(2), keeper.convertToArea("block", "w", List.of(A, B, C), "test", TESTER));
        assertEquals(Set.of(A, B), store.area("block").orElseThrow().chunks());
        assertTrue(store.all().isEmpty());
        assertEquals(forcedBefore, loader.forced);
    }

    @Test
    void convertWithNothingToMoveMakesNoArea() {
        assertEquals(Optional.of(0), keeper.convertToArea("empty", "w", List.of(A), "test", TESTER));
        assertTrue(store.area("empty").isEmpty());
    }

    @Test
    void convertRefusesATakenName() {
        keeper.keep(List.of(A), TESTER);
        keeper.createArea(area("farm", B));
        assertEquals(Optional.empty(), keeper.convertToArea("farm", "w", List.of(A), "test", TESTER));
        assertTrue(store.contains(A));
    }

    @Test
    void dissolveTurnsTheAreaIntoSingleChunksAndUnloadsNothing() {
        keeper.keep(List.of(A), TESTER);
        keeper.createArea(area("farm", A, B));
        Set<KeptChunk> forcedBefore = Set.copyOf(loader.forced);
        assertEquals(Optional.of(1), keeper.dissolveArea("farm", TESTER));
        assertEquals(Set.of(A, B), store.all());
        assertTrue(store.area("farm").isEmpty());
        assertEquals(forcedBefore, loader.forced);
        assertEquals(Optional.empty(), keeper.dissolveArea("farm", TESTER));
    }

    @Test
    void dissolvedChunksGetTheDissolverUnlessTheyWereKeptOnTheirOwnAlready() {
        keeper.keep(List.of(A), Origin.now("first"));
        keeper.createArea(area("farm", A, B));
        keeper.dissolveArea("farm", Origin.now("dissolver"));
        assertEquals("first", store.origin(A).orElseThrow().by());
        assertEquals("dissolver", store.origin(B).orElseThrow().by());
    }

    @Test
    void convertedAreaRemembersWhoConvertedIt() {
        keeper.keep(List.of(A), TESTER);
        keeper.convertToArea("block", "w", List.of(A), "test", Origin.now("converter"));
        assertEquals("converter", store.area("block").orElseThrow().origin().by());
    }

    @Test
    void releaseAllClearsEverything() {
        keeper.keep(List.of(A), TESTER);
        keeper.createArea(area("farm", B, C));
        Keeper.ReleaseAll released = keeper.releaseAll();
        assertEquals(new Keeper.ReleaseAll(1, 1), released);
        assertTrue(loader.forced.isEmpty());
        assertTrue(store.allKept().isEmpty());
    }

    @Test
    void keepingAChunkAnAreaAlreadyLoadsDoesNotLoadItTwice() {
        keeper.createArea(area("farm", A));
        loader.forced.clear();
        assertEquals(1, keeper.keep(List.of(A), TESTER));
        assertFalse(loader.forced.contains(A), "already force-loaded through the area, no second request");
    }
}
