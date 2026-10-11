package de.thillo.chunkkeeper;

import org.bukkit.configuration.file.FileConfiguration;

/** The values from config.yml. Create a new instance after reloading the config. */
public record Settings(
        boolean releaseAllConsoleOnly,
        boolean preventWorldUnload,
        boolean legacyPermissions,
        int railSearchLimit) {

    public static Settings from(FileConfiguration config) {
        return new Settings(
                config.getBoolean("release-all-console-only", true),
                config.getBoolean("prevent-world-unload", true),
                config.getBoolean("legacy-permissions", true),
                Math.max(1, config.getInt("rail-search-limit", 50000)));
    }
}
