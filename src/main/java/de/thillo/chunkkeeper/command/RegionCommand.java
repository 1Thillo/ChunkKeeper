package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
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
    public String description() {
        return keep ? "Keep all chunks of an area loaded, optionally as a named area."
                : "Stop keeping the single chunks of an area loaded (named areas: /chunkkeeper area release).";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Arguments.Named> named = Arguments.named(sender, args);
        if (named.isEmpty()) {
            return;
        }
        if (!keep && named.get().name().isPresent()) {
            sender.sendMessage(Messages.error("To release a named area, use /chunkkeeper area release <name>."));
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
        NamedArea namedArea = new NamedArea(name, area.world(), Set.copyOf(area.chunks()), area.description());
        Optional<Integer> newlyLoaded = plugin.keeper().createArea(namedArea);
        if (newlyLoaded.isEmpty()) {
            sender.sendMessage(Messages.error("There is already an area called '" + name + "'. Release it first or choose another name."));
            return;
        }
        sender.sendMessage(Messages.success("Created area '" + name + "' with " + namedArea.chunks().size() + " chunks from "
                + area.description() + " (" + newlyLoaded.get() + " of them were not kept before)."));
    }

    private void keepSingle(CommandSender sender, Area area) {
        int total = area.chunks().size();
        int added = plugin.keeper().keep(area.chunks());
        sender.sendMessage(Messages.success("Keeping " + added + " more chunks loaded (" + total + " in "
                + area.description() + ", " + (total - added) + " were kept already)."));
    }

    private void releaseSingle(CommandSender sender, Area area) {
        int removed = plugin.keeper().release(area.chunks());
        sender.sendMessage(Messages.success("Released " + removed + " of the " + area.chunks().size() + " chunks in "
                + area.description() + "."));
        long stillInAreas = area.chunks().stream().filter(chunk -> plugin.store().isKept(chunk)).count();
        if (stillInAreas > 0) {
            sender.sendMessage(Messages.info(stillInAreas + " of them stay loaded because they belong to named areas."));
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
