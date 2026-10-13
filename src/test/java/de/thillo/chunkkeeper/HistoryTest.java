package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryTest {

    @TempDir
    Path folder;

    @Test
    void emptyWithoutAFile() {
        assertTrue(new History(folder.resolve("history.log"), Logger.getAnonymousLogger()).newestFirst().isEmpty());
    }

    @Test
    void showsTheNewestLineFirst() {
        History history = new History(folder.resolve("history.log"), Logger.getAnonymousLogger());
        history.record("Thillo", "kept chunk (1, 2) in 'world'");
        history.record("Suiii", "released area 'halloween' (140 chunks)");

        List<String> lines = history.newestFirst();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith(" | Suiii | released area 'halloween' (140 chunks)"), lines.get(0));
        assertTrue(lines.get(1).endsWith(" | Thillo | kept chunk (1, 2) in 'world'"), lines.get(1));
    }
}
