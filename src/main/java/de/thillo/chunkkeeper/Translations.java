package de.thillo.chunkkeeper;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The message texts of all languages. A text is looked up in the wanted language, then in English,
 * and if it is missing there too, the key itself is shown, so a typo never hides a message.
 */
public final class Translations {

    public static final String FALLBACK = "en";

    /** language code (en, de, ...) -> message key -> text with {placeholders} */
    private final Map<String, Map<String, String>> languages;

    public Translations(Map<String, Map<String, String>> languages) {
        this.languages = Map.copyOf(languages);
    }

    /**
     * Reads a messages file into "key -> text", with nested keys joined by dots,
     * e.g. {@code chunk.kept}.
     */
    public static Map<String, String> parse(Reader yaml) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Broken messages file: " + e.getMessage(), e);
        }
        Map<String, String> texts = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                texts.put(key, config.getString(key));
            }
        }
        return texts;
    }

    public boolean supports(String language) {
        return languages.containsKey(language);
    }

    public Set<String> languages() {
        return languages.keySet();
    }

    public Map<String, String> texts(String language) {
        return languages.getOrDefault(language, Map.of());
    }

    /** The text in the given language, with all placeholders filled in. */
    public String format(String language, Text text) {
        String template = texts(language).get(text.key());
        if (template == null) {
            template = texts(FALLBACK).getOrDefault(text.key(), text.key());
        }
        String result = template;
        for (Map.Entry<String, Object> placeholder : text.placeholders().entrySet()) {
            Object value = placeholder.getValue();
            String rendered = value instanceof Text nested ? format(language, nested) : String.valueOf(value);
            result = result.replace("{" + placeholder.getKey() + "}", rendered);
        }
        return result;
    }
}
