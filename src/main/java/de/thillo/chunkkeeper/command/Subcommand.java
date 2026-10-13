package de.thillo.chunkkeeper.command;

import java.util.List;

import org.bukkit.command.CommandSender;

/** One part of /chunkkeeper, for example {@code keepchunk}. Its permission is {@code chunkkeeper.<name>}. */
interface Subcommand {

    String name();

    /** The arguments after the name, shown in /chunkkeeper help, e.g. {@code (current | coords <x> <z> <world>)}. */
    String usage();

    /** @param args the arguments after the subcommand name */
    void execute(CommandSender sender, String[] args);

    /** Tab completion for the argument that is being typed, which is the last one in {@code args}. */
    default List<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }
}
