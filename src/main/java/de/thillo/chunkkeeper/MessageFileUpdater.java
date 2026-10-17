package de.thillo.chunkkeeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;

/**
 * Keeps the copies of the built-in message files in {@code plugins/ChunkKeeper/messages/} up to
 * date, without overwriting texts a server owner changed.
 *
 * <p>For every copy it writes, it notes a checksum (a fingerprint of the content) in
 * {@code messages/.checksums.properties}. If the file still has that checksum when a newer version
 * starts, nobody edited it and it gets the new texts. If it was edited, it stays as it is and the
 * new texts are put next to it as {@code en.yml.new}.
 */
public final class MessageFileUpdater {

    /** The files of ChunkKeeper 1.2.0, which did not write checksums yet. */
    private static final Set<String> EARLIER_RELEASES = Set.of(
            "83b0dae61f50247e0fcca2ac18b471e5c5b69b331bcdaa0a8f4509e0d7e8073d", // en.yml 1.2.0
            "9045468fb1d017b36a27258532ca7b47e06efad77bf4c2e200e67d84660f6c72"); // de.yml 1.2.0

    public enum Result {
        /** There was no file yet. */
        COPIED,
        /** The file already has the texts of this version. */
        UP_TO_DATE,
        /** The file was unchanged and now has the texts of this version. */
        UPDATED,
        /** The file has changes by the server owner; the new texts are in a .new file next to it. */
        KEPT_CHANGES
    }

    private MessageFileUpdater() {
    }

    /**
     * @param file      e.g. plugins/ChunkKeeper/messages/de.yml
     * @param builtIn   the content of the same file inside the jar
     * @param checksums what was written before; updated by this method, the caller saves it
     */
    public static Result update(Path file, byte[] builtIn, Properties checksums) throws IOException {
        String name = file.getFileName().toString();
        String builtInChecksum = checksum(builtIn);
        if (!Files.exists(file)) {
            Files.write(file, builtIn);
            checksums.setProperty(name, builtInChecksum);
            return Result.COPIED;
        }
        String current = checksum(Files.readAllBytes(file));
        if (current.equals(builtInChecksum)) {
            checksums.setProperty(name, builtInChecksum);
            return Result.UP_TO_DATE;
        }
        if (current.equals(checksums.getProperty(name)) || EARLIER_RELEASES.contains(current)) {
            Files.write(file, builtIn);
            checksums.setProperty(name, builtInChecksum);
            return Result.UPDATED;
        }
        Files.write(file.resolveSibling(name + ".new"), builtIn);
        return Result.KEPT_CHANGES;
    }

    static String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }
}
