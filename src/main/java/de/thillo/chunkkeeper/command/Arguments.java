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
import de.thillo.chunkkeeper.NamedArea;

/** Small helpers for reading command arguments. They tell the sender what went wrong. */
final class Arguments {

    static final String CHUNK_USAGE = "(current | coords <x> <z> <world>)";
    static final String AREA_USAGE =
            "(coords <x1> <z1> <x2> <z2> <world> | radius <r> [<x> <z> <world>] | worldedit | worldguard <region> <world>)";

    private Arguments() {
    }

    static Optional<Integer> integer(CommandSender sender, String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            Messages.error(sender, "general.not-a-number", "text", text);
            return Optional.empty();
        }
    }

    static Optional<World> world(CommandSender sender, String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            Messages.error(sender, "general.unknown-world", "world", name);
        }
        return Optional.ofNullable(world);
    }

    static Optional<Player> player(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        Messages.error(sender, "general.players-only");
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
        if (mode.equals("radius") && (args.length == 2 || args.length == 5)) {
            return radius(plugin, sender, args);
        }
        if (mode.equals("worldedit") && args.length == 1) {
            Optional<Player> player = player(sender);
            if (player.isEmpty()) {
                return Optional.empty();
            }
            if (plugin.hooks().worldEdit().isEmpty()) {
                Messages.error(sender, "general.worldedit-missing");
                return Optional.empty();
            }
            return plugin.hooks().worldEdit().get().selection(player.get());
        }
        if (mode.equals("worldguard") && args.length == 3) {
            if (plugin.hooks().worldGuard().isEmpty()) {
                Messages.error(sender, "general.worldguard-missing");
                return Optional.empty();
            }
            Optional<World> world = world(sender, args[2]);
            return world.flatMap(w -> plugin.hooks().worldGuard().get().region(sender, w, args[1]));
        }
        sendUsage(sender, command, AREA_USAGE);
        return Optional.empty();
    }

    /**
     * Reads {@code radius <r>} around the chunk the player stands in, or {@code radius <r> <x> <z> <world>}
     * around the given chunk.
     */
    private static Optional<Area> radius(ChunkKeeper plugin, CommandSender sender, String[] args) {
        Optional<Integer> radius = integer(sender, args[1]);
        if (radius.isEmpty()) {
            return Optional.empty();
        }
        if (radius.get() < 0) {
            Messages.error(sender, "general.negative-radius");
            return Optional.empty();
        }
        int limit = plugin.settings().maxAreaChunks();
        int largest = Area.largestRadius(limit);
        if (radius.get() > largest) {
            Messages.error(sender, "general.radius-too-large", "radius", radius.get(), "limit", limit, "largest", largest);
            return Optional.empty();
        }
        Optional<KeptChunk> center = args.length == 2
                ? player(sender).map(player -> KeptChunk.of(player.getChunk()))
                : chunk(sender, new String[] {"coords", args[2], args[3], args[4]}, "");
        return center.map(chunk -> Area.around(chunk.world(), chunk.x(), chunk.z(), radius.get()));
    }

    private static void tooLarge(CommandSender sender, long size, int limit) {
        Messages.error(sender, "general.area-too-large", "size", size, "limit", limit);
    }

    /** Tab completion for the area modes. */
    static List<String> areaSuggestions(ChunkKeeper plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> modes = new ArrayList<>(List.of("coords", "radius"));
            plugin.hooks().worldEdit().ifPresent(hook -> modes.add("worldedit"));
            plugin.hooks().worldGuard().ifPresent(hook -> modes.add("worldguard"));
            return modes;
        }
        String mode = args[0].toLowerCase();
        if (mode.equals("coords")) {
            return coordSuggestions(sender, "xzxzw", args.length - 1);
        }
        if (mode.equals("radius")) {
            return args.length == 2 ? List.of("1", "2", "3") : coordSuggestions(sender, "rxzw", args.length - 1);
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

    /** The arguments without {@code --name <name>}, and the name if one was given. */
    record Named(String[] rest, Optional<String> name) {
    }

    /**
     * Splits off an optional {@code --name <name>}. Names are turned into lower case.
     * Returns empty (and tells the sender) if the name is missing or not allowed.
     */
    static Optional<Named> named(CommandSender sender, String[] args) {
        List<String> rest = new ArrayList<>();
        String name = null;
        for (int i = 0; i < args.length; i++) {
            if (!args[i].equalsIgnoreCase("--name")) {
                rest.add(args[i]);
                continue;
            }
            if (i + 1 >= args.length) {
                Messages.error(sender, "general.name-missing");
                return Optional.empty();
            }
            name = args[++i].toLowerCase();
            if (!NamedArea.isValidName(name)) {
                Messages.error(sender, "general.invalid-name", "name", args[i]);
                return Optional.empty();
            }
        }
        return Optional.of(new Named(rest.toArray(String[]::new), Optional.ofNullable(name)));
    }

    /** Suggestions for the {@code --name} option: the option itself, or a placeholder after it. */
    static Optional<List<String>> nameSuggestions(String[] args) {
        if (args.length >= 2 && args[args.length - 2].equalsIgnoreCase("--name")) {
            return Optional.of(List.of("<name>"));
        }
        if (args.length >= 1 && args[args.length - 1].startsWith("-")) {
            return Optional.of(List.of("--name"));
        }
        return Optional.empty();
    }

    static void sendUsage(CommandSender sender, String command, String usage) {
        Messages.error(sender, "general.usage", "command", command, "usage", usage);
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
