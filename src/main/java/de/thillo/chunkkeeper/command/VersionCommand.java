package de.thillo.chunkkeeper.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;

/** {@code version}: shows which ChunkKeeper build and which server software is running. */
final class VersionCommand implements Subcommand {

    private final ChunkKeeper plugin;

    VersionCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String usage() {
        return "";
    }

    @Override
    public String description() {
        return "Show the plugin and server version.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String platform = isFolia() ? "Folia" : Bukkit.getName();
        sender.sendMessage(Messages.success("ChunkKeeper v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Messages.info("Server: " + platform + " " + Bukkit.getMinecraftVersion() + " (" + Bukkit.getVersion() + ")"));
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
