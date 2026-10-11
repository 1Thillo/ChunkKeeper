package de.thillo.chunkkeeper;

import org.bukkit.command.CommandSender;

/**
 * Permission checks. Every subcommand has its own node, {@code chunkkeeper.<subcommand>}.
 * With {@code legacy-permissions} enabled, the old {@code keepchunks.<subcommand>} node counts as well.
 */
public final class Permissions {

    private final ChunkKeeper plugin;

    public Permissions(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    public boolean has(CommandSender sender, String subcommand) {
        if (sender.hasPermission("chunkkeeper." + subcommand)) {
            return true;
        }
        return plugin.settings().legacyPermissions() && sender.hasPermission("keepchunks." + subcommand);
    }
}
