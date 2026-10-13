package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.NamedArea;

/**
 * {@code keepregion} and {@code releaseregion}: many chunks at once, given by two chunk corners, a
 * WorldEdit selection or a WorldGuard region.
 *
 * <p>Without a name the chunks are kept one by one, like single chunks. With {@code --name} they
 * become a named area that is listed and released as a whole ({@code /chunkkeeper area}).
 */
final class RegionCommand implements Subcommand {

    private final ChunkKeeper plugin;
    private final boolean keep;

    RegionCommand(ChunkKeeper plugin, boolean keep) {
        this.plugin = plugin;
        this.keep = keep;
    }

    @Override
    public String name() {
        return keep ? "keepregion" : "releaseregion";
    }

    @Override
    public String usage() {
        return keep ? Arguments.AREA_USAGE + " [--name <name>]" : Arguments.AREA_USAGE;
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Arguments.Named> named = Arguments.named(sender, args);
        if (named.isEmpty()) {
            return;
        }
        if (!keep && named.get().name().isPresent()) {
            Messages.error(sender, "general.use-area-release");
            return;
        }
        Optional<Area> area = Arguments.area(plugin, sender, named.get().rest(), name());
        if (area.isEmpty()) {
            return;
        }
        if (named.get().name().isPresent()) {
            createNamedArea(sender, named.get().name().get(), area.get());
        } else if (keep) {
            keepSingle(sender, area.get());
        } else {
            releaseSingle(sender, area.get());
        }
    }

    private void createNamedArea(CommandSender sender, String name, Area area) {
        NamedArea namedArea = new NamedArea(name, area.world(), Set.copyOf(area.chunks()), Messages.stored(area.description()),
                Origin.now(sender.getName()));
        Optional<Integer> newlyLoaded = plugin.keeper().createArea(namedArea);
        if (newlyLoaded.isEmpty()) {
            Messages.error(sender, "general.area-exists", "name", name);
            return;
        }
        Messages.success(sender, "region.area-created", "name", name, "count", namedArea.chunks().size(),
                "source", area.description(), "new", newlyLoaded.get());
        plugin.recordChange(sender, "history.area-created", "name", name, "count", namedArea.chunks().size(), "source", area.description());
    }

    private void keepSingle(CommandSender sender, Area area) {
        int total = area.chunks().size();
        int added = plugin.keeper().keep(area.chunks(), Origin.now(sender.getName()));
        Messages.success(sender, "region.kept", "added", added, "total", total, "area", area.description(), "already", total - added);
        if (added > 0) {
            plugin.recordChange(sender, "history.kept-chunks", "count", added, "area", area.description());
        }
    }

    private void releaseSingle(CommandSender sender, Area area) {
        int removed = plugin.keeper().release(area.chunks());
        Messages.success(sender, "region.released", "removed", removed, "total", area.chunks().size(), "area", area.description());
        if (removed > 0) {
            plugin.recordChange(sender, "history.released-chunks", "count", removed, "area", area.description());
        }
        long stillInAreas = area.chunks().stream().filter(chunk -> plugin.store().isKept(chunk)).count();
        if (stillInAreas > 0) {
            Messages.info(sender, "region.still-in-areas", "count", stillInAreas);
        }
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (keep) {
            Optional<List<String>> nameOption = Arguments.nameSuggestions(args);
            if (nameOption.isPresent()) {
                return nameOption.get();
            }
        }
        return Arguments.areaSuggestions(plugin, sender, args);
    }
}
