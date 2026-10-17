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
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.NamedArea;
import de.thillo.chunkkeeper.Text;

/**
 * {@code area}: lists and manages named areas. They are created with {@code keepregion ... --name},
 * {@code keeprail ... --name} or {@code area convert}, which groups existing single chunks.
 */
final class AreaCommand implements Subcommand {

    private static final List<String> ACTIONS = List.of("list", "info", "release", "rename", "convert", "dissolve");
    private static final String CONVERT_USAGE =
            "convert <name> (coords <x1> <z1> <x2> <z2> <world> | radius <r> [<x> <z> <world>] | worldedit | worldguard <region> <world> | world <world>)";

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
            Messages.info(sender, "area.none");
            return;
        }
        Messages.info(sender, "area.list-header", "count", areas.size());
        for (NamedArea area : areas) {
            Messages.namedField(sender, area.name(), Text.of("area.list-entry", "count", area.chunks().size(), "world", area.world()));
        }
    }

    private void info(CommandSender sender, String areaName) {
        Optional<NamedArea> found = find(sender, areaName);
        if (found.isEmpty()) {
            return;
        }
        NamedArea area = found.get();
        Messages.info(sender, "area.info-header", "name", area.name());
        Messages.field(sender, "area.label-world", area.world());
        Messages.field(sender, "area.label-chunks", area.chunks().size());
        if (!area.source().isEmpty()) {
            Messages.field(sender, "area.label-source", area.source());
        }
        Messages.field(sender, "area.label-created", area.origin().describe());
        Comparator<KeptChunk> byX = Comparator.comparingInt(KeptChunk::x);
        int minX = area.chunks().stream().min(byX).map(KeptChunk::x).orElse(0);
        int maxX = area.chunks().stream().max(byX).map(KeptChunk::x).orElse(0);
        Comparator<KeptChunk> byZ = Comparator.comparingInt(KeptChunk::z);
        int minZ = area.chunks().stream().min(byZ).map(KeptChunk::z).orElse(0);
        int maxZ = area.chunks().stream().max(byZ).map(KeptChunk::z).orElse(0);
        Messages.field(sender, "area.label-span", Text.of("area.span", "x1", minX, "z1", minZ, "x2", maxX, "z2", maxZ));
    }

    private void release(CommandSender sender, String areaName) {
        Optional<Keeper.AreaRelease> released = plugin.keeper().releaseArea(areaName);
        if (released.isEmpty()) {
            Messages.error(sender, "general.area-unknown", "name", areaName);
            return;
        }
        NamedArea area = released.get().area();
        int stillKept = area.chunks().size() - released.get().unloaded();
        Messages.success(sender, "area.released", "name", area.name(), "count", area.chunks().size());
        plugin.recordChange(sender, "history.area-released", "name", area.name(), "count", area.chunks().size());
        if (stillKept > 0) {
            Messages.info(sender, "area.still-kept", "count", stillKept);
        }
    }

    private void rename(CommandSender sender, String oldName, String newName) {
        if (!NamedArea.isValidName(newName)) {
            Messages.error(sender, "general.invalid-name", "name", newName);
            return;
        }
        if (find(sender, oldName).isEmpty()) {
            return;
        }
        if (plugin.keeper().renameArea(oldName, newName)) {
            Messages.success(sender, "area.renamed", "old", oldName, "new", newName);
            plugin.recordChange(sender, "history.area-renamed", "old", oldName, "new", newName);
        } else {
            Messages.error(sender, "general.area-exists-short", "name", newName);
        }
    }

    /**
     * Moves the single chunks inside a selection into a new named area, e.g. to group chunks that
     * were imported from KeepChunks. Nothing is unloaded on the way.
     */
    private void convert(CommandSender sender, String areaName, String[] selectionArgs) {
        if (!NamedArea.isValidName(areaName)) {
            Messages.error(sender, "general.invalid-name", "name", areaName);
            return;
        }
        if (plugin.store().area(areaName).isPresent()) {
            Messages.error(sender, "general.area-exists-other-name", "name", areaName);
            return;
        }
        Optional<Area> selection = selection(sender, selectionArgs);
        if (selection.isEmpty()) {
            return;
        }
        Optional<Integer> moved = plugin.keeper().convertToArea(areaName, selection.get().world(),
                selection.get().chunks(), Messages.stored(selection.get().description()), Origin.now(sender.getName()));
        if (moved.isEmpty()) {
            Messages.error(sender, "general.area-exists-other-name", "name", areaName);
        } else if (moved.get() == 0) {
            Messages.error(sender, "area.nothing-to-convert", "selection", selection.get().description());
        } else {
            Messages.success(sender, "area.converted", "count", moved.get(), "selection", selection.get().description(), "name", areaName);
            plugin.recordChange(sender, "history.area-converted", "name", areaName, "count", moved.get());
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
            return Optional.of(new Area(world, plugin.store().inWorld(world), Text.of("describe.world-singles", "world", world)));
        }
        return Arguments.area(plugin, sender, args, name() + " convert <name>");
    }

    private void dissolve(CommandSender sender, String areaName) {
        Optional<NamedArea> area = plugin.store().area(areaName);
        Optional<Integer> added = plugin.keeper().dissolveArea(areaName, Origin.now(sender.getName()));
        if (added.isEmpty() || area.isEmpty()) {
            Messages.error(sender, "general.area-unknown", "name", areaName);
            return;
        }
        Messages.success(sender, "area.dissolved", "name", areaName, "count", area.get().chunks().size(), "added", added.get());
        plugin.recordChange(sender, "history.area-dissolved", "name", areaName, "count", area.get().chunks().size());
    }

    private Optional<NamedArea> find(CommandSender sender, String areaName) {
        Optional<NamedArea> area = plugin.store().area(areaName);
        if (area.isEmpty()) {
            Messages.error(sender, "general.area-unknown", "name", areaName);
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
