package de.thillo.chunkkeeper;

import java.util.Collection;

/**
 * Adds chunks to or removes them from the list, switches their force-load flag and saves the file.
 * All commands, the importer and the rail search go through here.
 */
public final class Keeper {

    private final ChunkStore store;
    private final ChunkLoader loader;

    public Keeper(ChunkStore store, ChunkLoader loader) {
        this.store = store;
        this.loader = loader;
    }

    /** @return how many of the chunks were not kept before */
    public int keep(Collection<KeptChunk> chunks) {
        int added = 0;
        for (KeptChunk chunk : chunks) {
            if (store.add(chunk)) {
                loader.keepLoaded(chunk);
                added++;
            }
        }
        if (added > 0) {
            store.save();
        }
        return added;
    }

    /** @return how many of the chunks were kept before */
    public int release(Collection<KeptChunk> chunks) {
        int removed = 0;
        for (KeptChunk chunk : chunks) {
            if (store.remove(chunk)) {
                loader.release(chunk);
                removed++;
            }
        }
        if (removed > 0) {
            store.save();
        }
        return removed;
    }
}
