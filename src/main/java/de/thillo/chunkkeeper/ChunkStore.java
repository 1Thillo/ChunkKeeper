package de.thillo.chunkkeeper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The list of kept chunks, saved in {@code plugins/ChunkKeeper/chunks.yml}.
 *
 * <p>On Folia several threads can run commands at the same time, so the set is a concurrent one
 * and saving is {@code synchronized}.
 */
public final class ChunkStore {

    private static final String KEY = "chunks";

    private final File file;
    private final Logger logger;
    private final Set<KeptChunk> chunks = ConcurrentHashMap.newKeySet();

    public ChunkStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public boolean fileExists() {
        return file.exists();
    }

    public void load() {
        chunks.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String line : yaml.getStringList(KEY)) {
            Optional<KeptChunk> chunk = KeptChunk.parse(line, ",");
            if (chunk.isPresent()) {
                chunks.add(chunk.get());
            } else {
                logger.warning("Skipping unreadable entry in " + file.getName() + ": '" + line + "'");
            }
        }
    }

    public synchronized void save() {
        List<String> lines = new ArrayList<>();
        for (KeptChunk chunk : chunks) {
            lines.add(chunk.serialize());
        }
        lines.sort(null);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Chunks kept loaded by ChunkKeeper, one per line as x,z,world (chunk coordinates).",
                "Use the /chunkkeeper commands to change this list, or edit it while the server is stopped."));
        yaml.set(KEY, lines);
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    public boolean contains(KeptChunk chunk) {
        return chunks.contains(chunk);
    }

    /** @return true if the chunk was new */
    public boolean add(KeptChunk chunk) {
        return chunks.add(chunk);
    }

    /** @return true if the chunk was in the list */
    public boolean remove(KeptChunk chunk) {
        return chunks.remove(chunk);
    }

    public Set<KeptChunk> all() {
        return Set.copyOf(chunks);
    }

    public List<KeptChunk> inWorld(String world) {
        return chunks.stream().filter(chunk -> chunk.world().equals(world)).toList();
    }

    public boolean hasWorld(String world) {
        return chunks.stream().anyMatch(chunk -> chunk.world().equals(world));
    }

    public int size() {
        return chunks.size();
    }

    public void clear() {
        chunks.clear();
    }

    public Collection<String> worlds() {
        return chunks.stream().map(KeptChunk::world).distinct().sorted().toList();
    }
}
