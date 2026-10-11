package de.thillo.chunkkeeper.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.regions.Region;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;

/**
 * Reads a player's WorldEdit selection. Works with WorldEdit and FastAsyncWorldEdit.
 *
 * <p>Only create this class when WorldEdit is installed: it uses WorldEdit classes, which do not
 * exist otherwise.
 */
public final class WorldEditHook {

    public Optional<Area> selection(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        Region region;
        try {
            region = session.getSelection(session.getSelectionWorld());
        } catch (IncompleteRegionException e) {
            player.sendMessage(Messages.error("Select an area with WorldEdit first."));
            return Optional.empty();
        }
        String world = BukkitAdapter.adapt(region.getWorld()).getName();
        // getChunks() follows the real shape, so polygon and sphere selections work as well
        List<KeptChunk> chunks = new ArrayList<>();
        for (BlockVector2 chunk : region.getChunks()) {
            chunks.add(new KeptChunk(world, chunk.x(), chunk.z()));
        }
        return Optional.of(new Area(world, chunks, "your WorldEdit selection in '" + world + "'"));
    }
}
