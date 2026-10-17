package de.thillo.chunkkeeper.command;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Keeper;
import de.thillo.chunkkeeper.Messages;

/** {@code releaseall confirm}: releases every single chunk and every named area. Console only, unless config.yml allows more. */
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
    public void execute(CommandSender sender, String[] args) {
        if (plugin.settings().releaseAllConsoleOnly() && !(sender instanceof ConsoleCommandSender)) {
            Messages.error(sender, "releaseall.console-only");
            return;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            Messages.error(sender, "releaseall.confirm", "chunks", plugin.store().size(), "areas", plugin.store().areas().size());
            return;
        }
        Keeper.ReleaseAll released = plugin.keeper().releaseAll();
        Messages.success(sender, "releaseall.done", "chunks", released.chunks(), "areas", released.areas());
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("confirm") : List.of();
    }
}
