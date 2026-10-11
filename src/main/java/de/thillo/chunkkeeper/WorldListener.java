package de.thillo.chunkkeeper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Force-loads the kept chunks of worlds that are loaded after the server start, e.g. by Multiverse. */
final class WorldListener implements Listener {

    private final ChunkStore store;
    private final ChunkLoader loader;

    WorldListener(ChunkStore store, ChunkLoader loader) {
        this.store = store;
        this.loader = loader;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (KeptChunk chunk : store.inWorld(event.getWorld().getName())) {
            loader.keepLoaded(chunk);
        }
    }
}
