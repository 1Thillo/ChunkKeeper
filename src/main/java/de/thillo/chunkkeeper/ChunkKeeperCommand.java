package de.thillo.chunkkeeper;

import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles {@code /chunkkeeper <subcommand>}. Each subcommand is one branch in {@link #execute}.
 */
final class ChunkKeeperCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("version");

    private final ChunkKeeper plugin;

    ChunkKeeperCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("version")) {
            sendVersion(sender);
            return;
        }
        sender.sendMessage(Component.text("Usage: /chunkkeeper version", NamedTextColor.RED));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(typed)).toList();
        }
        return List.of();
    }

    @Override
    public String permission() {
        return "chunkkeeper.use";
    }

    private void sendVersion(CommandSender sender) {
        String platform = isFolia() ? "Folia" : Bukkit.getName();
        sender.sendMessage(Component.text("ChunkKeeper v" + plugin.getPluginMeta().getVersion(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Server: " + platform + " " + Bukkit.getMinecraftVersion()
                + " (" + Bukkit.getVersion() + ")", NamedTextColor.GRAY));
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
