package de.thillo.chunkkeeper;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * A group of kept chunks with a name, for example "halloween". It is kept and released as a whole,
 * independently of single chunks: a chunk stays loaded as long as it is kept on its own or belongs
 * to at least one named area.
 *
 * @param source how the area was made, for messages, e.g. "WorldGuard region 'halloween' in 'world'"
 */
public record NamedArea(String name, String world, Set<KeptChunk> chunks, String source) {

    /** Lower-case letters, digits, "-" and "_", so a name is easy to type and safe as a YAML key. */
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public NamedArea {
        chunks = Set.copyOf(chunks);
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public boolean contains(KeptChunk chunk) {
        return chunks.contains(chunk);
    }
}
