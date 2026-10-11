package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.rail.RailTracer;

/**
 * {@code keeprail} and {@code releaserail}: follows a railway from a rail block and keeps (or
 * releases) every chunk along it, including the chunks right next to the track.
 */
final class RailCommand implements Subcommand {

    private final ChunkKeeper plugin;
    private final boolean keep;

    RailCommand(ChunkKeeper plugin, boolean keep) {
        this.plugin = plugin;
        this.keep = keep;
    }

    @Override
    public String name() {
        return keep ? "keeprail" : "releaserail";
    }

    @Override
    public String usage() {
        return "(current | coords <x> <y> <z> <world>)";
    }

    @Override
    public String description() {
        return keep ? "Keep the chunks along a railway loaded." : "Stop keeping the chunks along a railway loaded.";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Location> start = startPosition(sender, args);
        if (start.isEmpty()) {
            return;
        }
        Location at = start.get();
        World world = at.getWorld();
        sender.sendMessage(Messages.info("Following the rails from " + at.getBlockX() + ", " + at.getBlockY() + ", "
                + at.getBlockZ() + " in '" + world.getName() + "'..."));

        new RailTracer(plugin, world, plugin.settings().railSearchLimit(), result -> {
            if (!result.startFound()) {
                sender.sendMessage(Messages.error("There is no rail at that position. Stand on the track or give its coordinates."));
                return;
            }
            if (result.limitHit()) {
                sender.sendMessage(Messages.error("Stopped after " + result.rails()
                        + " rails (rail-search-limit in config.yml). Only the part found so far is used."));
            }
            int total = result.chunks().size();
            if (keep) {
                int added = plugin.keeper().keep(result.chunks());
                sender.sendMessage(Messages.success("Found " + result.rails() + " rails. Keeping " + added + " more chunks loaded ("
                        + total + " along the track, " + (total - added) + " were kept already)."));
            } else {
                int removed = plugin.keeper().release(result.chunks());
                sender.sendMessage(Messages.success("Found " + result.rails() + " rails. Released " + removed + " of the "
                        + total + " chunks along the track."));
            }
        }).start(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private Optional<Location> startPosition(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("current")) {
            // A player standing on a rail is inside the rail block, so their own position is the start.
            return Arguments.player(sender).map(Player::getLocation);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("coords")) {
            Optional<Integer> x = Arguments.integer(sender, args[1]);
            Optional<Integer> y = x.isPresent() ? Arguments.integer(sender, args[2]) : Optional.empty();
            Optional<Integer> z = y.isPresent() ? Arguments.integer(sender, args[3]) : Optional.empty();
            Optional<World> world = z.isPresent() ? Arguments.world(sender, args[4]) : Optional.empty();
            return world.map(w -> new Location(w, x.get(), y.get(), z.get()));
        }
        Arguments.sendUsage(sender, name(), usage());
        return Optional.empty();
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("current", "coords");
        }
        if (args[0].equalsIgnoreCase("coords")) {
            int position = args.length - 1;
            // Unlike the other commands these are block coordinates, so a player gets their own position.
            if (sender instanceof Player player && position >= 1 && position <= 3) {
                Location at = player.getLocation();
                int[] values = {at.getBlockX(), at.getBlockY(), at.getBlockZ()};
                return List.of(String.valueOf(values[position - 1]));
            }
            return Arguments.coordSuggestions(sender, "xyzw", position);
        }
        return List.of();
    }
}
