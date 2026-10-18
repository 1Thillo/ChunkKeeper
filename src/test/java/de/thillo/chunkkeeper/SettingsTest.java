package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void missingValuesFallBackToDefaults() {
        Settings settings = Settings.from(new YamlConfiguration());
        assertEquals(new Settings(true, true, true, 50000, 10000, "auto", "en", 20, 16), settings);
    }

    @Test
    void readsConfiguredValues() throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                release-all-console-only: false
                prevent-world-unload: false
                legacy-permissions: false
                rail-search-limit: 123
                max-area-chunks: 456
                language: de
                console-language: de
                backups-to-keep: 5
                show-max-radius: 4
                """);
        assertEquals(new Settings(false, false, false, 123, 456, "de", "de", 5, 4), Settings.from(config));
    }

    @Test
    void limitsAreAtLeastOne() throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("rail-search-limit: 0\nmax-area-chunks: -5\n");
        Settings settings = Settings.from(config);
        assertEquals(1, settings.railSearchLimit());
        assertEquals(1, settings.maxAreaChunks());
    }
}
