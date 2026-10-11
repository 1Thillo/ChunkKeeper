# ChunkKeeper

Keep selected chunks loaded on Paper and Folia servers, even when no players are around. Useful for
farms, redstone machines, minecart railways and event areas.

ChunkKeeper is a from-scratch replacement for the discontinued KeepChunks plugin. It keeps the
familiar command names and takes over KeepChunks' chunks automatically on its first start.

- Paper and Folia 26.2, Java 25
- Single chunks, rectangular areas, WorldEdit selections, WorldGuard regions and whole railways
- Optional: WorldEdit or FastAsyncWorldEdit, WorldGuard

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
EssentialsX also uses `/kc` and `/ck` (for `/createkit`), but leaves them to ChunkKeeper;
`/essentials:kc` still reaches Essentials.

Coordinates are **chunk coordinates** (block coordinate divided by 16), except for the rail commands,
which take the block position of a rail. `/chunkkeeper chunkinfo current` shows the chunk you stand in.

| Command | What it does |
|---|---|
| `keepchunk current` | Keep the chunk you stand in loaded |
| `keepchunk coords <x> <z> <world>` | Keep one chunk loaded |
| `releasechunk current \| coords <x> <z> <world>` | Stop keeping one chunk loaded |
| `keepregion coords <x1> <z1> <x2> <z2> <world>` | Keep all chunks between two corners loaded |
| `keepregion worldedit` | Keep every chunk touched by your WorldEdit selection loaded |
| `keepregion worldguard <region> <world>` | Keep every chunk touched by a WorldGuard region loaded |
| `releaseregion ...` | The same areas, released |
| `keeprail current \| coords <x> <y> <z> <world>` | Follow a railway from a rail block and keep every chunk along it loaded, plus the chunks right next to it |
| `releaserail ...` | The same railway, released |
| `releaseall confirm` | Release every chunk (console only by default) |
| `chunkinfo current \| coords ...` | Whether a chunk is kept, force-loaded and loaded right now; with an area: how many of its chunks are kept |
| `list [<world> [page]]` | Kept chunks per world, or the chunks of one world |
| `import` | Add the chunks from KeepChunks' `data.yml` again |
| `reload` | Reload `config.yml` and `chunks.yml` |
| `version` | Plugin and server version |
| `help` | The commands you may use |

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

The kept chunks are stored in `plugins/ChunkKeeper/chunks.yml`, one `x,z,world` line per chunk.
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

The plugin jar is `build/libs/ChunkKeeper-<version>.jar`. Every pushed branch is also built by
GitHub Actions; the jar is attached to the workflow run as `plugin-jar`.

## License

MIT, see [LICENSE](LICENSE).
