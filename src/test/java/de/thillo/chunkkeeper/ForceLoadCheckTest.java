package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Setting missing force-load flags again, without a server. */
class ForceLoadCheckTest {

    /** Remembers which chunks have the flag, like the server would. */
    private static final class FakeFlags implements ForceLoadCheck.Flags {
        final Set<KeptChunk> forced = new HashSet<>();

        @Override
        public boolean isMissing(KeptChunk chunk) {
            return !forced.contains(chunk);
        }

        @Override
        public void setAgain(KeptChunk chunk) {
            forced.add(chunk);
        }
    }

    private static final KeptChunk A = new KeptChunk("w", 0, 0);
    private static final KeptChunk B = new KeptChunk("w", 1, 0);
    private static final KeptChunk C = new KeptChunk("w", 2, 0);

    @Test
    void setsOnlyMissingFlagsAndCountsThem() {
        FakeFlags flags = new FakeFlags();
        flags.forced.add(A);

        ForceLoadCheck check = new ForceLoadCheck(List.of(A, B, C), chunk -> true, flags);

        assertFalse(check.runBatch(10));
        assertEquals(Set.of(A, B, C), flags.forced);
        assertEquals(2, check.repaired());
    }

    @Test
    void nothingToRepairCountsNothing() {
        FakeFlags flags = new FakeFlags();
        flags.forced.addAll(List.of(A, B));

        ForceLoadCheck check = new ForceLoadCheck(List.of(A, B), chunk -> true, flags);

        assertFalse(check.runBatch(10));
        assertEquals(0, check.repaired());
    }

    @Test
    void worksInBatches() {
        FakeFlags flags = new FakeFlags();
        ForceLoadCheck check = new ForceLoadCheck(List.of(A, B, C), chunk -> true, flags);

        assertTrue(check.runBatch(2));
        assertEquals(2, flags.forced.size());

        assertFalse(check.runBatch(2));
        assertEquals(3, flags.forced.size());
        assertEquals(3, check.repaired());
    }

    @Test
    void chunkReleasedDuringTheWalkDoesNotGetItsFlagBack() {
        FakeFlags flags = new FakeFlags();
        Set<KeptChunk> kept = new HashSet<>(List.of(A, B));
        ForceLoadCheck check = new ForceLoadCheck(List.of(A, B), kept::contains, flags);

        assertTrue(check.runBatch(1));
        kept.remove(B);
        assertFalse(check.runBatch(1));

        assertEquals(Set.of(A), flags.forced);
        assertEquals(1, check.repaired());
    }

    @Test
    void noChunksIsDoneAtOnce() {
        ForceLoadCheck check = new ForceLoadCheck(List.of(), chunk -> true, new FakeFlags());

        assertFalse(check.runBatch(10));
        assertEquals(0, check.repaired());
    }
}
