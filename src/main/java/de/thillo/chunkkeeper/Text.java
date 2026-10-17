package de.thillo.chunkkeeper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A message that is not translated yet: the key of its text in messages/&lt;language&gt;.yml and the
 * values for its placeholders. A value may be another {@code Text}, which is translated into the
 * same language, for example the description of a chunk inside a longer message.
 */
public record Text(String key, Map<String, Object> placeholders) {

    public Text {
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }

    /**
     * @param pairs placeholder names and values, alternating:
     *              {@code Text.of("chunk.kept", "chunk", chunk.describe())}
     */
    public static Text of(String key, Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must come in name/value pairs: " + key);
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            placeholders.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return new Text(key, placeholders);
    }
}
