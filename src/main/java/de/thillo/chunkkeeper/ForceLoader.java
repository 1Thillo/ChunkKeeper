package de.thillo.chunkkeeper;

import java.util.Collection;

/**
 * Switches the force-load flag of chunks. {@link ChunkLoader} does it on the server; the unit tests
 * use a simple fake, so {@link Keeper} can be tested without a running server.
 */
public interface ForceLoader {

    void keepLoaded(Collection<KeptChunk> chunks);

    void release(Collection<KeptChunk> chunks);
}
