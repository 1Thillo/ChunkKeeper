package de.thillo.chunkkeeper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remembers every change in {@code plugins/ChunkKeeper/history.log}, one line per change:
 * when, who and what. Unlike the origin stored with a chunk, this also keeps releases, so it
 * shows who removed something.
 */
public final class History {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path file;
    private final Logger logger;

    public History(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Adds a line like "2026-10-13 14:02:11 | Thillo | kept chunk (3, 4) in 'world'". */
    public synchronized void record(String who, String what) {
        String line = TIME.format(Instant.now()) + " | " + who + " | " + what + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not write to " + file.getFileName(), e);
        }
    }

    /** All recorded lines, newest first. */
    public synchronized List<String> newestFirst() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            lines.removeIf(String::isBlank);
            Collections.reverse(lines);
            return lines;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file.getFileName(), e);
            return List.of();
        }
    }
}
