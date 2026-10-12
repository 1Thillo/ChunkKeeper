package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;

/**
 * {@code keepchunk} and {@code releasechunk}: one chunk, either the one you stand in or one given by
 * chunk coordinates. Both are the same class because they only differ in the direction.
 */
final class ChunkCommand implements Subcommand {

    private final ChunkKeeper plugin;
    private final boolean keep;

    ChunkCommand(ChunkKeeper plugin, boolean keep) {
        this.plugin = plugin;
        this.keep = keep;
    }

    @Override
    public String name() {
        return keep ? "keepchunk" : "releasechunk";
    }

    @Override
    public String usage() {
        return Arguments.CHUNK_USAGE;
    }

    @Override
    public String description() {
        return keep ? "Keep one chunk loaded." : "Stop keeping one chunk loaded.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<KeptChunk> chunk = Arguments.chunk(sender, args, name());
        if (chunk.isEmpty()) {
            return;
        }
        if (keep) {
            if (plugin.keeper().keep(List.of(chunk.get())) == 0) {
                sender.sendMessage(Messages.error("Chunk " + chunk.get().describe() + " is already kept on its own."));
            } else {
                sender.sendMessage(Messages.success("Keeping chunk " + chunk.get().describe() + " loaded."));
            }
        } else {
            boolean released = plugin.keeper().release(List.of(chunk.get())) > 0;
            List<String> areas = plugin.store().areasContaining(chunk.get());
            if (released) {
                sender.sendMessage(Messages.success("Released chunk " + chunk.get().describe() + "."));
            } else if (areas.isEmpty()) {
                sender.sendMessage(Messages.error("Chunk " + chunk.get().describe() + " was not kept loaded."));
            } else {
                sender.sendMessage(Messages.error("Chunk " + chunk.get().describe() + " is not kept on its own."));
            }
            if (!areas.isEmpty()) {
                sender.sendMessage(Messages.info("It stays loaded through the named area(s) " + String.join(", ", areas)
                        + ". Use /chunkkeeper area release <name> to release an area."));
            }
        }
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("current", "coords");
        }
        if (args[0].equalsIgnoreCase("coords")) {
            return Arguments.coordSuggestions(sender, "xzw", args.length - 1);
        }
        return List.of();
    }
}
