package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test
    void addingToAnAreaLoadsOnlyChunksNothingKeptBefore() {
        keeper.createArea(area("farm", A));
        keeper.keep(List.of(B), TESTER);
        Keeper.AreaChange change = keeper.addToArea("farm", List.of(A, B, C), 100);
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 2, 1), change, "A was in the area, B kept on its own");
        assertEquals(Set.of(A, B, C), store.area("farm").orElseThrow().chunks());
        assertEquals(Set.of(A, B, C), loader.forced);
    }

    @Test
    void addingKeepsNameSourceAndOrigin() {
        keeper.createArea(area("farm", A));
        keeper.addToArea("farm", List.of(B), 100);
        NamedArea farm = store.area("farm").orElseThrow();
        assertEquals("test", farm.source());
        assertEquals(TESTER, farm.origin());
    }

    @Test
    void addingChunksTheAreaHasChangesNothing() {
        keeper.createArea(area("farm", A, B));
        assertEquals(Keeper.AreaChange.Outcome.NOTHING_TO_CHANGE, keeper.addToArea("farm", List.of(A), 100).outcome());
    }

    @Test
    void addingRespectsTheLimitForTheWholeArea() {
        keeper.createArea(area("farm", A, B));
        Keeper.AreaChange change = keeper.addToArea("farm", List.of(C), 2);
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.TOO_LARGE, 3, 0), change);
        assertEquals(Set.of(A, B), store.area("farm").orElseThrow().chunks());
        assertFalse(loader.forced.contains(C));
    }

    @Test
    void addingToAnUnknownAreaFails() {
        assertEquals(Keeper.AreaChange.Outcome.NO_SUCH_AREA, keeper.addToArea("farm", List.of(A), 100).outcome());
        assertTrue(store.areas().isEmpty());
    }

    @Test
    void removingFromAnAreaUnloadsOnlyChunksNothingElseKeeps() {
        keeper.createArea(area("farm", A, B, C));
        keeper.keep(List.of(B), TESTER);
        Keeper.AreaChange change = keeper.removeFromArea("farm", List.of(A, B), () -> { });
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 2, 1), change, "B is still kept on its own");
        assertEquals(Set.of(C), store.area("farm").orElseThrow().chunks());
        assertEquals(Set.of(B, C), loader.forced);
    }

    @Test
    void removingRunsTheBackupStepOnlyWhenSomethingChanges() {
        keeper.createArea(area("farm", A, B));
        List<String> steps = new ArrayList<>();
        keeper.removeFromArea("farm", List.of(C), () -> steps.add("nothing"));
        keeper.removeFromArea("farm", List.of(A, B), () -> steps.add("empty"));
        keeper.removeFromArea("farm", List.of(A), () -> steps.add("changed"));
        assertEquals(List.of("changed"), steps);
    }

    @Test
    void removingEveryChunkIsRefused() {
        keeper.createArea(area("farm", A, B));
        Keeper.AreaChange change = keeper.removeFromArea("farm", List.of(A, B, C), () -> { });
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.WOULD_BE_EMPTY, 2, 0), change);
        assertEquals(Set.of(A, B), store.area("farm").orElseThrow().chunks());
        assertEquals(Set.of(A, B), loader.forced);
    }

    @Test
    void removingChunksTheAreaDoesNotHaveChangesNothing() {
        keeper.createArea(area("farm", A));
        assertEquals(Keeper.AreaChange.Outcome.NOTHING_TO_CHANGE, keeper.removeFromArea("farm", List.of(B), () -> { }).outcome());
        assertEquals(Keeper.AreaChange.Outcome.NO_SUCH_AREA, keeper.removeFromArea("other", List.of(A), () -> { }).outcome());
    }

    @Test
    void pausingUnloadsOnlyChunksNothingElseKeeps() {
        keeper.createArea(area("farm", A, B));
        keeper.keep(List.of(B), TESTER);
        Keeper.AreaChange change = keeper.pauseArea("farm");
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 2, 1), change, "B is kept on its own");
        assertTrue(store.area("farm").orElseThrow().paused());
        assertEquals(Set.of(B), loader.forced);
        assertFalse(store.isKept(A));
    }

    @Test
    void resumingLoadsTheChunksAgain() {
        keeper.createArea(area("farm", A, B));
        keeper.keep(List.of(B), TESTER);
        keeper.pauseArea("farm");
        Keeper.AreaChange change = keeper.resumeArea("farm");
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 2, 1), change, "B was still loaded");
        assertFalse(store.area("farm").orElseThrow().paused());
        assertEquals(Set.of(A, B), loader.forced);
    }

    @Test
    void pausingTwiceOrResumingAnActiveAreaChangesNothing() {
        keeper.createArea(area("farm", A));
        assertEquals(Keeper.AreaChange.Outcome.NOTHING_TO_CHANGE, keeper.resumeArea("farm").outcome());
        keeper.pauseArea("farm");
        assertEquals(Keeper.AreaChange.Outcome.NOTHING_TO_CHANGE, keeper.pauseArea("farm").outcome());
        assertEquals(Keeper.AreaChange.Outcome.NO_SUCH_AREA, keeper.pauseArea("other").outcome());
        assertEquals(Keeper.AreaChange.Outcome.NO_SUCH_AREA, keeper.resumeArea("other").outcome());
    }

    @Test
    void pausedAreaStaysPausedAfterSavingAndLoading() {
        keeper.createArea(area("farm", A));
        keeper.pauseArea("farm");
        ChunkStore reloaded = new ChunkStore(new File(folder.toFile(), "chunks.yml"), Logger.getAnonymousLogger());
        reloaded.load();
        assertTrue(reloaded.area("farm").orElseThrow().paused());
        assertTrue(reloaded.allKept().isEmpty());
    }

    @Test
    void changingAPausedAreaLoadsAndUnloadsNothing() {
        keeper.createArea(area("farm", A, B));
        keeper.pauseArea("farm");
        loader.forced.add(C);  // something else force-loads C, ChunkKeeper must not touch it
        Keeper.AreaChange added = keeper.addToArea("farm", List.of(C), 100);
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 1, 0), added);
        Keeper.AreaChange removed = keeper.removeFromArea("farm", List.of(A, C), () -> { });
        assertEquals(new Keeper.AreaChange(Keeper.AreaChange.Outcome.CHANGED, 2, 0), removed);
        assertEquals(Set.of(C), loader.forced);
        assertTrue(store.area("farm").orElseThrow().paused());
    }

    @Test
    void releasingAPausedAreaUnloadsNothing() {
        keeper.createArea(area("farm", A));
        keeper.pauseArea("farm");
        loader.forced.add(A);  // something else force-loads A
        assertEquals(0, keeper.releaseArea("farm").orElseThrow().unloaded());
        assertEquals(Set.of(A), loader.forced);
    }

    @Test
    void aPausedAreaCannotBeDissolved() {
        keeper.createArea(area("farm", A));
        keeper.pauseArea("farm");
        assertTrue(keeper.dissolveArea("farm", TESTER).isEmpty());
        assertTrue(store.area("farm").isPresent());
        assertFalse(store.contains(A));
    }

    @Test
    void renamingKeepsTheAreaPaused() {
        keeper.createArea(area("farm", A));
        keeper.pauseArea("farm");
        keeper.renameArea("farm", "barn");
        assertTrue(store.area("barn").orElseThrow().paused());
    }

    @Test
    void changedAreasSurviveSavingAndLoading() {
        keeper.createArea(area("farm", A, B));
        keeper.addToArea("farm", List.of(C), 100);
        keeper.removeFromArea("farm", List.of(A), () -> { });
        ChunkStore reloaded = new ChunkStore(new File(folder.toFile(), "chunks.yml"), Logger.getAnonymousLogger());
        reloaded.load();
        assertEquals(Set.of(B, C), reloaded.area("farm").orElseThrow().chunks());
    }
}
