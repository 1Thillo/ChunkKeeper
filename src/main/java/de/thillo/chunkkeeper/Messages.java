package de.thillo.chunkkeeper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Chat messages in ChunkKeeper's colours: green for success, red for errors, grey for information. */
public final class Messages {

    private static final Component PREFIX = Component.text()
            .append(Component.text("CK", NamedTextColor.DARK_GREEN, TextDecoration.BOLD))
            .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
            .build();

    private Messages() {
    }

    public static Component success(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GREEN));
    }

    public static Component error(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.RED));
    }

    public static Component info(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GRAY));
    }

    /** A "label: value" line without prefix, used in lists such as /chunkkeeper chunkinfo. */
    public static Component field(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    public static Component yesNo(String label, boolean value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(value ? Component.text("yes", NamedTextColor.GREEN) : Component.text("no", NamedTextColor.RED));
    }
}
