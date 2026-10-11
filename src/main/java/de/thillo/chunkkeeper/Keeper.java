package de.thillo.chunkkeeper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        List<KeptChunk> added = new ArrayList<>();
        for (KeptChunk chunk : chunks) {
            if (store.add(chunk)) {
                added.add(chunk);
            }
        }
        if (!added.isEmpty()) {
            loader.keepLoaded(added);
            store.save();
        }
        return added.size();
    }

    /** @return how many of the chunks were kept before */
    public int release(Collection<KeptChunk> chunks) {
        List<KeptChunk> removed = new ArrayList<>();
        for (KeptChunk chunk : chunks) {
            if (store.remove(chunk)) {
                removed.add(chunk);
            }
        }
        if (!removed.isEmpty()) {
            loader.release(removed);
            store.save();
        }
        return removed.size();
    }
}
