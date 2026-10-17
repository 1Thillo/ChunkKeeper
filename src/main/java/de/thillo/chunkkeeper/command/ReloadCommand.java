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
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();
        Messages.success(sender, "reload.done", "chunks", plugin.store().size(), "areas", plugin.store().areas().size());
    }
}
