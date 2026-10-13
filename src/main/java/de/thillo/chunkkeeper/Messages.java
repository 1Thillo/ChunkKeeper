package de.thillo.chunkkeeper;

import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Sends chat messages in ChunkKeeper's colours (green for success, red for errors, grey for
 * information) and in the language of the receiver.
 *
 * <p>Every message is given as a key from messages/&lt;language&gt;.yml plus placeholder values. With
 * {@code language: auto} a player gets their Minecraft language (if there is a messages file for it),
 * the console gets {@code console-language}.
 */
public final class Messages {

    private static final Component PREFIX = Component.text()
            .append(Component.text("CK", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
            .build();

    // volatile: replaced by /chunkkeeper reload while other threads may send messages (Folia)
    private static volatile Translations translations = new Translations(Map.of());
    private static volatile String fixedLanguage;
    private static volatile String consoleLanguage = Translations.FALLBACK;

    private Messages() {
    }

    /**
     * @param language        "auto" or a language code that everyone gets
     * @param consoleLanguage the language for the server console when {@code language} is "auto"
     */
    public static void configure(Translations loaded, String language, String consoleLanguage) {
        Messages.translations = loaded;
        Messages.fixedLanguage = language.equalsIgnoreCase("auto") ? null : language.toLowerCase(Locale.ROOT);
        Messages.consoleLanguage = consoleLanguage.toLowerCase(Locale.ROOT);
    }

    /** The language a sender gets messages in. */
    public static String language(CommandSender sender) {
        if (fixedLanguage != null) {
            return fixedLanguage;
        }
        if (sender instanceof Player player) {
            String own = player.locale().getLanguage();
            return translations.supports(own) ? own : Translations.FALLBACK;
        }
        return consoleLanguage;
    }

    public static String text(CommandSender sender, Text text) {
        return translations.format(language(sender), text);
    }

    public static String text(CommandSender sender, String key, Object... placeholders) {
        return text(sender, Text.of(key, placeholders));
    }

    /** For texts that are saved in a file, such as the origin of a named area: always the console language. */
    public static String stored(Text text) {
        return translations.format(fixedLanguage != null ? fixedLanguage : consoleLanguage, text);
    }

    public static void success(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(PREFIX.append(Component.text(text(sender, key, placeholders), NamedTextColor.GREEN)));
    }

    public static void error(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(PREFIX.append(Component.text(text(sender, key, placeholders), NamedTextColor.RED)));
    }

    public static void info(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(PREFIX.append(Component.text(text(sender, key, placeholders), NamedTextColor.GRAY)));
    }

    /** A "label: value" line without prefix, used in lists such as /chunkkeeper chunkinfo. */
    public static void field(CommandSender sender, String labelKey, Object value) {
        sendField(sender, text(sender, labelKey), value);
    }

    /** Like {@link #field}, but with a label that is not translated, such as a world or area name. */
    public static void namedField(CommandSender sender, String label, Object value) {
        sendField(sender, label, value);
    }

    private static void sendField(CommandSender sender, String label, Object value) {
        String shown = value instanceof Text text ? text(sender, text) : String.valueOf(value);
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(shown, NamedTextColor.WHITE)));
    }

    public static void yesNo(CommandSender sender, String labelKey, boolean value) {
        Component answer = value
                ? Component.text(text(sender, "general.yes-word"), NamedTextColor.GREEN)
                : Component.text(text(sender, "general.no-word"), NamedTextColor.RED);
        sender.sendMessage(Component.text(text(sender, labelKey) + ": ", NamedTextColor.GRAY).append(answer));
    }
}
