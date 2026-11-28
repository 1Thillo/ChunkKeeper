package de.thillo.chunkkeeper;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A group of kept chunks with a name, for example "halloween". It is kept and released as a whole,
 * independently of single chunks: a chunk stays loaded as long as it is kept on its own or belongs
 * to at least one named area that is not paused.
 *
 * @param source how the area was made, for messages, e.g. "WorldGuard region 'halloween' in 'world'"
 * @param origin who created it and when
 * @param paused a paused area stays stored with everything it has, but keeps no chunk loaded
 */
public record NamedArea(String name, String world, Set<KeptChunk> chunks, String source, Origin origin, boolean paused) {

    /** Lower-case letters, digits, "-" and "_", so a name is easy to type and safe as a YAML key. */
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public NamedArea {
        chunks = Set.copyOf(chunks);
    }

    /** A new area, which is not paused. */
    public NamedArea(String name, String world, Set<KeptChunk> chunks, String source, Origin origin) {
        this(name, world, chunks, source, origin, false);
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public boolean contains(KeptChunk chunk) {
        return chunks.contains(chunk);
    }

    public NamedArea renamed(String newName) {
        return new NamedArea(newName, world, chunks, source, origin, paused);
    }

    /** The same area with other chunks. Everything else stays as it is. */
    public NamedArea withChunks(Set<KeptChunk> newChunks) {
        return new NamedArea(name, world, newChunks, source, origin, paused);
    }

    /** The same area, paused or not. */
    public NamedArea withPaused(boolean nowPaused) {
        return new NamedArea(name, world, chunks, source, origin, nowPaused);
    }
}
