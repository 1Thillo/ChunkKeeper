package de.thillo.chunkkeeper;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * One walk over the kept chunks that sets missing force-load flags again, a few chunks at a time.
 * It knows nothing about the server, so the unit tests can run it with a fake; {@link ForceLoadRepair}
 * runs it on the server.
 */
final class ForceLoadCheck {

    /** Reads and sets the force-load flag of a chunk. */
    interface Flags {

        /** @return true if the chunk should have the flag and does not; false as well if its world is not loaded */
        boolean isMissing(KeptChunk chunk);

        void setAgain(KeptChunk chunk);
    }

    private final Iterator<KeptChunk> remaining;
    private final Predicate<KeptChunk> stillKept;
    private final Flags flags;
    private int repaired;

    ForceLoadCheck(Collection<KeptChunk> chunks, Predicate<KeptChunk> stillKept, Flags flags) {
        // Copy first: the walk takes several ticks and what is kept may change in between.
        this.remaining = List.copyOf(chunks).iterator();
        this.stillKept = stillKept;
        this.flags = flags;
    }

    /** Checks the next chunks. @return true if there are chunks left for another batch */
    boolean runBatch(int batchSize) {
        for (int i = 0; i < batchSize && remaining.hasNext(); i++) {
            KeptChunk chunk = remaining.next();
            // A chunk released since the walk began has no flag on purpose, so it must not get it back.
            if (stillKept.test(chunk) && flags.isMissing(chunk)) {
                flags.setAgain(chunk);
                repaired++;
            }
        }
        return remaining.hasNext();
    }

    /** @return how many flags this walk has set again so far */
    int repaired() {
        return repaired;
    }
}
