package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;

/** Small helpers for reading command arguments. They tell the sender what went wrong. */
final class Arguments {

    static final String CHUNK_USAGE = "(current | coords <x> <z> <world>)";

    private Arguments() {
    }

    static Optional<Integer> integer(CommandSender sender, String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            sender.sendMessage(Messages.error("'" + text + "' is not a whole number."));
            return Optional.empty();
        }
    }

    static Optional<World> world(CommandSender sender, String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            sender.sendMessage(Messages.error("World '" + name + "' does not exist or is not loaded."));
        }
        return Optional.ofNullable(world);
    }

    static Optional<Player> player(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        sender.sendMessage(Messages.error("Only players can do that. From the console, use 'coords' instead."));
        return Optional.empty();
    }

    /**
     * Reads {@code current} or {@code coords <x> <z> <world>} (chunk coordinates).
     * Sends the usage if the arguments do not fit.
     */
    static Optional<KeptChunk> chunk(CommandSender sender, String[] args, String command) {
        if (args.length == 1 && args[0].equalsIgnoreCase("current")) {
            return player(sender).map(player -> KeptChunk.of(player.getChunk()));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("coords")) {
            Optional<Integer> x = integer(sender, args[1]);
            Optional<Integer> z = x.isPresent() ? integer(sender, args[2]) : Optional.empty();
            Optional<World> world = z.isPresent() ? world(sender, args[3]) : Optional.empty();
            return world.map(w -> new KeptChunk(w.getName(), x.get(), z.get()));
        }
        sendUsage(sender, command, CHUNK_USAGE);
        return Optional.empty();
    }

    static void sendUsage(CommandSender sender, String command, String usage) {
        sender.sendMessage(Messages.error("Usage: /chunkkeeper " + command + " " + usage));
    }

    /**
     * Suggestions while typing the values after a mode such as {@code coords}.
     *
     * @param pattern  one letter per value: x or z for chunk coordinates (a player gets the chunk they
     *                 stand in), w for a world name, anything else becomes a placeholder like {@code <y>}
     * @param position which value is being typed, counting from 1
     */
    static List<String> coordSuggestions(CommandSender sender, String pattern, int position) {
        if (position < 1 || position > pattern.length()) {
            return List.of();
        }
        char kind = pattern.charAt(position - 1);
        if (kind == 'w') {
            return worldNames();
        }
        if (sender instanceof Player player) {
            Chunk chunk = player.getChunk();
            if (kind == 'x') {
                return List.of(String.valueOf(chunk.getX()));
            }
            if (kind == 'z') {
                return List.of(String.valueOf(chunk.getZ()));
            }
        }
        return List.of("<" + kind + ">");
    }

    static List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }
}
