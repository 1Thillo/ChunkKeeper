package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChunkBordersTest {

    @Test
    void aSingleChunkHasFourEdges() {
        List<ChunkBorders.Edge> edges = ChunkBorders.of(List.of(new KeptChunk("world", 1, 2)));
        assertEquals(4, edges.size());
        // chunk (1, 2) covers blocks x 16..31 and z 32..47
        assertTrue(edges.contains(new ChunkBorders.Edge(new KeptChunk("world", 1, 2), 16, 32, 32, 32)));
        assertTrue(edges.contains(new ChunkBorders.Edge(new KeptChunk("world", 1, 2), 32, 32, 32, 48)));
    }

    @Test
    void edgesBetweenChunksOfTheGroupAreLeftOut() {
        // a 3 x 3 square has 3 edges per side on the outside and none inside
        assertEquals(12, ChunkBorders.of(Area.rectangle("world", 0, 0, 2, 2).chunks()).size());
    }

    @Test
    void negativeCoordinatesWork() {
        List<ChunkBorders.Edge> edges = ChunkBorders.of(List.of(new KeptChunk("world", -1, -1)));
        assertTrue(edges.contains(new ChunkBorders.Edge(new KeptChunk("world", -1, -1), -16, -16, 0, -16)));
    }

    @Test
    void anEmptyGroupHasNoEdges() {
        assertEquals(0, ChunkBorders.of(List.of()).size());
    }
}
