package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class TranslationsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9]+)}");

    private static Map<String, String> builtIn(String language) throws IOException {
        try (InputStream in = TranslationsTest.class.getResourceAsStream("/messages/" + language + ".yml")) {
            assertTrue(in != null, "messages/" + language + ".yml is missing");
            return Translations.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * YAML only warns about a key that appears twice and quietly uses the last one, so a doubled
     * key would go unnoticed. This reads the files line by line and fails instead.
     */
    @Test
    void noKeyAppearsTwice() throws IOException {
        for (String language : new String[] {"en", "de"}) {
            Set<String> seen = new TreeSet<>();
            Set<String> doubled = new TreeSet<>();
            String section = "";
            try (InputStream in = TranslationsTest.class.getResourceAsStream("/messages/" + language + ".yml")) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (line.isBlank() || line.trim().startsWith("#") || !line.contains(":")) {
                        continue;
                    }
                    String key = line.trim().substring(0, line.trim().indexOf(':'));
                    if (!line.startsWith(" ")) {
                        section = key;
                    }
                    String path = line.startsWith(" ") ? section + "." + key : key;
                    if (!seen.add(path)) {
                        doubled.add(path);
                    }
                }
            }
            assertEquals(Set.of(), doubled, "doubled keys in " + language + ".yml");
        }
    }

    @Test
    void germanHasExactlyTheEnglishKeys() throws IOException {
        assertEquals(new TreeSet<>(builtIn("en").keySet()), new TreeSet<>(builtIn("de").keySet()));
    }

    @Test
    void germanUsesTheSamePlaceholders() throws IOException {
        Map<String, String> english = builtIn("en");
        Map<String, String> german = builtIn("de");
        for (String key : english.keySet()) {
            assertEquals(placeholders(english.get(key)), placeholders(german.get(key)), "placeholders of " + key);
        }
    }

    /** Catches typos: every key that the code asks for has to exist in the English file. */
    @Test
    void everyKeyUsedInTheCodeExists() throws IOException {
        Set<String> english = builtIn("en").keySet();
        Pattern keyUse = Pattern.compile(
                "(?:Messages\\.(?:success|error|info|field|yesNo|text)\\(\\s*\\w+,\\s*|Text\\.of\\()\"([a-z0-9-]+\\.[a-z0-9.-]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = keyUse.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (!english.contains(matcher.group(1))) {
                        missing.add(matcher.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void everySubcommandHasAHelpText() throws IOException {
        Set<String> english = builtIn("en").keySet();
        for (String subcommand : new String[] {"keepchunk", "releasechunk", "keepregion", "releaseregion", "keeprail",
                "releaserail", "area", "releaseall", "list", "chunkinfo", "show", "reload", "import", "version"}) {
            assertTrue(english.contains("help." + subcommand), "help." + subcommand);
        }
    }

    @Test
    void fillsPlaceholdersAndNestedTexts() {
        Translations translations = new Translations(Map.of(
                "en", Map.of("chunk.kept", "Keeping chunk {chunk} loaded.", "describe.chunk", "({x}, {z}) in '{world}'"),
                "de", Map.of("chunk.kept", "Chunk {chunk} wird jetzt geladen gehalten.", "describe.chunk", "({x}, {z}) in „{world}“")));
        Text text = Text.of("chunk.kept", "chunk", new KeptChunk("world", 3, -4).describe());
        assertEquals("Keeping chunk (3, -4) in 'world' loaded.", translations.format("en", text));
        assertEquals("Chunk (3, -4) in „world“ wird jetzt geladen gehalten.", translations.format("de", text));
    }

    @Test
    void choosesSingularOrPlural() {
        Translations translations = new Translations(Map.of("en", Map.of("list.header", "{count} {count|chunk is|chunks are} kept")));
        assertEquals("1 chunk is kept", translations.format("en", Text.of("list.header", "count", 1)));
        assertEquals("2 chunks are kept", translations.format("en", Text.of("list.header", "count", 2)));
        assertEquals("0 chunks are kept", translations.format("en", Text.of("list.header", "count", 0L)));
    }

    /** A typo like {cuont|chunk|chunks} would always pick the plural, so every form must name a placeholder of its text. */
    @Test
    void pluralFormsNameAPlaceholderOfTheirText() throws IOException {
        Pattern plural = Pattern.compile("\\{([a-z0-9]+)\\|");
        for (String language : new String[] {"en", "de"}) {
            for (Map.Entry<String, String> text : builtIn(language).entrySet()) {
                Matcher matcher = plural.matcher(text.getValue());
                while (matcher.find()) {
                    assertTrue(placeholders(text.getValue()).contains(matcher.group(1)),
                            language + " " + text.getKey() + ": {" + matcher.group(1) + "|...} without {" + matcher.group(1) + "}");
                }
            }
        }
    }

    @Test
    void fallsBackToEnglishAndThenToTheKey() {
        Translations translations = new Translations(Map.of("en", Map.of("only.english", "hello"), "de", Map.of()));
        assertEquals("hello", translations.format("de", Text.of("only.english")));
        assertEquals("hello", translations.format("fr", Text.of("only.english")));
        assertEquals("no.such.key", translations.format("de", Text.of("no.such.key")));
    }
}
