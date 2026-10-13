package de.thillo.chunkkeeper.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

import de.thillo.chunkkeeper.ChunkKeeper;
import de.thillo.chunkkeeper.ChunkStore;
import de.thillo.chunkkeeper.Messages;
import de.thillo.chunkkeeper.Text;

/**
 * {@code backup list | create | restore <name> [confirm]}: copies of chunks.yml. ChunkKeeper also
 * makes one on its own before releaseall, import and restore.
 */
final class BackupCommand implements Subcommand {

    private static final List<String> ACTIONS = List.of("list", "create", "restore");

    private final ChunkKeeper plugin;

    BackupCommand(ChunkKeeper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "backup";
    }

    @Override
    public String usage() {
        return "(list | create | restore <name> [confirm])";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase();
        switch (action) {
            case "list" -> list(sender);
            case "create" -> create(sender);
            case "restore" -> {
                if (args.length == 2 || (args.length == 3 && args[2].equalsIgnoreCase("confirm"))) {
                    restore(sender, args[1].toLowerCase(), args.length == 3);
                } else {
                    Arguments.sendUsage(sender, name(), "restore <name> [confirm]");
                }
            }
            default -> Arguments.sendUsage(sender, name(), usage());
        }
    }

    private void list(CommandSender sender) {
        List<String> names;
        try {
            names = plugin.backups().list();
        } catch (IOException e) {
            failed(sender, e);
            return;
        }
        if (names.isEmpty()) {
            Messages.info(sender, "backup.none");
            return;
        }
        Messages.info(sender, "backup.list-header", "count", names.size());
        for (String backup : names) {
            Messages.namedField(sender, backup, contents(backup));
        }
    }

    private void create(CommandSender sender) {
        Optional<String> backup = plugin.backupNow("manual");
        if (backup.isEmpty()) {
            Messages.error(sender, "backup.nothing-to-save");
            return;
        }
        Messages.success(sender, "backup.created", "name", backup.get());
        plugin.recordChange(sender, "history.backup-created", "name", backup.get());
    }

    private void restore(CommandSender sender, String backup, boolean confirmed) {
        if (plugin.backups().find(backup).isEmpty()) {
            Messages.error(sender, "backup.unknown", "name", backup);
            return;
        }
        if (!confirmed) {
            Messages.info(sender, "backup.confirm-restore", "name", backup, "contents", contents(backup));
            return;
        }
        Optional<String> previous = plugin.backupNow("before-restore");
        try {
            plugin.backups().restore(backup);
        } catch (IOException e) {
            failed(sender, e);
            return;
        }
        plugin.reloadChunks();
        Messages.success(sender, "backup.restored", "name", backup,
                "chunks", plugin.store().size(), "areas", plugin.store().areas().size(), "previous", previous.orElse("-"));
        plugin.recordChange(sender, "history.backup-restored", "name", backup);
    }

    /** What a backup holds, e.g. "12 single chunks, 2 named areas". */
    private Text contents(String backup) {
        Optional<Path> file = plugin.backups().find(backup);
        if (file.isEmpty()) {
            return Text.of("general.none");
        }
        ChunkStore copy = new ChunkStore(new File(file.get().toString()), Logger.getAnonymousLogger());
        copy.load();
        return Text.of("backup.contents", "chunks", copy.size(), "areas", copy.areas().size());
    }

    private void failed(CommandSender sender, IOException e) {
        Messages.error(sender, "backup.failed", "error", e.getMessage());
        plugin.getLogger().warning("Backup action failed: " + e);
    }

    @Override
    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        if (args[0].equalsIgnoreCase("restore")) {
            if (args.length == 2) {
                try {
                    return plugin.backups().list();
                } catch (IOException e) {
                    return List.of();
                }
            }
            if (args.length == 3) {
                return List.of("confirm");
            }
        }
        return List.of();
    }
}
