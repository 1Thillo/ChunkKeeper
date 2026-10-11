package de.thillo.chunkkeeper.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;

/** {@code releaseall confirm}: empties the whole list. Console only, unless config.yml allows more. */
final class ReleaseAllCommand implements Subcommand {

    private final ChunkKeeper plugin;

    ReleaseAllCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "releaseall";
    }

    @Override
    public String usage() {
        return "confirm";
    }

    @Override
    public String description() {
        return "Stop keeping any chunk loaded.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (plugin.settings().releaseAllConsoleOnly() && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Messages.error("Only the server console may do that (release-all-console-only in config.yml)."));
            return;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Messages.error("This releases all " + plugin.store().size()
                    + " kept chunks. Type /chunkkeeper releaseall confirm to do it."));
            return;
        }
        int released = plugin.keeper().release(plugin.store().all());
        sender.sendMessage(Messages.success("Released " + released + " chunks."));
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("confirm") : List.of();
    }
}
