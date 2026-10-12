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
 * another area.
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
    public synchronized int keep(Collection<KeptChunk> chunks) {
        List<KeptChunk> nowLoaded = new ArrayList<>();
        int added = 0;
        for (KeptChunk chunk : chunks) {
            boolean wasKept = store.isKept(chunk);
            if (store.add(chunk)) {
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
     * Removes a named area. Its chunks stay loaded where something else still keeps them.
     *
     * @return the removed area and how many of its chunks are no longer loaded, or empty if there is no such area
     */
    public synchronized Optional<AreaRelease> releaseArea(String name) {
        Optional<NamedArea> area = store.removeArea(name);
        if (area.isEmpty()) {
            return Optional.empty();
        }
        List<KeptChunk> nowUnloaded = area.get().chunks().stream().filter(chunk -> !store.isKept(chunk)).toList();
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
    public synchronized Optional<Integer> convertToArea(String name, String world, Collection<KeptChunk> selection, String source) {
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
        store.addArea(new NamedArea(name, world, singles, source));
        for (KeptChunk chunk : singles) {
            store.remove(chunk);
        }
        store.save();
        return Optional.of(singles.size());
    }

    /**
     * Removes a named area but keeps all its chunks, now on their own. Nothing is unloaded.
     *
     * @return how many of its chunks were not kept on their own before, or empty if there is no such area
     */
    public synchronized Optional<Integer> dissolveArea(String name) {
        Optional<NamedArea> area = store.area(name);
        if (area.isEmpty()) {
            return Optional.empty();
        }
        int added = 0;
        for (KeptChunk chunk : area.get().chunks()) {
            if (store.add(chunk)) {
                added++;
            }
        }
        store.removeArea(name);
        store.save();
        return Optional.of(added);
    }

    /** @return false if the new name is taken or the old one does not exist */
    public synchronized boolean renameArea(String oldName, String newName) {
        Optional<NamedArea> area = store.area(oldName);
        if (area.isEmpty() || store.area(newName).isPresent()) {
            return false;
        }
        store.removeArea(oldName);
        NamedArea old = area.get();
        store.addArea(new NamedArea(newName, old.world(), old.chunks(), old.source()));
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
}
