package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@code /chunkkeeper <subcommand> ...}: finds the subcommand by its name, checks the permission and
 * hands over the remaining arguments. Without a subcommand, or with {@code help}, it lists what the
 * sender may use.
 *
 * <p>The command is declared in plugin.yml (the classic Bukkit way) instead of Paper's Brigadier
 * API: Brigadier commands are only registered at server start, so they would disappear when PlugMan
 * reloads the plugin.
 */
public final class ChunkKeeperCommand implements CommandExecutor, TabCompleter {

    private static final String HELP = "help";

    private final Permissions permissions;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    public ChunkKeeperCommand(ChunkKeeper plugin) {
        this.permissions = plugin.permissions();
        add(new ChunkCommand(plugin, true));
        add(new ChunkCommand(plugin, false));
        add(new RegionCommand(plugin, true));
        add(new RegionCommand(plugin, false));
        add(new RailCommand(plugin, true));
        add(new RailCommand(plugin, false));
        add(new AreaCommand(plugin));
        add(new ReleaseAllCommand(plugin));
        add(new ListCommand(plugin));
        add(new ChunkInfoCommand(plugin));
        add(new ReloadCommand(plugin));
        add(new ImportCommand(plugin));
        add(new VersionCommand(plugin));
    }

    private void add(Subcommand subcommand) {
        subcommands.put(subcommand.name(), subcommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = args.length == 0 ? HELP : args[0].toLowerCase();
        if (name.equals(HELP)) {
            sendHelp(sender);
            return true;
        }
        Subcommand subcommand = subcommands.get(name);
        if (subcommand == null) {
            Messages.error(sender, "general.unknown-subcommand", "subcommand", args[0]);
            return true;
        }
        if (!permissions.has(sender, subcommand.name())) {
            Messages.error(sender, "general.no-permission");
            return true;
        }
        subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0];
            return startingWith(visibleNames(sender), typed);
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null || !permissions.has(sender, subcommand.name())) {
            return List.of();
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return startingWith(subcommand.suggest(sender, rest), rest[rest.length - 1]);
    }

    /**
     * Who gets to see /chunkkeeper at all: everyone with at least one of these permissions. Bukkit
     * accepts several permissions separated by ";" and lets anyone through who has one of them.
     */
    public String visibilityPermissions(boolean includeLegacy) {
        List<String> nodes = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of(HELP));
        names.addAll(subcommands.keySet());
        for (String name : names) {
            nodes.add("chunkkeeper." + name);
            if (includeLegacy) {
                nodes.add("keepchunks." + name);
            }
        }
        return String.join(";", nodes);
    }

    private List<String> visibleNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        if (permissions.has(sender, HELP)) {
            names.add(HELP);
        }
        for (Subcommand subcommand : subcommands.values()) {
            if (permissions.has(sender, subcommand.name())) {
                names.add(subcommand.name());
            }
        }
        return names;
    }

    private void sendHelp(CommandSender sender) {
        Messages.info(sender, "help.header");
        for (Subcommand subcommand : subcommands.values()) {
            if (permissions.has(sender, subcommand.name())) {
                sender.sendMessage(Component.text("/chunkkeeper " + subcommand.name() + " " + subcommand.usage(), NamedTextColor.WHITE)
                        .append(Component.text(" - " + Messages.text(sender, "help." + subcommand.name()), NamedTextColor.GRAY)));
            }
        }
    }

    private static List<String> startingWith(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
