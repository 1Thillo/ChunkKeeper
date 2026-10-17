package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void missingValuesFallBackToDefaults() {
        Settings settings = Settings.from(new YamlConfiguration());
        assertEquals(new Settings(true, true, true, 50000, 10000, "auto", "en"), settings);
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
                """);
        assertEquals(new Settings(false, false, false, 123, 456, "de", "de"), Settings.from(config));
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
