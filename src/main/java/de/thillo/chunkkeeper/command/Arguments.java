package de.thillo.chunkkeeper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.thillo.chunkkeeper.Area;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.KeptChunk;
import de.thillo.chunkkeeper.Messages;

/** Small helpers for reading command arguments. They tell the sender what went wrong. */
final class Arguments {

    static final String CHUNK_USAGE = "(current | coords <x> <z> <world>)";
    static final String AREA_USAGE = "(coords <x1> <z1> <x2> <z2> <world> | worldedit | worldguard <region> <world>)";

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

    /**
     * Reads {@code coords <x1> <z1> <x2> <z2> <world>} (chunk coordinates), {@code worldedit} or
     * {@code worldguard <region> <world>}. Sends the usage if the arguments do not fit, and refuses
     * areas larger than max-area-chunks from config.yml, so a typo cannot load millions of chunks.
     */
    static Optional<Area> area(ChunkKeeper plugin, CommandSender sender, String[] args, String command) {
        Optional<Area> area = readArea(plugin, sender, args, command);
        int limit = plugin.settings().maxAreaChunks();
        if (area.isPresent() && area.get().chunks().size() > limit) {
            tooLarge(sender, area.get().chunks().size(), limit);
            return Optional.empty();
        }
        return area;
    }

    private static Optional<Area> readArea(ChunkKeeper plugin, CommandSender sender, String[] args, String command) {
        String mode = args.length == 0 ? "" : args[0].toLowerCase();
        if (mode.equals("coords") && args.length == 6) {
            List<Integer> numbers = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                Optional<Integer> number = integer(sender, args[i]);
                if (number.isEmpty()) {
                    return Optional.empty();
                }
                numbers.add(number.get());
            }
            long size = Area.rectangleSize(numbers.get(0), numbers.get(1), numbers.get(2), numbers.get(3));
            if (size > plugin.settings().maxAreaChunks()) {
                // checked before building the list, which could be huge
                tooLarge(sender, size, plugin.settings().maxAreaChunks());
                return Optional.empty();
            }
            return world(sender, args[5]).map(world ->
                    Area.rectangle(world.getName(), numbers.get(0), numbers.get(1), numbers.get(2), numbers.get(3)));
        }
        if (mode.equals("worldedit") && args.length == 1) {
            Optional<Player> player = player(sender);
            if (player.isEmpty()) {
                return Optional.empty();
            }
            if (plugin.hooks().worldEdit().isEmpty()) {
                sender.sendMessage(Messages.error("WorldEdit or FastAsyncWorldEdit is not installed."));
                return Optional.empty();
            }
            return plugin.hooks().worldEdit().get().selection(player.get());
        }
        if (mode.equals("worldguard") && args.length == 3) {
            if (plugin.hooks().worldGuard().isEmpty()) {
                sender.sendMessage(Messages.error("WorldGuard is not installed."));
                return Optional.empty();
            }
            Optional<World> world = world(sender, args[2]);
            return world.flatMap(w -> plugin.hooks().worldGuard().get().region(sender, w, args[1]));
        }
        sendUsage(sender, command, AREA_USAGE);
        return Optional.empty();
    }

    private static void tooLarge(CommandSender sender, long size, int limit) {
        sender.sendMessage(Messages.error("That area has " + size + " chunks, more than the limit of " + limit
                + " (max-area-chunks in config.yml)."));
    }

    /** Tab completion for the area modes. */
    static List<String> areaSuggestions(ChunkKeeper plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> modes = new ArrayList<>(List.of("coords"));
            plugin.hooks().worldEdit().ifPresent(hook -> modes.add("worldedit"));
            plugin.hooks().worldGuard().ifPresent(hook -> modes.add("worldguard"));
            return modes;
        }
        String mode = args[0].toLowerCase();
        if (mode.equals("coords")) {
            return coordSuggestions(sender, "xzxzw", args.length - 1);
        }
        if (mode.equals("worldguard") && plugin.hooks().worldGuard().isPresent()) {
            if (args.length == 2) {
                World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
                return plugin.hooks().worldGuard().get().regionNames(world);
            }
            if (args.length == 3) {
                return worldNames();
            }
        }
        return List.of();
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
