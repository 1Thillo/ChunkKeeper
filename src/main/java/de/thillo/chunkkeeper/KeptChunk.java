package de.thillo.chunkkeeper;

import java.util.Optional;
import java.util.regex.Pattern;

import org.bukkit.Chunk;

/**
 * One chunk that ChunkKeeper keeps loaded: the world name plus the chunk coordinates.
 * Chunk coordinates are block coordinates divided by 16, so chunk (1, 0) covers blocks x 16..31.
 */
public record KeptChunk(String world, int x, int z) {

    public static KeptChunk of(Chunk chunk) {
        return new KeptChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static KeptChunk atBlock(String world, int blockX, int blockZ) {
        // >> 4 divides by 16 and rounds down, also for negative numbers (-1 becomes chunk -1, not 0)
        return new KeptChunk(world, blockX >> 4, blockZ >> 4);
    }

    /**
     * Turns text like {@code "12,-5,world"} back into a chunk. The world comes last so that a
     * world name containing the separator still works.
     *
     * @param separator "," for ChunkKeeper's own file, "#" for KeepChunks' data.yml
     */
    public static Optional<KeptChunk> parse(String text, String separator) {
        String[] parts = text.split(Pattern.quote(separator), 3);
        if (parts.length != 3 || parts[2].isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new KeptChunk(parts[2], Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public String serialize() {
        return x + "," + z + "," + world;
    }

    /** Short form for chat messages, for example {@code (12, -5) in 'world'}. */
    public Text describe() {
        return Text.of("describe.chunk", "x", x, "z", z, "world", world);
    }
}
