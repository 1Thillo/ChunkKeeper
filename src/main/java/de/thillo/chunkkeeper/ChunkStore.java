package de.thillo.chunkkeeper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The kept chunks, saved in {@code plugins/ChunkKeeper/chunks.yml}: single chunks under
 * {@code chunks}, and named areas under {@code areas}. Both exist side by side; a chunk may be kept
 * on its own and belong to areas at the same time.
 *
 * <p>On Folia several threads can run commands at the same time, so the collections are concurrent
 * ones and saving is {@code synchronized}.
 */
public final class ChunkStore {

    private static final String CHUNKS = "chunks";
    private static final String AREAS = "areas";

    private final File file;
    private final Logger logger;
    private final Set<KeptChunk> chunks = ConcurrentHashMap.newKeySet();
    private final Map<String, NamedArea> areas = new ConcurrentHashMap<>();

    public ChunkStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public boolean fileExists() {
        return file.exists();
    }

    public void load() {
        chunks.clear();
        areas.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String line : yaml.getStringList(CHUNKS)) {
            Optional<KeptChunk> chunk = KeptChunk.parse(line, ",");
            if (chunk.isPresent()) {
                chunks.add(chunk.get());
            } else {
                logger.warning("Skipping unreadable entry in " + file.getName() + ": '" + line + "'");
            }
        }
        ConfigurationSection section = yaml.getConfigurationSection(AREAS);
        if (section != null) {
            for (String name : section.getKeys(false)) {
                loadArea(name, section.getConfigurationSection(name));
            }
        }
    }

    private void loadArea(String name, ConfigurationSection section) {
        String world = section == null ? null : section.getString("world");
        if (!NamedArea.isValidName(name) || world == null) {
            logger.warning("Skipping unreadable area '" + name + "' in " + file.getName());
            return;
        }
        Set<KeptChunk> areaChunks = new HashSet<>();
        for (String line : section.getStringList(CHUNKS)) {
            // inside an area only "x,z" is stored, the world is given once for the whole area
            Optional<KeptChunk> chunk = KeptChunk.parse(line + "," + world, ",");
            if (chunk.isPresent()) {
                areaChunks.add(chunk.get());
            } else {
                logger.warning("Skipping unreadable chunk '" + line + "' of area '" + name + "' in " + file.getName());
            }
        }
        areas.put(name, new NamedArea(name, world, areaChunks, section.getString("source", "")));
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Chunks kept loaded by ChunkKeeper. Use the /chunkkeeper commands to change them,",
                "or edit this file while the server is stopped.",
                "chunks: single chunks, one per line as x,z,world (chunk coordinates)",
                "areas:  named areas; their chunks are listed as x,z"));
        yaml.set(CHUNKS, sortedLines(chunks, true));
        for (NamedArea area : areas()) {
            String path = AREAS + "." + area.name();
            yaml.set(path + ".world", area.world());
            yaml.set(path + ".source", area.source());
            yaml.set(path + "." + CHUNKS, sortedLines(area.chunks(), false));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    private static List<String> sortedLines(Collection<KeptChunk> chunks, boolean withWorld) {
        List<String> lines = new ArrayList<>();
        for (KeptChunk chunk : chunks) {
            lines.add(withWorld ? chunk.serialize() : chunk.x() + "," + chunk.z());
        }
        lines.sort(null);
        return lines;
    }

    // --- single chunks ---

    /** @return true if the chunk is kept on its own (not counting named areas) */
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

    /** The chunks kept on their own. */
    public Set<KeptChunk> all() {
        return Set.copyOf(chunks);
    }

    public List<KeptChunk> inWorld(String world) {
        return chunks.stream().filter(chunk -> chunk.world().equals(world)).toList();
    }

    public int size() {
        return chunks.size();
    }

    public Collection<String> worlds() {
        return chunks.stream().map(KeptChunk::world).distinct().sorted().toList();
    }

    // --- named areas ---

    public Optional<NamedArea> area(String name) {
        return Optional.ofNullable(areas.get(name));
    }

    /** All named areas, sorted by name. */
    public List<NamedArea> areas() {
        return areas.values().stream().sorted(Comparator.comparing(NamedArea::name)).toList();
    }

    /** @return false if an area with that name exists already */
    public boolean addArea(NamedArea area) {
        return areas.putIfAbsent(area.name(), area) == null;
    }

    public Optional<NamedArea> removeArea(String name) {
        return Optional.ofNullable(areas.remove(name));
    }

    public List<String> areasContaining(KeptChunk chunk) {
        return areas().stream().filter(area -> area.contains(chunk)).map(NamedArea::name).toList();
    }

    // --- everything together ---

    /** True if the chunk is kept on its own or through at least one named area. */
    public boolean isKept(KeptChunk chunk) {
        if (chunks.contains(chunk)) {
            return true;
        }
        for (NamedArea area : areas.values()) {
            if (area.contains(chunk)) {
                return true;
            }
        }
        return false;
    }

    /** Every chunk that has to be force-loaded: single chunks plus the chunks of all areas. */
    public Set<KeptChunk> allKept() {
        Set<KeptChunk> result = new HashSet<>(chunks);
        for (NamedArea area : areas.values()) {
            result.addAll(area.chunks());
        }
        return result;
    }

    public List<KeptChunk> allKeptInWorld(String world) {
        return allKept().stream().filter(chunk -> chunk.world().equals(world)).toList();
    }

    public boolean hasWorld(String world) {
        return chunks.stream().anyMatch(chunk -> chunk.world().equals(world))
                || areas.values().stream().anyMatch(area -> area.world().equals(world));
    }
}
