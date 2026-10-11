package de.thillo.chunkkeeper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Force-loads the kept chunks of worlds that are loaded after the server start (e.g. by Multiverse),
 * and stops worlds with kept chunks from being unloaded.
 */
final class WorldListener implements Listener {

    private final ChunkKeeper plugin;

    WorldListener(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        for (KeptChunk chunk : plugin.store().inWorld(event.getWorld().getName())) {
            plugin.loader().keepLoaded(chunk);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        String world = event.getWorld().getName();
        if (plugin.settings().preventWorldUnload() && plugin.store().hasWorld(world)) {
            event.setCancelled(true);
            plugin.getLogger().info("Kept world '" + world + "' from unloading because it has kept chunks.");
        }
    }
}
