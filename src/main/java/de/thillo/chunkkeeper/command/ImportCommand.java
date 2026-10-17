package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.importer.KeepChunksImporter;

/**
 * {@code import}: takes over chunks that something else keeps loaded.
 *
 * <ul>
 * <li>{@code import} or {@code import keepchunks}: the chunks from KeepChunks' data.yml. The first
 * start does this on its own; the command is for servers that kept using KeepChunks for a while.</li>
 * <li>{@code import forceload [world]}: the chunks force-loaded with the vanilla /forceload command,
 * so ChunkKeeper lists them and they can be released with its commands.</li>
 * </ul>
 */
final class ImportCommand implements Subcommand {

    private static final String KEEPCHUNKS = "keepchunks";
    private static final String FORCELOAD = "forceload";

    private final ChunkKeeper plugin;

    ImportCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "import";
    }

    @Override
    public String usage() {
        return "[keepchunks | forceload [<world>]]";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String source = args.length == 0 ? KEEPCHUNKS : args[0].toLowerCase();
        if (source.equals(KEEPCHUNKS) && args.length <= 1) {
            importKeepChunks(sender);
        } else if (source.equals(FORCELOAD) && args.length <= 2) {
            List<World> worlds = new ArrayList<>(Bukkit.getWorlds());
            if (args.length == 2) {
                Optional<World> world = Arguments.world(sender, args[1]);
                if (world.isEmpty()) {
                    return;
                }
                worlds = List.of(world.get());
            }
            importForceLoaded(sender, worlds);
        } else {
            Messages.error(sender, "general.usage", "command", name(), "usage", usage());
        }
    }

    private void importKeepChunks(CommandSender sender) {
        KeepChunksImporter importer = plugin.importer();
        Optional<KeepChunksImporter.Result> result = importer.read();
        if (result.isEmpty()) {
            Messages.error(sender, "import.no-file", "file", importer.dataFile().getPath());
            return;
        }
        plugin.backupNow("import").ifPresent(backup -> Messages.info(sender, "backup.automatic", "name", backup));
        int added = plugin.keeper().keep(result.get().chunks(), Origin.now(sender.getName() + " (KeepChunks import)"));
        Messages.success(sender, "import.done", "found", result.get().chunks().size(), "added", added);
        plugin.recordChange(sender, "history.imported", "count", added);
        if (!result.get().unreadable().isEmpty()) {
            Messages.error(sender, "import.unreadable", "count", result.get().unreadable().size());
            plugin.getLogger().warning("Unreadable KeepChunks entries: " + result.get().unreadable());
        }
    }

    private void importForceLoaded(CommandSender sender, List<World> worlds) {
        // The list of force-loaded chunks belongs to the global region thread on Folia
        plugin.loader().runGlobal(() -> {
            List<KeptChunk> forceLoaded = new ArrayList<>();
            for (World world : worlds) {
                for (Chunk chunk : world.getForceLoadedChunks()) {
                    forceLoaded.add(new KeptChunk(world.getName(), chunk.getX(), chunk.getZ()));
                }
            }
            // chunks that ChunkKeeper keeps itself, alone or in an area, stay as they are
            List<KeptChunk> notKept = forceLoaded.stream().filter(chunk -> !plugin.store().isKept(chunk)).toList();
            if (forceLoaded.isEmpty()) {
                Messages.info(sender, "import.forceload-none");
                return;
            }
            if (notKept.isEmpty()) {
                Messages.info(sender, "import.forceload-nothing", "found", forceLoaded.size());
                return;
            }
            plugin.backupNow("import-forceload").ifPresent(backup -> Messages.info(sender, "backup.automatic", "name", backup));
            int added = plugin.keeper().keep(notKept, Origin.now(sender.getName() + " (/forceload import)"));
            Messages.success(sender, "import.forceload-done", "added", added, "already", forceLoaded.size() - notKept.size());
            plugin.recordChange(sender, "history.imported-forceload", "count", added);
        });
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of(KEEPCHUNKS, FORCELOAD);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(FORCELOAD)) {
            return Arguments.worldNames();
        }
        return List.of();
    }
}
