package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageFileUpdaterTest {

    private static final byte[] OLD = "chunk:\n  kept: \"Keeping {count} chunks\"\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW = "chunk:\n  kept: \"Keeping {count} {count|chunk|chunks}\"\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path folder;

    @Test
    void copiesTheFileOnTheFirstStart() throws IOException {
        Properties checksums = new Properties();
        assertEquals(MessageFileUpdater.Result.COPIED, MessageFileUpdater.update(folder.resolve("en.yml"), OLD, checksums));
        assertEquals(new String(OLD, StandardCharsets.UTF_8), Files.readString(folder.resolve("en.yml")));
        assertEquals(MessageFileUpdater.Result.UP_TO_DATE, MessageFileUpdater.update(folder.resolve("en.yml"), OLD, checksums));
    }

    @Test
    void updatesACopyNobodyChanged() throws IOException {
        Properties checksums = new Properties();
        MessageFileUpdater.update(folder.resolve("en.yml"), OLD, checksums);
        assertEquals(MessageFileUpdater.Result.UPDATED, MessageFileUpdater.update(folder.resolve("en.yml"), NEW, checksums));
        assertEquals(new String(NEW, StandardCharsets.UTF_8), Files.readString(folder.resolve("en.yml")));
    }

    @Test
    void keepsChangesAndPutsTheNewTextsNextToThem() throws IOException {
        Properties checksums = new Properties();
        MessageFileUpdater.update(folder.resolve("en.yml"), OLD, checksums);
        Files.writeString(folder.resolve("en.yml"), "chunk:\n  kept: \"My own text\"\n");

        assertEquals(MessageFileUpdater.Result.KEPT_CHANGES, MessageFileUpdater.update(folder.resolve("en.yml"), NEW, checksums));
        assertEquals("chunk:\n  kept: \"My own text\"\n", Files.readString(folder.resolve("en.yml")));
        assertEquals(new String(NEW, StandardCharsets.UTF_8), Files.readString(folder.resolve("en.yml.new")));
    }

    @Test
    void doesNotTouchAChangedFileWithoutChecksum() throws IOException {
        Files.writeString(folder.resolve("en.yml"), "chunk:\n  kept: \"My own text\"\n");
        assertEquals(MessageFileUpdater.Result.KEPT_CHANGES, MessageFileUpdater.update(folder.resolve("en.yml"), NEW, new Properties()));
        assertFalse(Files.readString(folder.resolve("en.yml")).contains("{count|"));
    }

    /** Servers that ran 1.2.0 have no checksums yet; their unchanged copies must still be recognised. */
    @Test
    void recognisesTheFilesOf120() throws IOException {
        for (String language : new String[] {"en", "de"}) {
            try (InputStream in = getClass().getResourceAsStream("/messages-1.2.0/" + language + ".yml")) {
                Files.write(folder.resolve(language + ".yml"), in.readAllBytes());
            }
            assertEquals(MessageFileUpdater.Result.UPDATED,
                    MessageFileUpdater.update(folder.resolve(language + ".yml"), NEW, new Properties()), language);
        }
    }
}
