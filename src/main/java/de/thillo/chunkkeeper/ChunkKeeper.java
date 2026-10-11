package de.thillo.chunkkeeper;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkKeeper extends JavaPlugin {

    @Override
    public void onEnable() {
        registerCommand("chunkkeeper", "Keep chunks loaded.", List.of("ck"), new ChunkKeeperCommand(this));
    }
}
