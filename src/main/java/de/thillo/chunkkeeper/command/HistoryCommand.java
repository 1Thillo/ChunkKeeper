package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** {@code history [page]}: who changed what and when, newest first, from history.log. */
final class HistoryCommand implements Subcommand {

    private static final int PAGE_SIZE = 10;

    private final ChunkKeeper plugin;

    HistoryCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "history";
    }

    @Override
    public String usage() {
        return "[page]";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length == 1) {
            Optional<Integer> number = Arguments.integer(sender, args[0]);
            if (number.isEmpty()) {
                return;
            }
            page = number.get();
        } else if (args.length > 1) {
            Arguments.sendUsage(sender, name(), usage());
            return;
        }

        List<String> lines = plugin.history().newestFirst();
        if (lines.isEmpty()) {
            Messages.info(sender, "history.empty");
            return;
        }
        int pages = (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page < 1 || page > pages) {
            Messages.error(sender, "list.no-such-page", "page", page, "pages", pages);
            return;
        }
        Messages.info(sender, "history.header", "page", page, "pages", pages);
        int from = (page - 1) * PAGE_SIZE;
        for (String line : lines.subList(from, Math.min(from + PAGE_SIZE, lines.size()))) {
            sender.sendMessage(Component.text(line, NamedTextColor.WHITE));
        }
    }
}
