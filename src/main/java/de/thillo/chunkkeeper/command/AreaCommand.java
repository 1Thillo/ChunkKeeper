package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Keeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.NamedArea;

/**
 * {@code area}: lists and manages named areas. They are created with {@code keepregion ... --name},
 * {@code keeprail ... --name} or {@code area convert}, which groups existing single chunks.
 */
final class AreaCommand implements Subcommand {

    private static final List<String> ACTIONS = List.of("list", "info", "release", "rename", "convert", "dissolve");
    private static final String CONVERT_USAGE =
            "convert <name> (coords <x1> <z1> <x2> <z2> <world> | worldedit | worldguard <region> <world> | world <world>)";

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
        return "(list | info <name> | release <name> | rename <name> <new name> | " + CONVERT_USAGE + " | dissolve <name>)";
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
            case "convert" -> {
                if (args.length >= 3) {
                    convert(sender, args[1].toLowerCase(), Arrays.copyOfRange(args, 2, args.length));
                } else {
                    Arguments.sendUsage(sender, name(), CONVERT_USAGE);
                }
            }
            case "dissolve" -> {
                if (args.length == 2) {
                    dissolve(sender, args[1].toLowerCase());
                } else {
                    Arguments.sendUsage(sender, name(), "dissolve <name>");
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

    /**
     * Moves the single chunks inside a selection into a new named area, e.g. to group chunks that
     * were imported from KeepChunks. Nothing is unloaded on the way.
     */
    private void convert(CommandSender sender, String areaName, String[] selectionArgs) {
        if (!NamedArea.isValidName(areaName)) {
            sender.sendMessage(Messages.error("'" + areaName + "' is not a valid area name. Use up to 32 letters, digits, - and _."));
            return;
        }
        if (plugin.store().area(areaName).isPresent()) {
            sender.sendMessage(Messages.error("There is already an area called '" + areaName + "'. Choose another name."));
            return;
        }
        Optional<Area> selection = selection(sender, selectionArgs);
        if (selection.isEmpty()) {
            return;
        }
        Optional<Integer> moved = plugin.keeper().convertToArea(areaName, selection.get().world(),
                selection.get().chunks(), selection.get().description());
        if (moved.isEmpty()) {
            sender.sendMessage(Messages.error("There is already an area called '" + areaName + "'. Choose another name."));
        } else if (moved.get() == 0) {
            sender.sendMessage(Messages.error("There are no single chunks in " + selection.get().description()
                    + ", so there is nothing to convert. No area was created."));
        } else {
            sender.sendMessage(Messages.success("Converted " + moved.get() + " single chunks from " + selection.get().description()
                    + " into area '" + areaName + "'. They stayed loaded the whole time."));
        }
    }

    /** {@code world <world>} takes every single chunk of a world, the other modes work like keepregion. */
    private Optional<Area> selection(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("world")) {
            if (args.length != 2) {
                Arguments.sendUsage(sender, name(), CONVERT_USAGE);
                return Optional.empty();
            }
            String world = args[1];
            return Optional.of(new Area(world, plugin.store().inWorld(world), "all single chunks in '" + world + "'"));
        }
        return Arguments.area(plugin, sender, args, name() + " convert <name>");
    }

    private void dissolve(CommandSender sender, String areaName) {
        Optional<NamedArea> area = plugin.store().area(areaName);
        Optional<Integer> added = plugin.keeper().dissolveArea(areaName);
        if (added.isEmpty() || area.isEmpty()) {
            sender.sendMessage(Messages.error("There is no area called '" + areaName + "'. See /chunkkeeper area list."));
            return;
        }
        sender.sendMessage(Messages.success("Dissolved area '" + areaName + "': its " + area.get().chunks().size()
                + " chunks are now kept on their own (" + added.get() + " of them were not before). Nothing was unloaded."));
    }

    private Optional<NamedArea> find(CommandSender sender, String areaName) {
        Optional<NamedArea> area = plugin.store().area(areaName);
        if (area.isEmpty()) {
            sender.sendMessage(Messages.error("There is no area called '" + areaName + "'. See /chunkkeeper area list."));
        }
        return area;
    }

    private List<String> convertSuggestions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<name>");
        }
        String[] selection = Arrays.copyOfRange(args, 2, args.length);
        if (selection.length == 1) {
            List<String> modes = new ArrayList<>(Arguments.areaSuggestions(plugin, sender, selection));
            modes.add("world");
            return modes;
        }
        if (selection[0].equalsIgnoreCase("world")) {
            return selection.length == 2 ? List.copyOf(plugin.store().worlds()) : List.of();
        }
        return Arguments.areaSuggestions(plugin, sender, selection);
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        String action = args[0].toLowerCase();
        if (action.equals("convert")) {
            return convertSuggestions(sender, args);
        }
        if (args.length == 2 && !action.equals("list")) {
            return plugin.store().areas().stream().map(NamedArea::name).toList();
        }
        if (args.length == 3 && action.equals("rename")) {
            return List.of("<new name>");
        }
        return List.of();
    }
}
