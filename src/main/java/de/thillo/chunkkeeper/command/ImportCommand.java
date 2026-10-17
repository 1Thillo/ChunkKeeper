package de.thillo.chunkkeeper.command;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.importer.KeepChunksImporter;

/**
 * {@code import}: adds the chunks from KeepChunks' data.yml to the list again. The first start does
 * this on its own; the command is for servers that kept using KeepChunks for a while afterwards.
 */
final class ImportCommand implements Subcommand {

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
        return "";
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        KeepChunksImporter importer = plugin.importer();
        Optional<KeepChunksImporter.Result> result = importer.read();
        if (result.isEmpty()) {
            Messages.error(sender, "import.no-file", "file", importer.dataFile().getPath());
            return;
        }
        int added = plugin.keeper().keep(result.get().chunks(), Origin.now(sender.getName() + " (KeepChunks import)"));
        Messages.success(sender, "import.done", "found", result.get().chunks().size(), "added", added);
        plugin.recordChange(sender, "history.imported", "count", added);
        if (!result.get().unreadable().isEmpty()) {
            Messages.error(sender, "import.unreadable", "count", result.get().unreadable().size());
            plugin.getLogger().warning("Unreadable KeepChunks entries: " + result.get().unreadable());
        }
    }
}
