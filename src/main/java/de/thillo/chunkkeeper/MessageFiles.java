package de.thillo.chunkkeeper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the messages: the texts built into the jar, overlaid with the files in
 * {@code plugins/ChunkKeeper/messages/}. Those files are copied there on the first start, so
 * server owners can change texts or add another language (e.g. fr.yml). Unchanged copies get the
 * texts of newer versions, see {@link MessageFileUpdater}.
 */
final class MessageFiles {

    static final List<String> BUILT_IN = List.of("en", "de");

    private MessageFiles() {
    }

    static Translations load(JavaPlugin plugin) {
        File folder = new File(plugin.getDataFolder(), "messages");
        Map<String, Map<String, String>> languages = new HashMap<>();
        Path checksumFile = folder.toPath().resolve(".checksums.properties");
        Properties checksums = readChecksums(plugin, checksumFile);

        for (String language : BUILT_IN) {
            String resource = "messages/" + language + ".yml";
            try (InputStream in = plugin.getResource(resource)) {
                if (in == null) {
                    continue;
                }
                byte[] builtIn = in.readAllBytes();
                languages.put(language, new HashMap<>(Translations.parse(
                        new InputStreamReader(new ByteArrayInputStream(builtIn), StandardCharsets.UTF_8))));
                Files.createDirectories(folder.toPath());
                report(plugin, language, MessageFileUpdater.update(folder.toPath().resolve(language + ".yml"), builtIn, checksums));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set up messages/" + language + ".yml", e);
            }
        }
        saveChecksums(plugin, checksumFile, checksums);

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

    private static void report(JavaPlugin plugin, String language, MessageFileUpdater.Result result) {
        switch (result) {
            case UPDATED -> plugin.getLogger().info("Updated messages/" + language + ".yml with the texts of this version.");
            case KEPT_CHANGES -> plugin.getLogger().info("messages/" + language + ".yml has your own changes, so it was not updated. "
                    + "The texts of this version are in messages/" + language + ".yml.new; copy over what you want.");
            default -> {
                // nothing worth telling
            }
        }
    }

    private static Properties readChecksums(JavaPlugin plugin, Path file) {
        Properties checksums = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                checksums.load(reader);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + file + ": " + e.getMessage());
            }
        }
        return checksums;
    }

    private static void saveChecksums(JavaPlugin plugin, Path file, Properties checksums) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            checksums.store(writer, "Written by ChunkKeeper: fingerprints of the message files it copied, to see whether you changed them");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + file + ": " + e.getMessage());
        }
    }
}
