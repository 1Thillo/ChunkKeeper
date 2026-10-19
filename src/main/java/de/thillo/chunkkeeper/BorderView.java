package de.thillo.chunkkeeper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * {@code /chunkkeeper show}: draws the outline of the kept chunks around a player with particles,
 * twice a second for {@link #SECONDS} seconds: lines on two heights and a post at every corner.
 * Only that player sees them.
 *
 * <p>The task runs on the player's own scheduler. On Folia that is the thread of the region the
 * player is in, which is where particles for that player have to be sent; on Paper it is the main
 * thread. The kept chunks are only read from the store, which is safe from any thread.
 */
public final class BorderView {

    public static final int SECONDS = 30;
    /** Drawn twice a second: dust particles fade after about half a second, so the lines do not flicker. */
    private static final long PERIOD_TICKS = 10;
    /** At most this many particles per player and drawing, so a huge area cannot flood the client. */
    private static final int PARTICLE_BUDGET = 2000;
    /** Corner posts reach from a little below the player's feet to this many blocks above them. */
    private static final double POST_HEIGHT = 4;
    // Yellow and magenta stand out against grass, water, sky and stone; green and blue did not.
    private static final Particle.DustOptions SINGLE = new Particle.DustOptions(Color.fromRGB(0xFF, 0xE0, 0x10), 2.0f);
    private static final Particle.DustOptions AREA = new Particle.DustOptions(Color.fromRGB(0xFF, 0x30, 0xF0), 2.0f);

    private final ChunkKeeper plugin;
    private final Map<UUID, ScheduledTask> running = new ConcurrentHashMap<>();

    public BorderView(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    public boolean isShowing(Player player) {
        return running.containsKey(player.getUniqueId());
    }

    /**
     * Starts showing the kept chunks within {@code radius} chunks, or starts over if it is already running.
     *
     * @return false if the player is no longer online
     */
    public boolean start(Player player, int radius) {
        stop(player);
        UUID id = player.getUniqueId();
        AtomicInteger drawings = new AtomicInteger();
        long total = SECONDS * 20L / PERIOD_TICKS;
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, self -> {
            if (drawings.incrementAndGet() > total) {
                self.cancel();
                running.remove(id, self);
                Messages.info(player, "show.ended");
                return;
            }
            draw(player, radius);
        }, () -> running.remove(id), 1L, PERIOD_TICKS);
        if (task == null) {
            return false;
        }
        running.put(id, task);
        return true;
    }

    /** @return false if nothing was being shown */
    public boolean stop(Player player) {
        ScheduledTask task = running.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        return task != null;
    }

    /** Stops everything, for example when the plugin is disabled or reloaded. */
    public void stopAll() {
        running.values().forEach(ScheduledTask::cancel);
        running.clear();
    }

    /** How many kept chunks there are within {@code radius} chunks of the player. */
    public long keptNearby(Player player, int radius) {
        KeptChunk center = centre(player);
        return plugin.store().allKeptInWorld(center.world()).stream().filter(chunk -> isNear(chunk, center, radius)).count();
    }

    private void draw(Player player, int radius) {
        KeptChunk center = centre(player);
        String world = center.world();

        record Line(ChunkBorders.Edge edge, Particle.DustOptions colour) {
        }
        List<Line> lines = new ArrayList<>();
        for (ChunkBorders.Edge edge : ChunkBorders.of(plugin.store().inWorld(world))) {
            lines.add(new Line(edge, SINGLE));
        }
        for (NamedArea area : plugin.store().areas()) {
            if (area.world().equals(world)) {
                for (ChunkBorders.Edge edge : ChunkBorders.of(area.chunks())) {
                    lines.add(new Line(edge, AREA));
                }
            }
        }

        Location at = player.getLocation();
        // nearest edges first, so the budget is spent where the player looks
        List<Line> near = lines.stream()
                .filter(line -> isNear(line.edge().chunk(), center, radius))
                .sorted(Comparator.comparingDouble(line -> distance(line.edge(), at)))
                .toList();

        int budget = PARTICLE_BUDGET;
        Set<Long> posts = new HashSet<>();
        double[] heights = {at.getY() + 0.3, at.getY() + 2.3};
        for (Line line : near) {
            ChunkBorders.Edge edge = line.edge();
            int dx = Integer.signum(edge.x2() - edge.x1());
            int dz = Integer.signum(edge.z2() - edge.z1());
            // one particle per block along the edge, on two heights
            for (int step = 0; step <= 16 && budget > 0; step++) {
                for (double y : heights) {
                    budget -= spawn(player, edge.x1() + dx * step, y, edge.z1() + dz * step, line.colour());
                }
            }
            // a post at both ends; corners shared by two edges get only one
            budget -= post(player, posts, edge.x1(), edge.z1(), at.getY(), line.colour(), budget);
            budget -= post(player, posts, edge.x2(), edge.z2(), at.getY(), line.colour(), budget);
            if (budget <= 0) {
                break;
            }
        }
    }

    /** A vertical line at a corner, unless that corner already has one. @return how many particles were sent */
    private static int post(Player player, Set<Long> posts, int x, int z, double feet, Particle.DustOptions colour, int budget) {
        // x and z packed into one number, so the set can tell corners apart
        if (budget <= 0 || !posts.add(((long) x << 32) | (z & 0xFFFFFFFFL))) {
            return 0;
        }
        int sent = 0;
        for (double y = feet - 1; y <= feet + POST_HEIGHT && sent < budget; y += 0.5) {
            sent += spawn(player, x, y, z, colour);
        }
        return sent;
    }

    /** @return how many particles were sent, for the budget */
    private static int spawn(Player player, double x, double y, double z, Particle.DustOptions colour) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, colour);
        return 1;
    }

    private static KeptChunk centre(Player player) {
        Location at = player.getLocation();
        return KeptChunk.atBlock(player.getWorld().getName(), at.getBlockX(), at.getBlockZ());
    }

    private static boolean isNear(KeptChunk chunk, KeptChunk center, int radius) {
        return Math.abs(chunk.x() - center.x()) <= radius && Math.abs(chunk.z() - center.z()) <= radius;
    }

    private static double distance(ChunkBorders.Edge edge, Location at) {
        double middleX = (edge.x1() + edge.x2()) / 2.0;
        double middleZ = (edge.z1() + edge.z2()) / 2.0;
        return Math.hypot(middleX - at.getX(), middleZ - at.getZ());
    }
}
