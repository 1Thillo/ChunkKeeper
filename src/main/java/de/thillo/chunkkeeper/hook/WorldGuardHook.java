package de.thillo.chunkkeeper.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.command.CommandSender;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Text;

/**
 * Looks up WorldGuard regions.
 *
 * <p>Only create this class when WorldGuard is installed: it uses WorldGuard classes, which do not
 * exist otherwise.
 */
public final class WorldGuardHook {

    public Optional<Area> region(CommandSender sender, World world, String name) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        ProtectedRegion region = regions == null ? null : regions.getRegion(name);
        if (region == null) {
            Messages.error(sender, "worldguard.unknown-region", "region", name, "world", world.getName());
            return Optional.empty();
        }
        if (region.getType() == RegionType.GLOBAL) {
            Messages.error(sender, "worldguard.global-region");
            return Optional.empty();
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        List<KeptChunk> chunks = new ArrayList<>();
        for (int x = min.x() >> 4; x <= max.x() >> 4; x++) {
            for (int z = min.z() >> 4; z <= max.z() >> 4; z++) {
                if (region.getType() == RegionType.CUBOID || touches(region, x, z, min.y(), max.y())) {
                    chunks.add(new KeptChunk(world.getName(), x, z));
                }
            }
        }
        return Optional.of(new Area(world.getName(), chunks, Text.of("describe.worldguard", "region", region.getId(), "world", world.getName())));
    }

    public List<String> regionNames(World world) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regions == null) {
            return List.of();
        }
        return regions.getRegions().keySet().stream().filter(id -> !id.equals(ProtectedRegion.GLOBAL_REGION)).sorted().toList();
    }

    /** For polygon regions: does the region overlap this chunk at all? */
    private static boolean touches(ProtectedRegion region, int chunkX, int chunkZ, int minY, int maxY) {
        ProtectedCuboidRegion chunk = new ProtectedCuboidRegion("chunkkeeper-probe",
                BlockVector3.at(chunkX << 4, minY, chunkZ << 4),
                BlockVector3.at((chunkX << 4) + 15, maxY, (chunkZ << 4) + 15));
        return !chunk.getIntersectingRegions(List.of(region)).isEmpty();
    }
}
