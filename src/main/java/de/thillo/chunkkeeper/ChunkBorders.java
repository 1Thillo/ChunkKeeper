package de.thillo.chunkkeeper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The outline of a group of chunks: the chunk edges that have a chunk of the group on one side and
 * none on the other. Edges between two chunks of the same group are left out, so a 3 x 3 area is
 * one square, not nine.
 */
public final class ChunkBorders {

    /**
     * One edge of a chunk as a line in block coordinates, 16 blocks long, from (x1, z1) to (x2, z2).
     * {@code chunk} is the chunk of the group that the edge belongs to.
     */
    public record Edge(KeptChunk chunk, int x1, int z1, int x2, int z2) {
    }

    private ChunkBorders() {
    }

    /** The outline of {@code chunks}; all of them should be in the same world. */
    public static List<Edge> of(Collection<KeptChunk> chunks) {
        Set<KeptChunk> group = new HashSet<>(chunks);
        List<Edge> edges = new ArrayList<>();
        for (KeptChunk chunk : group) {
            int west = chunk.x() * 16;
            int north = chunk.z() * 16;
            int east = west + 16;
            int south = north + 16;
            if (!group.contains(neighbour(chunk, 0, -1))) {
                edges.add(new Edge(chunk, west, north, east, north));
            }
            if (!group.contains(neighbour(chunk, 0, 1))) {
                edges.add(new Edge(chunk, west, south, east, south));
            }
            if (!group.contains(neighbour(chunk, -1, 0))) {
                edges.add(new Edge(chunk, west, north, west, south));
            }
            if (!group.contains(neighbour(chunk, 1, 0))) {
                edges.add(new Edge(chunk, east, north, east, south));
            }
        }
        return edges;
    }

    private static KeptChunk neighbour(KeptChunk chunk, int dx, int dz) {
        return new KeptChunk(chunk.world(), chunk.x() + dx, chunk.z() + dz);
    }
}
