package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;

/** {@code chunkinfo}: whether a chunk is kept, force-loaded and currently loaded. */
final class ChunkInfoCommand implements Subcommand {

    private final ChunkKeeper plugin;

    ChunkInfoCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "chunkinfo";
    }

    @Override
    public String usage() {
        return Arguments.CHUNK_USAGE;
    }

    @Override
    public String description() {
        return "Show whether a chunk is kept loaded.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<KeptChunk> target = Arguments.chunk(sender, args, name());
        if (target.isEmpty()) {
            return;
        }
        KeptChunk chunk = target.get();
        boolean kept = plugin.store().contains(chunk);
        // Asking the world about a chunk has to happen on the thread that owns it (Folia).
        plugin.loader().runAt(chunk, world -> {
            boolean forceLoaded = world.isChunkForceLoaded(chunk.x(), chunk.z());
            sender.sendMessage(Messages.info("Chunk " + chunk.describe()));
            sender.sendMessage(Messages.field("Blocks", "x " + (chunk.x() * 16) + " to " + (chunk.x() * 16 + 15)
                    + ", z " + (chunk.z() * 16) + " to " + (chunk.z() * 16 + 15)));
            sender.sendMessage(Messages.yesNo("Kept by ChunkKeeper", kept));
            sender.sendMessage(Messages.yesNo("Force-loaded", forceLoaded));
            sender.sendMessage(Messages.yesNo("Loaded right now", world.isChunkLoaded(chunk.x(), chunk.z())));
            if (kept != forceLoaded) {
                sender.sendMessage(Messages.info(kept
                        ? "The chunk is kept but not force-loaded yet; this fixes itself within a moment or after /chunkkeeper reload."
                        : "Something else force-loads this chunk, for example the vanilla /forceload command."));
            }
        });
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
