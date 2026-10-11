package de.thillo.chunkkeeper.hook;

import java.util.Optional;

import org.bukkit.Bukkit;

/**
 * Which optional plugins are installed. The hook objects are only created when their plugin exists,
 * so ChunkKeeper also runs without WorldEdit or WorldGuard.
 */
public final class Hooks {

    private final WorldEditHook worldEdit;
    private final WorldGuardHook worldGuard;

    public Hooks() {
        // FastAsyncWorldEdit registers under its own name and also "provides" WorldEdit
        boolean hasWorldEdit = isEnabled("WorldEdit") || isEnabled("FastAsyncWorldEdit");
        worldEdit = hasWorldEdit ? new WorldEditHook() : null;
        worldGuard = hasWorldEdit && isEnabled("WorldGuard") ? new WorldGuardHook() : null;
    }

    public Optional<WorldEditHook> worldEdit() {
        return Optional.ofNullable(worldEdit);
    }

    public Optional<WorldGuardHook> worldGuard() {
        return Optional.ofNullable(worldGuard);
    }

    private static boolean isEnabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }
}
