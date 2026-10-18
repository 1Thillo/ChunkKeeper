package de.thillo.chunkkeeper.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import de.thillo.chunkkeeper.BorderView;
import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.Messages;

/**
 * {@code show [<radius> | off]}: draws the outline of the kept chunks around the player with
 * particles. Without arguments it switches the view on or off.
 */
final class ShowCommand implements Subcommand {

    private static final int DEFAULT_RADIUS = 8;

    private final ChunkKeeper plugin;

    ShowCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "show";
    }

    @Override
    public String usage() {
        return "[<radius> | off]";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Optional<Player> player = Arguments.player(sender);
        if (player.isEmpty()) {
            return;
        }
        BorderView view = plugin.borderView();
        int largest = plugin.settings().showMaxRadius();
        if (args.length == 0 && view.isShowing(player.get()) || args.length == 1 && args[0].equalsIgnoreCase("off")) {
            view.stop(player.get());
            Messages.info(sender, "show.stopped");
            return;
        }
        if (args.length > 1) {
            Arguments.sendUsage(sender, name(), usage());
            return;
        }
        int radius = Math.min(DEFAULT_RADIUS, largest);
        if (args.length == 1) {
            Optional<Integer> given = Arguments.integer(sender, args[0]);
            if (given.isEmpty()) {
                return;
            }
            if (given.get() < 0 || given.get() > largest) {
                Messages.error(sender, "show.radius-out-of-range", "largest", largest);
                return;
            }
            radius = given.get();
        }
        if (view.start(player.get(), radius)) {
            Messages.success(sender, "show.started", "radius", radius, "seconds", BorderView.SECONDS);
            if (view.keptNearby(player.get(), radius) == 0) {
                Messages.info(sender, "show.nothing-near", "radius", radius);
            }
        }
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        return args.length == 1 ? List.of("off", "4", "8", "16") : List.of();
    }
}
