package de.thillo.chunkkeeper.rail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import de.thillo.chunkkeeper.KeptChunk;

/**
 * Follows a railway from one rail block and collects the chunks it runs through.
 *
 * <p>The search works chunk by chunk. Reading blocks is only allowed on the thread that owns them
 * (Folia), so for every chunk we load it without generating anything new, switch to its region
 * thread, follow the rails inside it and hand the rails that continue in neighbouring chunks over
 * to those chunks. Several chunks can be searched at the same time, which is why the shared state
 * uses concurrent sets and atomic counters. When no chunk is waiting any more, {@code onDone} runs.
 */
public final class RailTracer {

    /** A block position. */
    private record Pos(int x, int y, int z) {
        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }

    /**
     * The outcome of a search.
     *
     * @param chunks    every chunk with a rail in it, plus its eight neighbours
     * @param startFound false if there was no rail at the start position
     * @param limitHit  true if the search stopped early because of rail-search-limit
     */
    public record Result(int rails, Set<KeptChunk> chunks, boolean startFound, boolean limitHit) {
    }

    private final Plugin plugin;
    private final World world;
    private final int limit;
    private final Consumer<Result> onDone;

    private final Set<Pos> visited = ConcurrentHashMap.newKeySet();
    private final Set<KeptChunk> railChunks = ConcurrentHashMap.newKeySet();
    private final AtomicInteger rails = new AtomicInteger();
    private final AtomicInteger waitingChunks = new AtomicInteger();
    private final AtomicBoolean limitHit = new AtomicBoolean();

    public RailTracer(Plugin plugin, World world, int limit, Consumer<Result> onDone) {
        this.plugin = plugin;
        this.world = world;
        this.limit = limit;
        this.onDone = onDone;
    }

    /** Starts the search at a block position. Returns right away; the result arrives in {@code onDone}. */
    public void start(int x, int y, int z) {
        Pos start = new Pos(x, y, z);
        searchChunk(start.chunkX(), start.chunkZ(), List.of(start));
    }

    private void searchChunk(int chunkX, int chunkZ, List<Pos> positions) {
        waitingChunks.incrementAndGet();
        // false = do not generate missing chunks; a railway cannot run through terrain that does not exist
        world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, error) -> {
            if (chunk == null) {
                finishChunk();
                return;
            }
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
                try {
                    followRails(chunkX, chunkZ, positions);
                } finally {
                    finishChunk();
                }
            });
        });
    }

    /** Runs on the chunk's own thread: follows all rails inside this chunk. */
    private void followRails(int chunkX, int chunkZ, List<Pos> positions) {
        Queue<Pos> queue = new ArrayDeque<>(positions);
        Map<Long, List<Pos>> continuesElsewhere = new HashMap<>();

        while (!queue.isEmpty() && !limitHit.get()) {
            Pos pos = queue.poll();
            if (!isRail(pos) || !visited.add(pos)) {
                continue;
            }
            if (rails.incrementAndGet() >= limit) {
                limitHit.set(true);
            }
            railChunks.add(new KeptChunk(world.getName(), chunkX, chunkZ));

            for (Pos next : neighbours(pos)) {
                if (visited.contains(next)) {
                    continue;
                }
                if (next.chunkX() == chunkX && next.chunkZ() == chunkZ) {
                    queue.add(next);
                } else {
                    long key = ((long) next.chunkX() << 32) | (next.chunkZ() & 0xFFFFFFFFL);
                    continuesElsewhere.computeIfAbsent(key, k -> new ArrayList<>()).add(next);
                }
            }
        }

        if (!limitHit.get()) {
            for (List<Pos> group : continuesElsewhere.values()) {
                Pos first = group.get(0);
                searchChunk(first.chunkX(), first.chunkZ(), group);
            }
        }
    }

    /** Rails connect to the four sides, one block up, level or one block down (slopes). */
    private List<Pos> neighbours(Pos pos) {
        List<Pos> result = new ArrayList<>();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] side : sides) {
            for (int dy = -1; dy <= 1; dy++) {
                int y = pos.y() + dy;
                if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
                    result.add(new Pos(pos.x() + side[0], y, pos.z() + side[1]));
                }
            }
        }
        return result;
    }

    private boolean isRail(Pos pos) {
        // Tag.RAILS: normal, powered, detector and activator rails
        return Tag.RAILS.isTagged(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType());
    }

    private void finishChunk() {
        if (waitingChunks.decrementAndGet() == 0) {
            onDone.accept(new Result(rails.get(), withNeighbours(), !visited.isEmpty(), limitHit.get()));
        }
    }

    /** Like KeepChunks: keep a 3x3 square around every rail chunk, so carts never reach an unloaded edge. */
    private Set<KeptChunk> withNeighbours() {
        Set<KeptChunk> result = new HashSet<>();
        for (KeptChunk chunk : railChunks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    result.add(new KeptChunk(chunk.world(), chunk.x() + dx, chunk.z() + dz));
                }
            }
        }
        return result;
    }
}
