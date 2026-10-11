package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;

/**
 * {@code keepregion} and {@code releaseregion}: many chunks at once, given by two chunk corners, a
 * WorldEdit selection or a WorldGuard region.
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
        return Arguments.AREA_USAGE;
    }

    @Override
    public String description() {
        return keep ? "Keep all chunks of an area loaded." : "Stop keeping the chunks of an area loaded.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Area> area = Arguments.area(plugin, sender, args, name());
        if (area.isEmpty()) {
            return;
        }
        int total = area.get().chunks().size();
        if (keep) {
            int added = plugin.keeper().keep(area.get().chunks());
            sender.sendMessage(Messages.success("Keeping " + added + " more chunks loaded (" + total + " in "
                    + area.get().description() + ", " + (total - added) + " were kept already)."));
        } else {
            int removed = plugin.keeper().release(area.get().chunks());
            sender.sendMessage(Messages.success("Released " + removed + " of the " + total + " chunks in "
                    + area.get().description() + "."));
        }
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        return Arguments.areaSuggestions(plugin, sender, args);
    }
}
