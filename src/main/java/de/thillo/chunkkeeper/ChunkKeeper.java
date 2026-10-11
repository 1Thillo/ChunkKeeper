package de.thillo.chunkkeeper;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkKeeper extends JavaPlugin {

    private ChunkStore store;
    private ChunkLoader loader;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        store = new ChunkStore(new File(getDataFolder(), "chunks.yml"), getLogger());
        loader = new ChunkLoader(this);

        store.load();
        keepAllLoaded();

        Bukkit.getPluginManager().registerEvents(new WorldListener(store, loader), this);
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

    public ChunkStore store() {
        return store;
    }

    public ChunkLoader loader() {
        return loader;
    }
}
