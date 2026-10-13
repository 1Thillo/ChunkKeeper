package de.thillo.chunkkeeper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Who added a chunk or area, and when. {@code at} is empty for entries from before ChunkKeeper
 * remembered this.
 *
 * @param by a player name, "CONSOLE", or a short note such as "KeepChunks import"
 */
public record Origin(String by, Optional<Instant> at) {

    public static final Origin UNKNOWN = new Origin("", Optional.empty());

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static Origin now(String by) {
        return new Origin(by, Optional.of(Instant.now()));
    }

    public boolean isKnown() {
        return !by.isEmpty() && at.isPresent();
    }

    /** For messages, e.g. "Thillo, 2026-10-13 14:02", or a "not known" text. */
    public Text describe() {
        if (!isKnown()) {
            return Text.of("origin.unknown");
        }
        return Text.of("origin.known", "by", by, "date", DATE.format(at.get()));
    }
}
