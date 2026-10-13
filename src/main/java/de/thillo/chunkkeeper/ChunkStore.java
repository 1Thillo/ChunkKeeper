package de.thillo.chunkkeeper;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    /** single chunks and who added them */
    private final Map<KeptChunk, Origin> chunks = new ConcurrentHashMap<>();
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
        for (Object entry : yaml.getList(CHUNKS, List.of())) {
            loadChunk(entry);
        }
        ConfigurationSection section = yaml.getConfigurationSection(AREAS);
        if (section != null) {
            for (String name : section.getKeys(false)) {
                loadArea(name, section.getConfigurationSection(name));
            }
        }
    }

    /**
     * An entry is either just "x,z,world" (files from before 1.2.0) or a small map with the chunk and
     * who added it when: {@code {chunk: "x,z,world", by: Thillo, at: "2026-10-13T12:00:00Z"}}.
     */
    private void loadChunk(Object entry) {
        String line;
        Origin origin = Origin.UNKNOWN;
        if (entry instanceof Map<?, ?> map) {
            line = String.valueOf(map.get("chunk"));
            origin = readOrigin(map.get("by"), map.get("at"));
        } else {
            line = String.valueOf(entry);
        }
        Optional<KeptChunk> chunk = KeptChunk.parse(line, ",");
        if (chunk.isPresent()) {
            chunks.put(chunk.get(), origin);
        } else {
            logger.warning("Skipping unreadable entry in " + file.getName() + ": '" + entry + "'");
        }
    }

    private static Origin readOrigin(Object by, Object at) {
        if (by == null || at == null) {
            return Origin.UNKNOWN;
        }
        try {
            return new Origin(String.valueOf(by), Optional.of(Instant.parse(String.valueOf(at))));
        } catch (DateTimeParseException e) {
            return new Origin(String.valueOf(by), Optional.empty());
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
        Origin origin = readOrigin(section.get("by"), section.get("at"));
        areas.put(name, new NamedArea(name, world, areaChunks, section.getString("source", ""), origin));
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Chunks kept loaded by ChunkKeeper. Use the /chunkkeeper commands to change them,",
                "or edit this file while the server is stopped.",
                "chunks: single chunks as x,z,world (chunk coordinates), with who added them and when",
                "areas:  named areas; their chunks are listed as x,z"));
        yaml.set(CHUNKS, singleChunkEntries());
        for (NamedArea area : areas()) {
            String path = AREAS + "." + area.name();
            yaml.set(path + ".world", area.world());
            yaml.set(path + ".source", area.source());
            if (area.origin().isKnown()) {
                yaml.set(path + ".by", area.origin().by());
                yaml.set(path + ".at", area.origin().at().get().toString());
            }
            yaml.set(path + "." + CHUNKS, sortedLines(area.chunks()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    private List<Object> singleChunkEntries() {
        List<KeptChunk> sorted = new ArrayList<>(chunks.keySet());
        sorted.sort(Comparator.comparing(KeptChunk::serialize));
        List<Object> entries = new ArrayList<>();
        for (KeptChunk chunk : sorted) {
            Origin origin = chunks.getOrDefault(chunk, Origin.UNKNOWN);
            if (origin.isKnown()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("chunk", chunk.serialize());
                entry.put("by", origin.by());
                entry.put("at", origin.at().get().toString());
                entries.add(entry);
            } else {
                entries.add(chunk.serialize());
            }
        }
        return entries;
    }

    private static List<String> sortedLines(Collection<KeptChunk> chunks) {
        List<String> lines = new ArrayList<>();
        for (KeptChunk chunk : chunks) {
            lines.add(chunk.x() + "," + chunk.z());
        }
        lines.sort(null);
        return lines;
    }

    // --- single chunks ---

    /** @return true if the chunk is kept on its own (not counting named areas) */
    public boolean contains(KeptChunk chunk) {
        return chunks.containsKey(chunk);
    }

    /** @return true if the chunk was new; a chunk that is kept already keeps its first origin */
    public boolean add(KeptChunk chunk, Origin origin) {
        return chunks.putIfAbsent(chunk, origin) == null;
    }

    /** @return true if the chunk was in the list */
    public boolean remove(KeptChunk chunk) {
        return chunks.remove(chunk) != null;
    }

    /** Who added a single chunk, if it is kept on its own. */
    public Optional<Origin> origin(KeptChunk chunk) {
        return Optional.ofNullable(chunks.get(chunk));
    }

    /** The chunks kept on their own. */
    public Set<KeptChunk> all() {
        return Set.copyOf(chunks.keySet());
    }

    public List<KeptChunk> inWorld(String world) {
        return chunks.keySet().stream().filter(chunk -> chunk.world().equals(world)).toList();
    }

    public int size() {
        return chunks.size();
    }

    public Collection<String> worlds() {
        return chunks.keySet().stream().map(KeptChunk::world).distinct().sorted().toList();
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
        if (chunks.containsKey(chunk)) {
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
        Set<KeptChunk> result = new HashSet<>(chunks.keySet());
        for (NamedArea area : areas.values()) {
            result.addAll(area.chunks());
        }
        return result;
    }

    public List<KeptChunk> allKeptInWorld(String world) {
        return allKept().stream().filter(chunk -> chunk.world().equals(world)).toList();
    }

    public boolean hasWorld(String world) {
        return chunks.keySet().stream().anyMatch(chunk -> chunk.world().equals(world))
                || areas.values().stream().anyMatch(area -> area.world().equals(world));
    }
}
