package de.thillo.chunkkeeper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import de.thillo.chunkkeeper.command.ChunkKeeperCommand;
import de.thillo.chunkkeeper.hook.Hooks;
import de.thillo.chunkkeeper.importer.KeepChunksImporter;

public final class ChunkKeeper extends JavaPlugin {

    private ChunkStore store;
    private ChunkLoader loader;
    // volatile: /chunkkeeper reload replaces it while other threads may be reading it (Folia)
    private volatile Settings settings;
    private Permissions permissions;
    private Keeper keeper;
    private BorderView borderView;
    private KeepChunksImporter importer;
    private Hooks hooks;
    private History history;
    private Backups backups;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        addMissingConfigOptions();
        store = new ChunkStore(new File(getDataFolder(), "chunks.yml"), getLogger());
        loader = new ChunkLoader(this);
        keeper = new Keeper(store, loader);
        borderView = new BorderView(this);
        permissions = new Permissions(this);
        importer = new KeepChunksImporter(getDataFolder().getParentFile());
        hooks = new Hooks();
        history = new History(new File(getDataFolder(), "history.log").toPath(), getLogger());

        // No chunks.yml yet means this is the first start: take over what KeepChunks had.
        Optional<KeepChunksImporter.Result> imported = store.fileExists() ? Optional.empty() : importer.read();
        imported.flatMap(KeepChunksImporter.Result::releaseAllProtection).ifPresent(protection -> {
            getConfig().set("release-all-console-only", protection);
            saveConfig();
        });
        settings = Settings.from(getConfig());
        loadMessages();
        backups = new Backups(new File(getDataFolder(), "chunks.yml").toPath(), new File(getDataFolder(), "backups").toPath(),
                settings.backupsToKeep());

        store.load();
        // Import first, so the count below includes the imported chunks.
        imported.ifPresent(this::finishImport);
        keepAllLoaded();
        if (!store.fileExists()) {
            store.save();
        }
        warnIfKeepChunksIsInstalled();

        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        registerCommand();
    }

    @Override
    public void onDisable() {
        // PlugMan reloads: particles from the old plugin instance must not keep running
        if (borderView != null) {
            borderView.stopAll();
        }
    }

    private void loadMessages() {
        Messages.configure(MessageFiles.load(this), settings.language(), settings.consoleLanguage());
    }

    /** Servers updating from an older version get the options that are new in their config.yml. */
    private void addMissingConfigOptions() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void registerCommand() {
        PluginCommand command = Objects.requireNonNull(getCommand("chunkkeeper"), "chunkkeeper missing in plugin.yml");
        ChunkKeeperCommand handler = new ChunkKeeperCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        command.setPermission(handler.visibilityPermissions(settings.legacyPermissions()));
    }

    private void finishImport(KeepChunksImporter.Result result) {
        int added = keeper.keep(result.chunks(), Origin.now("KeepChunks import"));
        history.record("KeepChunks import", Messages.stored(Text.of("history.imported", "count", added)));
        getLogger().info("First start: imported " + added + " chunks from " + importer.dataFile().getPath() + ".");
        if (!result.unreadable().isEmpty()) {
            getLogger().warning("Could not read these KeepChunks entries: " + result.unreadable());
        }
    }

    private void warnIfKeepChunksIsInstalled() {
        if (Bukkit.getPluginManager().getPlugin("KeepChunks") != null) {
            getLogger().warning("KeepChunks is still installed. ChunkKeeper has taken over its chunks; "
                    + "please remove KeepChunks so the two plugins do not manage the same chunks. "
                    + "Use /chunkkeeper import to pick up chunks KeepChunks added in the meantime.");
        }
    }

    /** Force-loads every kept chunk (single chunks and named areas) whose world is currently loaded. */
    void keepAllLoaded() {
        List<KeptChunk> loadable = new ArrayList<>();
        int missingWorlds = 0;
        for (KeptChunk chunk : store.allKept()) {
            if (Bukkit.getWorld(chunk.world()) == null) {
                missingWorlds++;
            } else {
                loadable.add(chunk);
            }
        }
        loader.keepLoaded(loadable);
        getLogger().info("Keeping " + loadable.size() + " chunks loaded.");
        if (missingWorlds > 0) {
            getLogger().info(missingWorlds + " chunks belong to worlds that are not loaded yet; they follow when their world loads.");
        }
    }

    /**
     * Reads config.yml, the messages and chunks.yml again. Chunks that were removed from the file are released,
     * all others are force-loaded (again).
     */
    public void reload() {
        reloadConfig();
        addMissingConfigOptions();
        settings = Settings.from(getConfig());
        loadMessages();
        backups = new Backups(new File(getDataFolder(), "chunks.yml").toPath(), new File(getDataFolder(), "backups").toPath(),
                settings.backupsToKeep());
        reloadChunks();
    }

    /** Reads chunks.yml again: chunks that are gone are released, all others are force-loaded (again). */
    public void reloadChunks() {
        Set<KeptChunk> before = store.allKept();
        store.load();
        List<KeptChunk> removed = before.stream().filter(chunk -> !store.isKept(chunk)).toList();
        loader.release(removed);
        keepAllLoaded();
    }

    public ChunkStore store() {
        return store;
    }

    public ChunkLoader loader() {
        return loader;
    }

    public Settings settings() {
        return settings;
    }

    public Permissions permissions() {
        return permissions;
    }

    public BorderView borderView() {
        return borderView;
    }

    public Keeper keeper() {
        return keeper;
    }

    public KeepChunksImporter importer() {
        return importer;
    }

    public Hooks hooks() {
        return hooks;
    }

    public History history() {
        return history;
    }

    public Backups backups() {
        return backups;
    }

    /**
     * Saves chunks.yml and copies it to the backups folder.
     *
     * @return the name of the backup, or empty if there was nothing to save or it failed (see the console)
     */
    public Optional<String> backupNow(String reason) {
        store.save();
        try {
            return backups.create(reason);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not make a backup of chunks.yml", e);
            return Optional.empty();
        }
    }

    /** Writes a change to history.log, in the console language, with the sender as the person who made it. */
    public void recordChange(CommandSender who, String key, Object... placeholders) {
        history.record(who.getName(), Messages.stored(Text.of(key, placeholders)));
    }
}
