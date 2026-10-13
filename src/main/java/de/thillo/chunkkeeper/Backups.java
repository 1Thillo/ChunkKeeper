package de.thillo.chunkkeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies of chunks.yml in {@code plugins/ChunkKeeper/backups/}, named after the time and the reason,
 * e.g. {@code 2026-10-13_23-40-12-01_releaseall.yml}. Only the newest {@code keep} copies are kept.
 */
public final class Backups {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    /** Backup names only use these characters, so a name typed in a command cannot leave the folder. */
    private static final Pattern VALID_NAME = Pattern.compile("[0-9a-z_-]+");

    private final Path chunksFile;
    private final Path folder;
    private final int keep;

    public Backups(Path chunksFile, Path folder, int keep) {
        this.chunksFile = chunksFile;
        this.folder = folder;
        this.keep = Math.max(1, keep);
    }

    /**
     * Copies the current chunks.yml.
     *
     * @param reason a short word for the name, e.g. "releaseall"
     * @return the name of the backup, or empty if there is no chunks.yml yet
     */
    public Optional<String> create(String reason) throws IOException {
        if (!Files.exists(chunksFile)) {
            return Optional.empty();
        }
        Files.createDirectories(folder);
        // The number after the time keeps backups of the same second in order, because the list is
        // sorted by name: 2026-10-13_23-40-12-01_releaseall comes before ..._23-40-12-02_import.
        String stamp = STAMP.format(LocalDateTime.now());
        List<String> existing = list();
        int number = 1;
        while (number < 99 && startsWith(existing, stamp + "-" + String.format("%02d", number) + "_")) {
            number++;
        }
        String name = stamp + "-" + String.format("%02d", number) + "_" + reason.toLowerCase().replaceAll("[^0-9a-z-]", "-");
        Files.copy(chunksFile, file(name), StandardCopyOption.REPLACE_EXISTING);
        prune();
        return Optional.of(name);
    }

    /** Backup names, newest first. */
    public List<String> list() throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .map(name -> name.substring(0, name.length() - ".yml".length()))
                    .filter(name -> VALID_NAME.matcher(name).matches())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
    }

    /** The backup file, if a backup with this name exists. */
    public Optional<Path> find(String name) {
        if (!VALID_NAME.matcher(name).matches() || !Files.isRegularFile(file(name))) {
            return Optional.empty();
        }
        return Optional.of(file(name));
    }

    /** Puts a backup in place of chunks.yml. Make a backup of the current state first. */
    public void restore(String name) throws IOException {
        Path backup = find(name).orElseThrow(() -> new IOException("No backup called " + name));
        Files.copy(backup, chunksFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void prune() throws IOException {
        List<String> names = list();
        for (String old : names.subList(Math.min(keep, names.size()), names.size())) {
            Files.deleteIfExists(file(old));
        }
    }

    private static boolean startsWith(List<String> names, String prefix) {
        return names.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private Path file(String name) {
        return folder.resolve(name + ".yml");
    }
}
