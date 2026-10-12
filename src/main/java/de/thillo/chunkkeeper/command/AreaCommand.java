package de.thillo.chunkkeeper.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Keeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.NamedArea;

/**
 * {@code area}: lists and manages named areas. They are created with {@code keepregion ... --name}
 * or {@code keeprail ... --name}.
 */
final class AreaCommand implements Subcommand {

    private static final List<String> ACTIONS = List.of("list", "info", "release", "rename");

    private final ChunkKeeper plugin;

    AreaCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "area";
    }

    @Override
    public String usage() {
        return "(list | info <name> | release <name> | rename <name> <new name>)";
    }

    @Override
    public String description() {
        return "Manage named areas.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase();
        switch (action) {
            case "list" -> list(sender);
            case "info" -> {
                if (args.length == 2) {
                    info(sender, args[1].toLowerCase());
                } else {
                    Arguments.sendUsage(sender, name(), "info <name>");
                }
            }
            case "release" -> {
                if (args.length == 2) {
                    release(sender, args[1].toLowerCase());
                } else {
                    Arguments.sendUsage(sender, name(), "release <name>");
                }
            }
            case "rename" -> {
                if (args.length == 3) {
                    rename(sender, args[1].toLowerCase(), args[2].toLowerCase());
                } else {
                    Arguments.sendUsage(sender, name(), "rename <name> <new name>");
                }
            }
            default -> Arguments.sendUsage(sender, name(), usage());
        }
    }

    private void list(CommandSender sender) {
        List<NamedArea> areas = plugin.store().areas();
        if (areas.isEmpty()) {
            sender.sendMessage(Messages.info("There are no named areas. Create one with /chunkkeeper keepregion ... --name <name>."));
            return;
        }
        sender.sendMessage(Messages.info(areas.size() + " named areas:"));
        for (NamedArea area : areas) {
            sender.sendMessage(Messages.field(area.name(), area.chunks().size() + " chunks in '" + area.world() + "'"));
        }
    }

    private void info(CommandSender sender, String areaName) {
        Optional<NamedArea> found = find(sender, areaName);
        if (found.isEmpty()) {
            return;
        }
        NamedArea area = found.get();
        sender.sendMessage(Messages.info("Area '" + area.name() + "'"));
        sender.sendMessage(Messages.field("World", area.world()));
        sender.sendMessage(Messages.field("Chunks", String.valueOf(area.chunks().size())));
        if (!area.source().isEmpty()) {
            sender.sendMessage(Messages.field("Made from", area.source()));
        }
        Comparator<KeptChunk> byX = Comparator.comparingInt(KeptChunk::x);
        int minX = area.chunks().stream().min(byX).map(KeptChunk::x).orElse(0);
        int maxX = area.chunks().stream().max(byX).map(KeptChunk::x).orElse(0);
        Comparator<KeptChunk> byZ = Comparator.comparingInt(KeptChunk::z);
        int minZ = area.chunks().stream().min(byZ).map(KeptChunk::z).orElse(0);
        int maxZ = area.chunks().stream().max(byZ).map(KeptChunk::z).orElse(0);
        sender.sendMessage(Messages.field("Spans chunks", "(" + minX + ", " + minZ + ") to (" + maxX + ", " + maxZ + ")"));
    }

    private void release(CommandSender sender, String areaName) {
        Optional<Keeper.AreaRelease> released = plugin.keeper().releaseArea(areaName);
        if (released.isEmpty()) {
            sender.sendMessage(Messages.error("There is no area called '" + areaName + "'. See /chunkkeeper area list."));
            return;
        }
        NamedArea area = released.get().area();
        int stillKept = area.chunks().size() - released.get().unloaded();
        sender.sendMessage(Messages.success("Released area '" + area.name() + "' (" + area.chunks().size() + " chunks)."));
        if (stillKept > 0) {
            sender.sendMessage(Messages.info(stillKept + " of its chunks stay loaded because they are kept on their own or by other areas."));
        }
    }

    private void rename(CommandSender sender, String oldName, String newName) {
        if (!NamedArea.isValidName(newName)) {
            sender.sendMessage(Messages.error("'" + newName + "' is not a valid area name. Use up to 32 letters, digits, - and _."));
            return;
        }
        if (find(sender, oldName).isEmpty()) {
            return;
        }
        if (plugin.keeper().renameArea(oldName, newName)) {
            sender.sendMessage(Messages.success("Renamed area '" + oldName + "' to '" + newName + "'."));
        } else {
            sender.sendMessage(Messages.error("There is already an area called '" + newName + "'."));
        }
    }

    private Optional<NamedArea> find(CommandSender sender, String areaName) {
        Optional<NamedArea> area = plugin.store().area(areaName);
        if (area.isEmpty()) {
            sender.sendMessage(Messages.error("There is no area called '" + areaName + "'. See /chunkkeeper area list."));
        }
        return area;
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        String action = args[0].toLowerCase();
        if (args.length == 2 && !action.equals("list")) {
            return plugin.store().areas().stream().map(NamedArea::name).toList();
        }
        if (args.length == 3 && action.equals("rename")) {
            return List.of("<new name>");
        }
        return List.of();
    }
}
