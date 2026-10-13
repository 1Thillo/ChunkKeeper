package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupsTest {

    @TempDir
    Path folder;

    private Path chunks;

    @BeforeEach
    void setUp() {
        chunks = folder.resolve("chunks.yml");
    }

    private Backups backups(int keep) {
        return new Backups(chunks, folder.resolve("backups"), keep);
    }

    @Test
    void nothingToBackUpWithoutAFile() throws IOException {
        assertEquals(Optional.empty(), backups(5).create("test"));
        assertTrue(backups(5).list().isEmpty());
    }

    @Test
    void copiesTheFileAndListsNewestFirst() throws IOException {
        Files.writeString(chunks, "first");
        Backups backups = backups(5);
        String first = backups.create("releaseall").orElseThrow();
        Files.writeString(chunks, "second");
        String second = backups.create("import").orElseThrow();

        assertTrue(first.endsWith("_releaseall"), first);
        assertEquals(List.of(second, first), backups.list());
        assertEquals("first", Files.readString(backups.find(first).orElseThrow()));
    }

    @Test
    void keepsOnlyTheNewestBackups() throws IOException {
        Files.writeString(chunks, "data");
        Backups backups = backups(2);
        backups.create("one");
        String two = backups.create("two").orElseThrow();
        String three = backups.create("three").orElseThrow();
        assertEquals(List.of(three, two), backups.list());
    }

    @Test
    void restorePutsTheBackupInPlace() throws IOException {
        Files.writeString(chunks, "old state");
        Backups backups = backups(5);
        String name = backups.create("test").orElseThrow();
        Files.writeString(chunks, "new state");
        backups.restore(name);
        assertEquals("old state", Files.readString(chunks));
    }

    @Test
    void namesCannotLeaveTheBackupFolder() throws IOException {
        Files.writeString(chunks, "data");
        Files.writeString(folder.resolve("config.yml"), "secret");
        assertEquals(Optional.empty(), backups(5).find("../config"));
        assertEquals(Optional.empty(), backups(5).find("../chunks"));
    }
}
