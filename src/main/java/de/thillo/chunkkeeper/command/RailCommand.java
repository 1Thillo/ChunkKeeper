package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Origin;
import de.thillo.chunkkeeper.NamedArea;
import de.thillo.chunkkeeper.Text;
import de.thillo.chunkkeeper.rail.RailTracer;

/**
 * {@code keeprail} and {@code releaserail}: follows a railway from a rail block and keeps (or
 * releases) every chunk along it, including the chunks right next to the track. With
 * {@code --name} the railway becomes a named area.
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
        return keep ? "(current | coords <x> <y> <z> <world>) [--name <name>]" : "(current | coords <x> <y> <z> <world>)";
    }


    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Arguments.Named> named = Arguments.named(sender, args);
        if (named.isEmpty()) {
            return;
        }
        if (!keep && named.get().name().isPresent()) {
            Messages.error(sender, "general.use-area-release");
            return;
        }
        Optional<String> areaName = named.get().name();
        if (areaName.isPresent() && plugin.store().area(areaName.get()).isPresent()) {
            Messages.error(sender, "general.area-exists", "name", areaName.get());
            return;
        }
        Optional<Location> start = startPosition(sender, named.get().rest());
        if (start.isEmpty()) {
            return;
        }
        Location at = start.get();
        World world = at.getWorld();
        Messages.info(sender, "rail.following", "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ(), "world", world.getName());

        Text railway = Text.of("describe.railway", "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ(), "world", world.getName());
        new RailTracer(plugin, world, plugin.settings().railSearchLimit(), result -> {
            if (!result.startFound()) {
                Messages.error(sender, "rail.no-rail");
                return;
            }
            if (result.limitHit()) {
                Messages.error(sender, "rail.limit-hit", "rails", result.rails());
            }
            int total = result.chunks().size();
            if (areaName.isPresent()) {
                String description = Messages.stored(railway);
                Optional<Integer> newlyLoaded = plugin.keeper().createArea(
                        new NamedArea(areaName.get(), world.getName(), result.chunks(), description, Origin.now(sender.getName())));
                if (newlyLoaded.isEmpty()) {
                    Messages.error(sender, "general.area-exists-short", "name", areaName.get());
                } else {
                    Messages.success(sender, "rail.area-created", "rails", result.rails(), "name", areaName.get(),
                            "count", total, "new", newlyLoaded.get());
                    plugin.recordChange(sender, "history.area-created", "name", areaName.get(), "count", total, "source", railway);
                }
            } else if (keep) {
                int added = plugin.keeper().keep(result.chunks(), Origin.now(sender.getName()));
                Messages.success(sender, "rail.kept", "rails", result.rails(), "added", added, "total", total, "already", total - added);
                if (added > 0) {
                    plugin.recordChange(sender, "history.kept-chunks", "count", added, "area", railway);
                }
            } else {
                int removed = plugin.keeper().release(result.chunks());
                Messages.success(sender, "rail.released", "rails", result.rails(), "removed", removed, "total", total);
                if (removed > 0) {
                    plugin.recordChange(sender, "history.released-chunks", "count", removed, "area", railway);
                }
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
        if (keep) {
            Optional<List<String>> nameOption = Arguments.nameSuggestions(args);
            if (nameOption.isPresent()) {
                return nameOption.get();
            }
        }
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
