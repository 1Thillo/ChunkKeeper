package de.thillo.chunkkeeper;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Switches the force-load flag of chunks on and off.
 *
 * <p>Folia splits the world into regions that tick on different threads, and a chunk may only be
 * touched from the thread that owns it. The region scheduler runs our code on exactly that thread.
 * On normal Paper the same scheduler simply runs it on the main thread, so this works on both.
 *
 * <p>A force-loaded chunk stays loaded without players nearby, and Minecraft saves that flag with
 * the world.
 */
public final class ChunkLoader {

    private final Plugin plugin;

    public ChunkLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public void keepLoaded(KeptChunk chunk) {
        setForceLoaded(chunk, true);
    }

    public void release(KeptChunk chunk) {
        setForceLoaded(chunk, false);
    }

    /**
     * Runs {@code task} on the thread that owns the chunk. Does nothing if the world is not loaded.
     *
     * @return false if the world is not loaded
     */
    public boolean runAt(KeptChunk chunk, Consumer<World> task) {
        World world = Bukkit.getWorld(chunk.world());
        if (world == null) {
            return false;
        }
        Bukkit.getRegionScheduler().execute(plugin, world, chunk.x(), chunk.z(), () -> task.accept(world));
        return true;
    }

    private void setForceLoaded(KeptChunk chunk, boolean forced) {
        runAt(chunk, world -> world.setChunkForceLoaded(chunk.x(), chunk.z(), forced));
    }
}
