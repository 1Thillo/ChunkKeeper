package de.thillo.chunkkeeper.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.NamedArea;
import de.thillo.chunkkeeper.Text;

/**
 * {@code list}: how many chunks are kept per world, or with a world name the chunks themselves,
 * ten per page.
 */
final class ListCommand implements Subcommand {

    private static final int PAGE_SIZE = 10;

    private final ChunkKeeper plugin;

    ListCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String usage() {
        return "[<world> [page]]";
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            listWorlds(sender);
        } else if (args.length <= 2) {
            int page = 1;
            if (args.length == 2) {
                Optional<Integer> number = Arguments.integer(sender, args[1]);
                if (number.isEmpty()) {
                    return;
                }
                page = number.get();
            }
            listChunks(sender, args[0], page);
        } else {
            Arguments.sendUsage(sender, name(), usage());
        }
    }

    private void listWorlds(CommandSender sender) {
        List<NamedArea> areas = plugin.store().areas();
        if (plugin.store().size() == 0 && areas.isEmpty()) {
            Messages.info(sender, "list.nothing");
            return;
        }
        if (plugin.store().size() > 0) {
            Messages.info(sender, "list.header", "count", plugin.store().size());
            for (String world : plugin.store().worlds()) {
                Messages.namedField(sender, world, Text.of("list.world-entry", "count", plugin.store().inWorld(world).size()));
            }
            Messages.info(sender, "list.details-hint");
        }
        if (!areas.isEmpty()) {
            Messages.info(sender, "area.list-header", "count", areas.size());
            for (NamedArea area : areas) {
                Messages.namedField(sender, area.name(), Text.of("area.list-entry", "count", area.chunks().size(), "world", area.world()));
            }
        }
    }

    private void listChunks(CommandSender sender, String world, int page) {
        List<KeptChunk> chunks = plugin.store().inWorld(world).stream()
                .sorted(Comparator.comparingInt(KeptChunk::x).thenComparingInt(KeptChunk::z))
                .toList();
        if (chunks.isEmpty()) {
            Messages.error(sender, "list.no-singles-in-world", "world", world);
            return;
        }
        int pages = (chunks.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page < 1 || page > pages) {
            Messages.error(sender, "list.no-such-page", "page", page, "pages", pages);
            return;
        }
        Messages.info(sender, "list.page-header", "world", world, "page", page, "pages", pages);
        int from = (page - 1) * PAGE_SIZE;
        for (KeptChunk chunk : chunks.subList(from, Math.min(from + PAGE_SIZE, chunks.size()))) {
            Messages.namedField(sender, Messages.text(sender, "list.chunk-label", "x", chunk.x(), "z", chunk.z()),
                    Text.of("list.chunk-entry", "x", chunk.x() * 16, "z", chunk.z() * 16));
        }
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.copyOf(plugin.store().worlds());
        }
        return List.of();
    }
}
