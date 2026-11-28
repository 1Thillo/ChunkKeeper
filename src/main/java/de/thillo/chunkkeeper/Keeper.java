package de.thillo.chunkkeeper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Changes what is kept and switches the force-load flags to match. All commands, the importer and
 * the rail search go through here.
 *
 * <p>Single chunks and named areas exist side by side. A chunk is force-loaded while anything keeps
 * it: itself, or at least one area. So releasing a single chunk leaves it loaded if an area still
 * contains it, and releasing an area leaves the chunks loaded that are kept on their own or by
 * another area. A paused area keeps nothing loaded, but stays stored until it is resumed.
 *
 * <p>The methods are {@code synchronized}: on Folia two commands can run at the same time, and
 * "is this chunk still kept by something?" must not change halfway through.
 */
public final class Keeper {

    private final ChunkStore store;
    private final ForceLoader loader;

    public Keeper(ChunkStore store, ForceLoader loader) {
        this.store = store;
        this.loader = loader;
    }

    /** Keeps chunks on their own. @return how many of them were not kept on their own before */
    public synchronized int keep(Collection<KeptChunk> chunks, Origin origin) {
        List<KeptChunk> nowLoaded = new ArrayList<>();
        int added = 0;
        for (KeptChunk chunk : chunks) {
            boolean wasKept = store.isKept(chunk);
            if (store.add(chunk, origin)) {
                added++;
                if (!wasKept) {
                    nowLoaded.add(chunk);
                }
            }
        }
        loader.keepLoaded(nowLoaded);
        if (added > 0) {
            store.save();
        }
        return added;
    }

    /**
     * Stops keeping chunks on their own. Chunks that belong to a named area stay loaded.
     *
     * @return how many of them were kept on their own before
     */
    public synchronized int release(Collection<KeptChunk> chunks) {
        List<KeptChunk> nowUnloaded = new ArrayList<>();
        int removed = 0;
        for (KeptChunk chunk : chunks) {
            if (store.remove(chunk)) {
                removed++;
                if (!store.isKept(chunk)) {
                    nowUnloaded.add(chunk);
                }
            }
        }
        loader.release(nowUnloaded);
        if (removed > 0) {
            store.save();
        }
        return removed;
    }

    /**
     * Adds a named area.
     *
     * @return how many of its chunks were not kept by anything before, or empty if the name is taken
     */
    public synchronized Optional<Integer> createArea(NamedArea area) {
        List<KeptChunk> nowLoaded = area.chunks().stream().filter(chunk -> !store.isKept(chunk)).toList();
        if (!store.addArea(area)) {
            return Optional.empty();
        }
        loader.keepLoaded(nowLoaded);
        store.save();
        return Optional.of(nowLoaded.size());
    }

    /**
     * Removes a named area. Its chunks stay loaded where something else still keeps them. A paused
     * area has nothing loaded, so releasing it unloads nothing.
     *
     * @return the removed area and how many of its chunks are no longer loaded, or empty if there is no such area
     */
    public synchronized Optional<AreaRelease> releaseArea(String name) {
        Optional<NamedArea> area = store.removeArea(name);
        if (area.isEmpty()) {
            return Optional.empty();
        }
        List<KeptChunk> nowUnloaded = area.get().paused()
                ? List.of()
                : area.get().chunks().stream().filter(chunk -> !store.isKept(chunk)).toList();
        loader.release(nowUnloaded);
        store.save();
        return Optional.of(new AreaRelease(area.get(), nowUnloaded.size()));
    }

    /**
     * Turns the single chunks inside {@code selection} into a new named area. Chunks in the selection
     * that are not kept on their own are left out. Nothing is unloaded or loaded: every chunk is kept
     * the whole time, first on its own and then by the area.
     *
     * @return how many chunks were moved into the area (0 means nothing to convert and no area was
     *         made), or empty if the name is taken
     */
    public synchronized Optional<Integer> convertToArea(String name, String world, Collection<KeptChunk> selection,
            String source, Origin origin) {
        if (store.area(name).isPresent()) {
            return Optional.empty();
        }
        Set<KeptChunk> singles = new HashSet<>();
        for (KeptChunk chunk : selection) {
            if (store.contains(chunk)) {
                singles.add(chunk);
            }
        }
        if (singles.isEmpty()) {
            return Optional.of(0);
        }
        // add the area before removing the single chunks, so the chunks are never "not kept"
        store.addArea(new NamedArea(name, world, singles, source, origin));
        for (KeptChunk chunk : singles) {
            store.remove(chunk);
        }
        store.save();
        return Optional.of(singles.size());
    }

    /**
     * Removes a named area but keeps all its chunks, now on their own. Nothing is unloaded. A paused
     * area cannot be dissolved, because its chunks would suddenly be loaded; resume it first.
     *
     * @return how many of its chunks were not kept on their own before, or empty if there is no such
     *         area or it is paused
     */
    public synchronized Optional<Integer> dissolveArea(String name, Origin origin) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty() || area.get().paused()) {
            return Optional.empty();
        }
        int added = 0;
        for (KeptChunk chunk : area.get().chunks()) {
            if (store.add(chunk, origin)) {
                added++;
            }
        }
        store.removeArea(name);
        store.save();
        return Optional.of(added);
    }

    /**
     * Adds chunks to an existing named area. Chunks the area already has are skipped.
     *
     * @param limit the most chunks the area may have afterwards (max-area-chunks)
     * @return what happened; {@code loadChanged} counts the chunks that were not kept by anything before
     */
    public synchronized AreaChange addToArea(String name, Collection<KeptChunk> chunks, int limit) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NO_SUCH_AREA, 0, 0);
        }
        Set<KeptChunk> newChunks = new HashSet<>(chunks);
        newChunks.removeAll(area.get().chunks());
        if (newChunks.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NOTHING_TO_CHANGE, 0, 0);
        }
        Set<KeptChunk> result = new HashSet<>(area.get().chunks());
        result.addAll(newChunks);
        if (result.size() > limit) {
            return new AreaChange(AreaChange.Outcome.TOO_LARGE, result.size(), 0);
        }
        // look this up before the area changes, afterwards every new chunk counts as kept;
        // a paused area loads nothing
        List<KeptChunk> nowLoaded = area.get().paused()
                ? List.of()
                : newChunks.stream().filter(chunk -> !store.isKept(chunk)).toList();
        store.removeArea(name);
        store.addArea(area.get().withChunks(result));
        loader.keepLoaded(nowLoaded);
        store.save();
        return new AreaChange(AreaChange.Outcome.CHANGED, newChunks.size(), nowLoaded.size());
    }

    /**
     * Removes chunks from an existing named area. Removed chunks stay loaded where something else
     * still keeps them. An area cannot lose all its chunks this way, {@link #releaseArea} is for that.
     *
     * @param beforeChange runs once all checks have passed and just before anything changes, for
     *                     example to make a backup
     * @return what happened; {@code loadChanged} counts the chunks that are no longer loaded
     */
    public synchronized AreaChange removeFromArea(String name, Collection<KeptChunk> chunks, Runnable beforeChange) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NO_SUCH_AREA, 0, 0);
        }
        Set<KeptChunk> removed = new HashSet<>(chunks);
        removed.retainAll(area.get().chunks());
        if (removed.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NOTHING_TO_CHANGE, 0, 0);
        }
        Set<KeptChunk> result = new HashSet<>(area.get().chunks());
        result.removeAll(removed);
        if (result.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.WOULD_BE_EMPTY, removed.size(), 0);
        }
        beforeChange.run();
        store.removeArea(name);
        store.addArea(area.get().withChunks(result));
        // look this up after the area changed, so the area itself no longer counts;
        // a paused area had nothing loaded
        List<KeptChunk> nowUnloaded = area.get().paused()
                ? List.of()
                : removed.stream().filter(chunk -> !store.isKept(chunk)).toList();
        loader.release(nowUnloaded);
        store.save();
        return new AreaChange(AreaChange.Outcome.CHANGED, removed.size(), nowUnloaded.size());
    }

    /**
     * Pauses a named area: it stays stored, but its chunks are unloaded unless something else keeps them.
     *
     * @return what happened; NOTHING_TO_CHANGE means it was paused already, {@code loadChanged}
     *         counts the chunks that are no longer loaded
     */
    public synchronized AreaChange pauseArea(String name) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NO_SUCH_AREA, 0, 0);
        }
        if (area.get().paused()) {
            return new AreaChange(AreaChange.Outcome.NOTHING_TO_CHANGE, area.get().chunks().size(), 0);
        }
        store.removeArea(name);
        store.addArea(area.get().withPaused(true));
        // look this up after pausing, so the area itself no longer counts
        List<KeptChunk> nowUnloaded = area.get().chunks().stream().filter(chunk -> !store.isKept(chunk)).toList();
        loader.release(nowUnloaded);
        store.save();
        return new AreaChange(AreaChange.Outcome.CHANGED, area.get().chunks().size(), nowUnloaded.size());
    }

    /**
     * Resumes a paused area: its chunks are kept loaded again.
     *
     * @return what happened; NOTHING_TO_CHANGE means it was not paused, {@code loadChanged} counts
     *         the chunks that were not kept by anything else
     */
    public synchronized AreaChange resumeArea(String name) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty()) {
            return new AreaChange(AreaChange.Outcome.NO_SUCH_AREA, 0, 0);
        }
        if (!area.get().paused()) {
            return new AreaChange(AreaChange.Outcome.NOTHING_TO_CHANGE, area.get().chunks().size(), 0);
        }
        // look this up while the area is still paused
        List<KeptChunk> nowLoaded = area.get().chunks().stream().filter(chunk -> !store.isKept(chunk)).toList();
        store.removeArea(name);
        store.addArea(area.get().withPaused(false));
        loader.keepLoaded(nowLoaded);
        store.save();
        return new AreaChange(AreaChange.Outcome.CHANGED, area.get().chunks().size(), nowLoaded.size());
    }

    /** @return false if the new name is taken or the old one does not exist */
    public synchronized boolean renameArea(String oldName, String newName) {
        Optional<NamedArea> area = store.area(oldName);
        if (area.isEmpty() || store.area(newName).isPresent()) {
            return false;
        }
        store.removeArea(oldName);
        store.addArea(area.get().renamed(newName));
        store.save();
        return true;
    }

    /** Releases every single chunk and every named area. @return how many single chunks and areas there were */
    public synchronized ReleaseAll releaseAll() {
        Set<KeptChunk> kept = store.allKept();
        int singles = store.size();
        int areas = store.areas().size();
        for (KeptChunk chunk : store.all()) {
            store.remove(chunk);
        }
        for (NamedArea area : store.areas()) {
            store.removeArea(area.name());
        }
        loader.release(kept);
        store.save();
        return new ReleaseAll(singles, areas);
    }

    public record AreaRelease(NamedArea area, int unloaded) {
    }

    public record ReleaseAll(int chunks, int areas) {
    }

    /**
     * The result of {@link #addToArea}, {@link #removeFromArea}, {@link #pauseArea} or {@link #resumeArea}.
     *
     * @param chunks      how many chunks were added or removed; for TOO_LARGE the size the area
     *                    would have had, for WOULD_BE_EMPTY how many chunks would have been removed;
     *                    for pause and resume the size of the area
     * @param loadChanged how many chunks were loaded or unloaded because of it
     */
    public record AreaChange(Outcome outcome, int chunks, int loadChanged) {

        public enum Outcome {
            CHANGED,
            NO_SUCH_AREA,
            /**
             * add: the area has all these chunks already; remove: it has none of them; pause: it is
             * paused already; resume: it is not paused
             */
            NOTHING_TO_CHANGE,
            TOO_LARGE,
            WOULD_BE_EMPTY
        }
    }
}
