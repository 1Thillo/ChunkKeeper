package de.thillo.chunkkeeper;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Switches the force-load flag of chunks on and off.
 *
 * <p>Folia runs the world on several threads. The list of force-loaded chunks belongs to the
 * "global region" thread, so we hand that work to the global region scheduler. Code that touches
 * blocks of a chunk has to run on the thread of that chunk's region instead, see {@link #runAt}.
 * On normal Paper both schedulers simply run the code on the main thread, so this works on both.
 *
 * <p>A force-loaded chunk stays loaded without players nearby, and Minecraft saves that flag with
 * the world.
 */
public final class ChunkLoader {

    private final Plugin plugin;

    public ChunkLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public void keepLoaded(Collection<KeptChunk> chunks) {
        setForceLoaded(chunks, true);
    }

    public void release(Collection<KeptChunk> chunks) {
        setForceLoaded(chunks, false);
    }

    /** Runs {@code task} on the global region thread, where force-load flags may be read and changed. */
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * Runs {@code task} on the thread that owns the chunk, where its blocks may be read.
     *
     * @return false if the world is not loaded; the task does not run then
     */
    public boolean runAt(KeptChunk chunk, Consumer<World> task) {
        World world = Bukkit.getWorld(chunk.world());
        if (world == null) {
            return false;
        }
        Bukkit.getRegionScheduler().execute(plugin, world, chunk.x(), chunk.z(), () -> task.accept(world));
        return true;
    }

    private void setForceLoaded(Collection<KeptChunk> chunks, boolean forced) {
        if (chunks.isEmpty()) {
            return;
        }
        // Copy first: the caller may change its collection before the task runs.
        List<KeptChunk> copy = List.copyOf(chunks);
        runGlobal(() -> {
            for (KeptChunk chunk : copy) {
                World world = Bukkit.getWorld(chunk.world());
                if (world != null) {
                    world.setChunkForceLoaded(chunk.x(), chunk.z(), forced);
                }
            }
        });
    }
}
