package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class KeptChunkTest {

    @Test
    void parsesOwnFormat() {
        assertEquals(Optional.of(new KeptChunk("world", 12, -5)), KeptChunk.parse("12,-5,world", ","));
    }

    @Test
    void parsesKeepChunksFormat() {
        assertEquals(Optional.of(new KeptChunk("halloween", -3, 7)), KeptChunk.parse("-3#7#halloween", "#"));
    }

    @Test
    void keepsSeparatorInsideWorldName() {
        assertEquals(Optional.of(new KeptChunk("my,world", 1, 2)), KeptChunk.parse("1,2,my,world", ","));
        assertEquals(Optional.of(new KeptChunk("a#b", 1, 2)), KeptChunk.parse("1#2#a#b", "#"));
    }

    @Test
    void rejectsBrokenEntries() {
        assertTrue(KeptChunk.parse("broken", ",").isEmpty());
        assertTrue(KeptChunk.parse("1,2", ",").isEmpty());
        assertTrue(KeptChunk.parse("1,2,", ",").isEmpty());
        assertTrue(KeptChunk.parse("x,2,world", ",").isEmpty());
        assertTrue(KeptChunk.parse("1#2#world", ",").isEmpty());
    }

    @Test
    void serializeAndParseRoundTrip() {
        KeptChunk chunk = new KeptChunk("world_nether", -100, 42);
        assertEquals(Optional.of(chunk), KeptChunk.parse(chunk.serialize(), ","));
    }

    @Test
    void blockToChunkRoundsDown() {
        assertEquals(new KeptChunk("w", 0, 0), KeptChunk.atBlock("w", 0, 15));
        assertEquals(new KeptChunk("w", 1, 0), KeptChunk.atBlock("w", 16, 0));
        // -1 lies in chunk -1, not chunk 0
        assertEquals(new KeptChunk("w", -1, -1), KeptChunk.atBlock("w", -1, -16));
        assertEquals(new KeptChunk("w", -2, 0), KeptChunk.atBlock("w", -17, 0));
    }
}
