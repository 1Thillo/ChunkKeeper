package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Permissions;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * {@code /chunkkeeper <subcommand> ...}: finds the subcommand by its name, checks the permission and
 * hands over the remaining arguments. Without a subcommand, or with {@code help}, it lists what the
 * sender may use.
 */
public final class ChunkKeeperCommand implements BasicCommand {

    private static final String HELP = "help";

    private final Permissions permissions;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    public ChunkKeeperCommand(ChunkKeeper plugin) {
        this.permissions = plugin.permissions();
        add(new ChunkCommand(plugin, true));
        add(new ChunkCommand(plugin, false));
        add(new VersionCommand(plugin));
    }

    private void add(Subcommand subcommand) {
        subcommands.put(subcommand.name(), subcommand);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String name = args.length == 0 ? HELP : args[0].toLowerCase();
        if (name.equals(HELP)) {
            sendHelp(sender);
            return;
        }
        Subcommand subcommand = subcommands.get(name);
        if (subcommand == null) {
            sender.sendMessage(Messages.error("Unknown subcommand '" + args[0] + "'. See /chunkkeeper help."));
            return;
        }
        if (!permissions.has(sender, subcommand.name())) {
            sender.sendMessage(Messages.error("You don't have permission to do that."));
            return;
        }
        subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
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

    /** Only show /chunkkeeper to people who may use at least one part of it. */
    @Override
    public boolean canUse(CommandSender sender) {
        return !visibleNames(sender).isEmpty();
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
        sender.sendMessage(Messages.info("ChunkKeeper commands (alias /ck):"));
        for (Subcommand subcommand : subcommands.values()) {
            if (permissions.has(sender, subcommand.name())) {
                sender.sendMessage(Component.text("/chunkkeeper " + subcommand.name() + " " + subcommand.usage(), NamedTextColor.WHITE)
                        .append(Component.text(" - " + subcommand.description(), NamedTextColor.GRAY)));
            }
        }
    }

    private static List<String> startingWith(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
