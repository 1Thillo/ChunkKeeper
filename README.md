# ChunkKeeper

Keep selected chunks loaded on Paper and Folia servers, even when no players are around. Useful for
farms, redstone machines, minecart railways and event areas.

ChunkKeeper is a from-scratch replacement for the discontinued KeepChunks plugin. It keeps the
familiar command names and takes over KeepChunks' chunks automatically on its first start.

- Paper and Folia 26.2, Java 25
- Single chunks, rectangular areas, WorldEdit selections, WorldGuard regions and whole railways
- Named areas, such as an event area or a railway, that are kept and released as a whole
- Messages in English and German, chosen per player; every text can be changed
- Remembers who added what and when, keeps a history of all changes and backs up its data
- Optional: WorldEdit or FastAsyncWorldEdit, WorldGuard

What changed in each version is listed in the [changelog](CHANGELOG.md).

## Installation

1. Put `ChunkKeeper-<version>.jar` into the `plugins` folder.
2. Start the server. ChunkKeeper creates `plugins/ChunkKeeper/config.yml` and `chunks.yml`.

### Switching from KeepChunks

1. Stop the server.
2. Add ChunkKeeper and **remove** the KeepChunks jar. Leave the `plugins/KeepChunks` folder in place.
3. Start the server. On its first start ChunkKeeper reads `plugins/KeepChunks/data.yml` and its
   `releaseallprotection` setting, and keeps all those chunks loaded. The console shows how many
   chunks were imported. KeepChunks' files are only read, never changed.

`/keepchunks` and `/kc` keep working, the subcommands are the same, and the old
`keepchunks.<subcommand>` permissions are still accepted (see `legacy-permissions`). If KeepChunks
was used again after the switch, `/chunkkeeper import` picks up the chunks it added.

## Commands

The main command is `/chunkkeeper`, with the aliases `/keepchunks`, `/kc`, `/ck` and `/chk`.
EssentialsX also uses `/kc` and `/ck` (for `/createkit`), but leaves them to ChunkKeeper.

Coordinates are **chunk coordinates** (block coordinate divided by 16), except for the rail commands,
which take the block position of a rail. `/chunkkeeper chunkinfo current` shows the chunk you stand in.

| Command | What it does |
|---|---|
| `keepchunk current` | Keep the chunk you stand in loaded |
| `keepchunk coords <x> <z> <world>` | Keep one chunk loaded |
| `releasechunk current \| coords <x> <z> <world>` | Stop keeping one chunk loaded |
| `keepregion coords <x1> <z1> <x2> <z2> <world>` | Keep all chunks between two corners loaded |
| `keepregion radius <r> [<x> <z> <world>]` | Keep the chunks around you (or around a chunk) loaded: radius 1 is 3 x 3 chunks, 2 is 5 x 5 |
| `keepregion worldedit` | Keep every chunk touched by your WorldEdit selection loaded |
| `keepregion worldguard <region> <world>` | Keep every chunk touched by a WorldGuard region loaded |
| `keepregion ... --name <name>` | The same, but as a named area (see below) |
| `releaseregion ...` | The same areas, released (single chunks only) |
| `keeprail current \| coords <x> <y> <z> <world>` | Follow a railway from a rail block and keep every chunk along it loaded, plus the chunks right next to it |
| `keeprail ... --name <name>` | The same, but as a named area |
| `releaserail ...` | The same railway, released (single chunks only) |
| `area list` | All named areas |
| `area info <name>` | World, size and origin of a named area |
| `area release <name>` | Release a named area |
| `area rename <name> <new name>` | Rename a named area |
| `area convert <name> (coords ... \| radius ... \| worldedit \| worldguard <region> <world> \| world <world>)` | Turn the single chunks inside a selection into a named area |
| `area dissolve <name>` | Turn a named area back into single chunks |
| `releaseall confirm` | Release every chunk and every named area (console only by default) |
| `chunkinfo current \| coords ...` | Whether a chunk is kept, force-loaded and loaded right now; with an area: how many of its chunks are kept |
| `show [<radius> \| off]` | Draw the outline of the kept chunks around you with particles for 30 seconds (green: single chunks, blue: named areas) |
| `list [<world> [page]]` | Kept chunks per world and the named areas, or the single chunks of one world |
| `import [keepchunks]` | Add the chunks from KeepChunks' `data.yml` again |
| `import forceload [<world>]` | Take over the chunks force-loaded with the vanilla `/forceload` command |
| `history [page]` | Who changed what and when, newest first |
| `backup list` | The backups of `chunks.yml` and what they hold |
| `backup create` | Make a backup now |
| `backup restore <name> [confirm]` | Restore a backup; without `confirm` it only shows what it holds |
| `reload` | Reload `config.yml`, `chunks.yml` and the messages |
| `version` | Plugin and server version |
| `help` | The commands you may use |

### Single chunks and named areas

Areas and railways can be kept in two ways, and both can be used side by side:

- **As single chunks** (`keepregion ...` without a name): every chunk is kept on its own, like
  KeepChunks did. You can later release any part of it with `releasechunk` or `releaseregion`.
- **As a named area** (`keepregion ... --name halloween`): the chunks are kept as one group.
  `list` shows "halloween: 140 chunks" instead of 140 lines, and `area release halloween` releases
  the whole group at once.

A chunk stays loaded as long as anything keeps it: itself, or at least one named area. Releasing
a single chunk that also belongs to an area leaves it loaded, and releasing an area leaves the
chunks loaded that are kept on their own or by another area. `chunkinfo` shows what keeps a chunk.

Existing single chunks can be grouped later with `area convert`, for example the chunks imported
from KeepChunks: `area convert halloween worldguard halloween halloween` moves the single chunks
inside that WorldGuard region into the area `halloween`, and `area convert event world event` takes
all single chunks of the world `event`. Chunks in the selection that are not kept are left out.
`area dissolve <name>` does the opposite. Neither of them unloads a chunk on the way.

Names may use lower-case letters, digits, `-` and `_` (up to 32 characters).

### Who, when and backups

- `chunkinfo` and `area info` show who added a single chunk or created an area, and when.
- Every change is written to `plugins/ChunkKeeper/history.log` with time, name and what happened,
  releases included; `/chunkkeeper history` shows it in the game.
- Before `releaseall`, `import` and restoring a backup, ChunkKeeper copies `chunks.yml` to
  `plugins/ChunkKeeper/backups/`. An accidental `releaseall` can be undone with
  `/chunkkeeper backup restore <name> confirm`; the message after `releaseall` names the backup.
  Restoring makes a backup of the current state first, so it can be undone as well.

## Languages

With `language: auto` every player gets messages in their Minecraft language if ChunkKeeper has
it, otherwise in English. English and German are built in; the console uses `console-language`.
`language: de` (or `en`) gives everyone the same language.

The texts are in `plugins/ChunkKeeper/messages/en.yml` and `de.yml`. Change any of them and run
`/chunkkeeper reload`; words in `{braces}` are filled in by the plugin, and `{count|chunk|chunks}`
becomes "chunk" when count is 1 and "chunks" otherwise. For another language, copy
`en.yml` to e.g. `fr.yml` and translate it; players with a French client then get it.

Files you did not change are updated automatically when a new ChunkKeeper version brings new texts.
Files you changed are left alone; the new texts then appear next to them as `en.yml.new`.

## Permissions

Every subcommand has its own permission, `chunkkeeper.<subcommand>`, for example
`chunkkeeper.keepregion`. All of them default to operators. `chunkkeeper.*` grants everything.

## Configuration

`plugins/ChunkKeeper/config.yml`, apply changes with `/chunkkeeper reload`:

| Option | Default | Meaning |
|---|---|---|
| `release-all-console-only` | `true` | Only the console may run `releaseall` |
| `prevent-world-unload` | `true` | Worlds with kept chunks are not unloaded (e.g. by Multiverse) |
| `legacy-permissions` | `true` | Also accept the old `keepchunks.<subcommand>` permissions |
| `rail-search-limit` | `50000` | `keeprail` stops after this many rail blocks |
| `max-area-chunks` | `10000` | `keepregion`/`releaseregion` refuse larger areas, so a typo cannot force-load millions of chunks |
| `language` | `auto` | `auto` = each player's Minecraft language, or a fixed language such as `de` or `en` |
| `console-language` | `en` | Language of the console when `language` is `auto` |
| `backups-to-keep` | `20` | How many backups of `chunks.yml` are kept |
| `show-max-radius` | `16` | The largest radius for `/chunkkeeper show`; more means more particles |

The kept chunks are stored in `plugins/ChunkKeeper/chunks.yml`: single chunks under `chunks`
(`x,z,world`, with who added them and when) and named areas under `areas`.
Edit it only while the server is stopped, or run `/chunkkeeper reload` afterwards.

## How it works

ChunkKeeper uses Minecraft's own force-loading (the same mechanism as the vanilla `/forceload`
command). Force-loaded chunks keep ticking without players nearby, and Minecraft saves the flag with
the world. Every kept chunk costs memory and tick time, so keep only what you need.

On Folia, the force-load flags are changed on the global region thread and blocks are only read on
the thread of the region that owns them, so ChunkKeeper is safe to use there.

## Building

```bash
./gradlew build
```

The plugin jar is `build/libs/ChunkKeeper-<version>.jar`. The build also runs the unit tests.
Every pushed branch is built by GitHub Actions; the jar is attached to the workflow run as
`plugin-jar`.

## Reloading with PlugMan

ChunkKeeper can be reloaded at runtime with PlugManX (`/plugman reload ChunkKeeper`), also on
Folia. To update without a restart, unload it, replace the jar and load the new one:

```
/plugman unload ChunkKeeper
(replace ChunkKeeper-<old>.jar with ChunkKeeper-<new>.jar)
/plugman load ChunkKeeper-<new>
```

Kept chunks stay loaded the whole time. On Windows the jar of a loaded plugin cannot be deleted,
so unload first.

## Development with hotswap

```bash
./gradlew runServer
```

starts a Paper 26.2 development server in `run/` with the plugin, running on the JetBrains Runtime
(downloaded by Gradle on the first run) with a debugger port on `localhost:5005`. On the first run,
accept the EULA in `run/eula.txt` and start again.

In VS Code, start the debug configuration **Attach to dev server (hotswap)**. Saved changes are
then applied to the running server ("hot code replace"), including new methods and fields. Changes
to `plugin.yml`, new commands or listeners and code in `onEnable` still need a restart of the task.

## Dependency updates

Dependabot checks the Gradle dependencies and GitHub Actions every week. Patch and minor updates
are merged automatically once the CI build is green; major updates stay open as pull requests for
a manual look.

## License

MIT, see [LICENSE](LICENSE).
