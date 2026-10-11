package de.thillo.chunkkeeper.importer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.configuration.file.YamlConfiguration;

import de.thillo.chunkkeeper.KeptChunk;

/**
 * Reads the data of the old KeepChunks plugin, so a server can switch without losing its chunks.
 *
 * <p>KeepChunks stores every kept chunk in {@code plugins/KeepChunks/data.yml} under {@code chunks}
 * as text like {@code "12#-5#world"}. Regions and rails are already split into single chunks there.
 * Its only setting that matters here is {@code general.releaseallprotection} in config.yml.
 *
 * <p>The KeepChunks files are only read, never changed.
 */
public final class KeepChunksImporter {

    /** What was found in the KeepChunks folder. */
    public record Result(List<KeptChunk> chunks, List<String> unreadable, Optional<Boolean> releaseAllProtection) {
    }

    private final File folder;

    /** @param pluginsFolder the server's plugins folder */
    public KeepChunksImporter(File pluginsFolder) {
        this.folder = new File(pluginsFolder, "KeepChunks");
    }

    public File dataFile() {
        return new File(folder, "data.yml");
    }

    /** @return empty if there is no KeepChunks data.yml */
    public Optional<Result> read() {
        File data = dataFile();
        if (!data.isFile()) {
            return Optional.empty();
        }
        List<KeptChunk> chunks = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (String line : YamlConfiguration.loadConfiguration(data).getStringList("chunks")) {
            Optional<KeptChunk> chunk = KeptChunk.parse(line, "#");
            if (chunk.isPresent()) {
                chunks.add(chunk.get());
            } else {
                unreadable.add(line);
            }
        }

        Optional<Boolean> releaseAllProtection = Optional.empty();
        File config = new File(folder, "config.yml");
        if (config.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
            if (yaml.isBoolean("general.releaseallprotection")) {
                releaseAllProtection = Optional.of(yaml.getBoolean("general.releaseallprotection"));
            }
        }
        return Optional.of(new Result(chunks, unreadable, releaseAllProtection));
    }
}
