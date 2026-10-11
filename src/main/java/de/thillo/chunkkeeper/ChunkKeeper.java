package de.thillo.chunkkeeper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import de.thillo.chunkkeeper.command.ChunkKeeperCommand;
import de.thillo.chunkkeeper.importer.KeepChunksImporter;

public final class ChunkKeeper extends JavaPlugin {

    private ChunkStore store;
    private ChunkLoader loader;
    // volatile: /chunkkeeper reload replaces it while other threads may be reading it (Folia)
    private volatile Settings settings;
    private Permissions permissions;
    private Keeper keeper;
    private KeepChunksImporter importer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new ChunkStore(new File(getDataFolder(), "chunks.yml"), getLogger());
        loader = new ChunkLoader(this);
        keeper = new Keeper(store, loader);
        permissions = new Permissions(this);
        importer = new KeepChunksImporter(getDataFolder().getParentFile());

        // No chunks.yml yet means this is the first start: take over what KeepChunks had.
        Optional<KeepChunksImporter.Result> imported = store.fileExists() ? Optional.empty() : importer.read();
        imported.flatMap(KeepChunksImporter.Result::releaseAllProtection).ifPresent(protection -> {
            getConfig().set("release-all-console-only", protection);
            saveConfig();
        });
        settings = Settings.from(getConfig());

        store.load();
        keepAllLoaded();
        imported.ifPresent(this::finishImport);
        if (!store.fileExists()) {
            store.save();
        }
        warnIfKeepChunksIsInstalled();

        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        registerCommand("chunkkeeper", "Keep chunks loaded.", List.of("keepchunks", "chk"), new ChunkKeeperCommand(this));
    }

    private void finishImport(KeepChunksImporter.Result result) {
        int added = keeper.keep(result.chunks());
        getLogger().info("First start: imported " + added + " chunks from " + importer.dataFile().getPath() + ".");
        if (!result.unreadable().isEmpty()) {
            getLogger().warning("Could not read these KeepChunks entries: " + result.unreadable());
        }
    }

    private void warnIfKeepChunksIsInstalled() {
        if (Bukkit.getPluginManager().getPlugin("KeepChunks") != null) {
            getLogger().warning("KeepChunks is still installed. ChunkKeeper has taken over its chunks; "
                    + "please remove KeepChunks so the two plugins do not manage the same chunks. "
                    + "Use /chunkkeeper import to pick up chunks KeepChunks added in the meantime.");
        }
    }

    /** Force-loads every kept chunk whose world is currently loaded. */
    void keepAllLoaded() {
        List<KeptChunk> loadable = new ArrayList<>();
        int missingWorlds = 0;
        for (KeptChunk chunk : store.all()) {
            if (Bukkit.getWorld(chunk.world()) == null) {
                missingWorlds++;
            } else {
                loadable.add(chunk);
            }
        }
        loader.keepLoaded(loadable);
        getLogger().info("Keeping " + loadable.size() + " chunks loaded.");
        if (missingWorlds > 0) {
            getLogger().info(missingWorlds + " chunks belong to worlds that are not loaded yet; they follow when their world loads.");
        }
    }

    /**
     * Reads config.yml and chunks.yml again. Chunks that were removed from the file are released,
     * all others are force-loaded (again).
     */
    public void reload() {
        reloadConfig();
        settings = Settings.from(getConfig());

        Set<KeptChunk> before = store.all();
        store.load();
        List<KeptChunk> removed = before.stream().filter(chunk -> !store.contains(chunk)).toList();
        loader.release(removed);
        keepAllLoaded();
    }

    public ChunkStore store() {
        return store;
    }

    public ChunkLoader loader() {
        return loader;
    }

    public Settings settings() {
        return settings;
    }

    public Permissions permissions() {
        return permissions;
    }

    public Keeper keeper() {
        return keeper;
    }

    public KeepChunksImporter importer() {
        return importer;
    }
}
