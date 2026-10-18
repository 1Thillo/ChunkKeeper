package de.thillo.chunkkeeper;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of chunks in one world, for example everything between two corners or inside a
 * WorldGuard region. {@code description} says in chat messages where the chunks come from.
 */
public record Area(String world, List<KeptChunk> chunks, Text description) {

    /** Every chunk in the rectangle between two chunk corners, in any order. */
    public static Area rectangle(String world, int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        List<KeptChunk> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new KeptChunk(world, x, z));
            }
        }
        return new Area(world, chunks, Text.of("describe.rectangle", "x1", minX, "z1", minZ, "x2", maxX, "z2", maxZ, "world", world));
    }

    /**
     * The square of chunks around a center chunk: radius 0 is only the center, radius 1 the center
     * and its 8 neighbours (3 x 3), radius 2 is 5 x 5 and so on.
     */
    public static Area around(String world, int centerX, int centerZ, int radius) {
        Area square = rectangle(world, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
        Text description = radius == 0
                ? new KeptChunk(world, centerX, centerZ).describe()
                : Text.of("describe.radius", "radius", radius, "x", centerX, "z", centerZ, "world", world);
        return new Area(world, square.chunks(), description);
    }

    /** The largest radius whose square has at most {@code maxChunks} chunks. */
    public static int largestRadius(int maxChunks) {
        int side = (int) Math.sqrt(Math.max(1, maxChunks));
        return (side - 1) / 2;
    }

    /** How many chunks the rectangle would contain, without building the list. */
    public static long rectangleSize(int x1, int z1, int x2, int z2) {
        return (Math.abs((long) x1 - x2) + 1) * (Math.abs((long) z1 - z2) + 1);
    }
}
