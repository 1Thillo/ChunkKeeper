package de.thillo.chunkkeeper.command;

import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
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
    public String description() {
        return "Add the chunks from KeepChunks' data.yml.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        KeepChunksImporter importer = plugin.importer();
        Optional<KeepChunksImporter.Result> result = importer.read();
        if (result.isEmpty()) {
            sender.sendMessage(Messages.error("There is no " + importer.dataFile().getPath() + " to import from."));
            return;
        }
        int added = plugin.keeper().keep(result.get().chunks());
        sender.sendMessage(Messages.success("Found " + result.get().chunks().size() + " chunks in KeepChunks' data.yml, "
                + added + " of them were new."));
        if (!result.get().unreadable().isEmpty()) {
            sender.sendMessage(Messages.error(result.get().unreadable().size() + " entries could not be read; see the console."));
            plugin.getLogger().warning("Unreadable KeepChunks entries: " + result.get().unreadable());
        }
    }
}
