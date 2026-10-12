package de.thillo.chunkkeeper.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.NamedArea;

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
    public String description() {
        return "Show the kept chunks.";
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
            sender.sendMessage(Messages.info("No chunks are kept loaded."));
            return;
        }
        if (plugin.store().size() > 0) {
            sender.sendMessage(Messages.info(plugin.store().size() + " chunks are kept loaded:"));
            for (String world : plugin.store().worlds()) {
                sender.sendMessage(Messages.field(world, plugin.store().inWorld(world).size() + " chunks"));
            }
            sender.sendMessage(Messages.info("Details: /chunkkeeper list <world> [page]"));
        }
        if (!areas.isEmpty()) {
            sender.sendMessage(Messages.info(areas.size() + " named areas:"));
            for (NamedArea area : areas) {
                sender.sendMessage(Messages.field(area.name(), area.chunks().size() + " chunks in '" + area.world() + "'"));
            }
        }
    }

    private void listChunks(CommandSender sender, String world, int page) {
        List<KeptChunk> chunks = plugin.store().inWorld(world).stream()
                .sorted(Comparator.comparingInt(KeptChunk::x).thenComparingInt(KeptChunk::z))
                .toList();
        if (chunks.isEmpty()) {
            sender.sendMessage(Messages.error("No single chunks are kept in world '" + world + "'. Named areas: /chunkkeeper area list"));
            return;
        }
        int pages = (chunks.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page < 1 || page > pages) {
            sender.sendMessage(Messages.error("Page " + page + " does not exist. There are " + pages + " pages."));
            return;
        }
        sender.sendMessage(Messages.info("Kept chunks in '" + world + "', page " + page + " of " + pages + ":"));
        int from = (page - 1) * PAGE_SIZE;
        for (KeptChunk chunk : chunks.subList(from, Math.min(from + PAGE_SIZE, chunks.size()))) {
            sender.sendMessage(Messages.field("Chunk (" + chunk.x() + ", " + chunk.z() + ")",
                    "blocks " + (chunk.x() * 16) + ", " + (chunk.z() * 16)));
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
