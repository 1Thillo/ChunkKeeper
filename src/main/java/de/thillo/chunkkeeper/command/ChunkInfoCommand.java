package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.Text;

/** {@code chunkinfo}: whether a chunk is kept, force-loaded and currently loaded. */
final class ChunkInfoCommand implements Subcommand {

    private final ChunkKeeper plugin;

    ChunkInfoCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "chunkinfo";
    }

    @Override
    public String usage() {
        return "(current | coords <x> <z> <world> | coords <x1> <z1> <x2> <z2> <world> | radius <r> [<x> <z> <world>] | worldedit | worldguard <region> <world>)";
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        if (isArea(args)) {
            areaInfo(sender, args);
        } else {
            chunkInfo(sender, args);
        }
    }

    private static boolean isArea(String[] args) {
        if (args.length == 0) {
            return false;
        }
        String mode = args[0].toLowerCase();
        return mode.equals("worldedit") || mode.equals("worldguard") || mode.equals("radius") || (mode.equals("coords") && args.length == 6);
    }

    private void areaInfo(CommandSender sender, String[] args) {
        Optional<Area> area = Arguments.area(plugin, sender, args, name());
        if (area.isEmpty()) {
            return;
        }
        long kept = area.get().chunks().stream().filter(plugin.store()::isKept).count();
        Messages.info(sender, "info.area-header", "area", area.get().description());
        Messages.field(sender, "info.label-area-chunks", area.get().chunks().size());
        Messages.field(sender, "info.label-kept", kept);
    }

    private void chunkInfo(CommandSender sender, String[] args) {
        Optional<KeptChunk> target = Arguments.chunk(sender, args, name());
        if (target.isEmpty()) {
            return;
        }
        KeptChunk chunk = target.get();
        boolean kept = plugin.store().isKept(chunk);
        boolean single = plugin.store().contains(chunk);
        Origin origin = plugin.store().origin(chunk).orElse(Origin.UNKNOWN);
        List<String> areas = plugin.store().areasContaining(chunk);
        World world = Bukkit.getWorld(chunk.world());
        // On Folia the force-load flags may only be read on the global region thread.
        plugin.loader().runGlobal(() -> {
            boolean forceLoaded = world.isChunkForceLoaded(chunk.x(), chunk.z());
            Messages.info(sender, "info.chunk-header", "chunk", chunk.describe());
            Messages.field(sender, "info.label-blocks", Text.of("info.blocks",
                    "x1", chunk.x() * 16, "x2", chunk.x() * 16 + 15, "z1", chunk.z() * 16, "z2", chunk.z() * 16 + 15));
            Messages.yesNo(sender, "info.label-kept", kept);
            Messages.yesNo(sender, "info.label-on-its-own", single);
            if (single) {
                Messages.field(sender, "info.label-added", origin.describe());
            }
            Messages.field(sender, "info.label-areas", areas.isEmpty() ? Text.of("general.none") : String.join(", ", areas));
            Messages.yesNo(sender, "info.label-force-loaded", forceLoaded);
            Messages.yesNo(sender, "info.label-loaded", world.isChunkLoaded(chunk.x(), chunk.z()));
            if (kept != forceLoaded) {
                Messages.info(sender, kept ? "info.not-force-loaded-yet" : "info.forced-by-something-else");
            }
        });
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> modes = new ArrayList<>(List.of("current"));
            modes.addAll(Arguments.areaSuggestions(plugin, sender, args));
            return modes;
        }
        // The third value after "coords" is either the world (one chunk) or x2 (an area), so offer both.
        if (args[0].equalsIgnoreCase("coords") && args.length == 4) {
            List<String> options = new ArrayList<>(Arguments.worldNames());
            options.addAll(Arguments.coordSuggestions(sender, "xzxzw", 3));
            return options;
        }
        return Arguments.areaSuggestions(plugin, sender, args);
    }
}
