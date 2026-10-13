package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;

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
    public void execute(CommandSender sender, String[] args) {
        Optional<KeptChunk> chunk = Arguments.chunk(sender, args, name());
        if (chunk.isEmpty()) {
            return;
        }
        if (keep) {
            if (plugin.keeper().keep(List.of(chunk.get()), Origin.now(sender.getName())) == 0) {
                Messages.error(sender, "chunk.already-kept", "chunk", chunk.get().describe());
            } else {
                Messages.success(sender, "chunk.kept", "chunk", chunk.get().describe());
                plugin.recordChange(sender, "history.kept-chunk", "chunk", chunk.get().describe());
            }
        } else {
            boolean released = plugin.keeper().release(List.of(chunk.get())) > 0;
            List<String> areas = plugin.store().areasContaining(chunk.get());
            if (released) {
                Messages.success(sender, "chunk.released", "chunk", chunk.get().describe());
                plugin.recordChange(sender, "history.released-chunk", "chunk", chunk.get().describe());
            } else if (areas.isEmpty()) {
                Messages.error(sender, "chunk.not-kept", "chunk", chunk.get().describe());
            } else {
                Messages.error(sender, "chunk.not-kept-on-its-own", "chunk", chunk.get().describe());
            }
            if (!areas.isEmpty()) {
                Messages.info(sender, "chunk.stays-through-areas", "areas", String.join(", ", areas));
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
