package de.thillo.chunkkeeper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Runs a {@link ForceLoadCheck} every few minutes, because other plugins or
 * {@code /forceload remove all} can remove the force-load flag of chunks that ChunkKeeper keeps.
 *
 * <p>Everything runs on the global region thread, the only place where Folia allows reading and
 * changing these flags. A server can keep many thousands of chunks, so one walk is spread over
 * several ticks and never holds up a single tick for long.
 *
 * <p>A chunk added a moment ago may still wait for its flag and then counts as repaired too. That
 * only makes the number in the log a little too high; the flag itself is right either way.
 */
public final class ForceLoadRepair {

    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final int CHUNKS_PER_TICK = 200;

    private final Plugin plugin;
    private final ChunkStore store;
    private ScheduledTask timer;
    // volatile: /chunkkeeper reload can stop a walk from another thread (Folia)
    private volatile ForceLoadCheck running;

    public ForceLoadRepair(Plugin plugin, ChunkStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Starts checking every {@code minutes} minutes and ends an earlier schedule. 0 means no checks. */
    public synchronized void start(int minutes) {
        stop();
        if (minutes <= 0) {
            return;
        }
        long period = minutes * TICKS_PER_MINUTE;
        timer = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> beginWalk(), period, period);
    }

    public synchronized void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        running = null;
    }

    private void beginWalk() {
        if (running != null) {
            return; // the last walk is not finished yet
        }
        ForceLoadCheck check = new ForceLoadCheck(store.allKept(), store::isKept, new ServerFlags());
        running = check;
        continueWalk(check);
    }

    private void continueWalk(ForceLoadCheck check) {
        if (running != check) {
            return; // stopped or restarted in the meantime
        }
        if (check.runBatch(CHUNKS_PER_TICK)) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> continueWalk(check), 1L);
            return;
        }
        running = null;
        if (check.repaired() > 0) {
            plugin.getLogger().warning("Force-load flags were missing on kept chunks, repaired: " + check.repaired()
                    + " (removed by something else, for example /forceload remove all).");
        }
    }

    /** The real flags of the server. Only use on the global region thread. */
    private static final class ServerFlags implements ForceLoadCheck.Flags {

        @Override
        public boolean isMissing(KeptChunk chunk) {
            World world = Bukkit.getWorld(chunk.world());
            return world != null && !world.isChunkForceLoaded(chunk.x(), chunk.z());
        }

        @Override
        public void setAgain(KeptChunk chunk) {
            World world = Bukkit.getWorld(chunk.world());
            if (world != null) {
                world.setChunkForceLoaded(chunk.x(), chunk.z(), true);
            }
        }
    }
}
