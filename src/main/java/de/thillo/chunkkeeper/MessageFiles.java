package de.thillo.chunkkeeper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the messages: the texts built into the jar, overlaid with the files in
 * {@code plugins/ChunkKeeper/messages/}. Those files are copied there on the first start, so
 * server owners can change texts or add another language (e.g. fr.yml).
 */
final class MessageFiles {

    static final List<String> BUILT_IN = List.of("en", "de");

    private MessageFiles() {
    }

    static Translations load(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "messages");
        Map<String, Map<String, String>> languages = new HashMap<>();

        for (String language : BUILT_IN) {
            String resource = "messages/" + language + ".yml";
            if (!new File(folder, language + ".yml").exists()) {
                plugin.saveResource(resource, false);
            }
            try (InputStream in = plugin.getResource(resource)) {
                if (in != null) {
                    languages.put(language, new HashMap<>(Translations.parse(new InputStreamReader(in, StandardCharsets.UTF_8))));
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the built-in " + resource, e);
            }
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        for (File file : files == null ? new File[0] : files) {
            String language = file.getName().substring(0, file.getName().length() - ".yml".length());
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                // texts from the file win, missing keys keep the built-in text
                languages.computeIfAbsent(language, key -> new HashMap<>()).putAll(Translations.parse(reader));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Skipping " + file.getPath() + ": " + e.getMessage());
            }
        }
        return new Translations(languages);
    }
}
