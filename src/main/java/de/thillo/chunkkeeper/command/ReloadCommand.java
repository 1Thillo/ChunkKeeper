package de.thillo.chunkkeeper.command;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;

/** {@code reload}: reads config.yml and chunks.yml again, for example after editing them by hand. */
final class ReloadCommand implements Subcommand {

    private final ChunkKeeper plugin;

    ReloadCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public String description() {
        return "Reload config.yml and chunks.yml.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();
        sender.sendMessage(Messages.success("Reloaded. " + plugin.store().size() + " single chunks and "
                + plugin.store().areas().size() + " named areas are kept loaded."));
    }
}
