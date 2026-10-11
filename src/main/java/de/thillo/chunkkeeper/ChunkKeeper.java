package de.thillo.chunkkeeper;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import de.thillo.chunkkeeper.command.ChunkKeeperCommand;

public final class ChunkKeeper extends JavaPlugin {

    private ChunkStore store;
    private ChunkLoader loader;
    // volatile: /chunkkeeper reload replaces it while other threads may be reading it (Folia)
    private volatile Settings settings;
    private Permissions permissions;
    private Keeper keeper;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());
        store = new ChunkStore(new File(getDataFolder(), "chunks.yml"), getLogger());
        loader = new ChunkLoader(this);
        keeper = new Keeper(store, loader);
        permissions = new Permissions(this);

        store.load();
        keepAllLoaded();

        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        registerCommand("chunkkeeper", "Keep chunks loaded.", List.of("ck"), new ChunkKeeperCommand(this));
    }

    /** Force-loads every kept chunk whose world is currently loaded. */
    void keepAllLoaded() {
        int missingWorlds = 0;
        for (KeptChunk chunk : store.all()) {
            if (Bukkit.getWorld(chunk.world()) == null) {
                missingWorlds++;
            } else {
                loader.keepLoaded(chunk);
            }
        }
        getLogger().info("Keeping " + (store.size() - missingWorlds) + " chunks loaded.");
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
        for (KeptChunk chunk : before) {
            if (!store.contains(chunk)) {
                loader.release(chunk);
            }
        }
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
}
