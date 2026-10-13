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
                "releaserail", "area", "releaseall", "list", "chunkinfo", "reload", "import", "version"}) {
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
    void fallsBackToEnglishAndThenToTheKey() {
        Translations translations = new Translations(Map.of("en", Map.of("only.english", "hello"), "de", Map.of()));
        assertEquals("hello", translations.format("de", Text.of("only.english")));
        assertEquals("hello", translations.format("fr", Text.of("only.english")));
        assertEquals("no.such.key", translations.format("de", Text.of("no.such.key")));
    }
}
