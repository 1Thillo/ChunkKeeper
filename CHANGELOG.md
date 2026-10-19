# Changelog

All notable changes to ChunkKeeper are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `/chunkkeeper show [<radius> | off]` draws the outline of the kept chunks around you with particles
  for 30 seconds: yellow for single chunks, magenta for named areas, with a post at every corner. Only you see them. The largest
  radius is `show-max-radius` (16) in config.yml; permission `chunkkeeper.show`.

## [1.3.0] - 2026-10-18

### Added
- `radius <r> [<x> <z> <world>]` selects the chunks around you or around a chunk, wherever an area is
  expected: `keepregion radius 2 --name farm` keeps 5 x 5 chunks. Radius 0 is one chunk, 1 is 3 x 3.
- `/chunkkeeper import forceload [world]` takes over chunks that were force-loaded with the vanilla
  `/forceload` command, so ChunkKeeper lists them and releases them like its own. `import keepchunks`
  is the old `import`, which still works without a word after it.

### Changed
- Message files in `plugins/ChunkKeeper/messages/` that nobody edited get the texts of each new
  version. Edited files stay as they are; the new texts are put next to them as `en.yml.new`.

### Fixed
- Messages use the singular for one thing: "1 chunk", "1 named area" instead of "1 chunks",
  "1 named areas". Message files can do this with `{count|chunk|chunks}`.

## [1.2.0] - 2026-10-17

### Added
- German messages. With `language: auto` every player gets their Minecraft language (English and
  German are built in), the console uses `console-language`. All texts are in
  `plugins/ChunkKeeper/messages/` and can be changed or translated into more languages.
- ChunkKeeper remembers who added each single chunk and named area, and when; `chunkinfo` and
  `area info` show it.
- `plugins/ChunkKeeper/history.log` records every change, releases included, and
  `/chunkkeeper history` shows it in the game (permission `chunkkeeper.history`).
- Automatic backups of `chunks.yml` before `releaseall`, `import` and restoring, plus
  `/chunkkeeper backup list | create | restore <name> [confirm]` (permission `chunkkeeper.backup`).
  The newest `backups-to-keep` (20) are kept.
- New options `language`, `console-language` and `backups-to-keep`; existing config files get them
  added automatically.
- Issue forms for bug reports and feature requests.
- This changelog.

### Changed
- `/chunkkeeper reload` also reloads the messages.
- Release jars are only attached after the smoke test on Paper and Folia passed.
- The dependency graph sent to GitHub only lists what the plugin jar ships and the Gradle build
  itself, so Dependabot alerts concern ChunkKeeper directly.

### Fixed
- On the first start the console said "Keeping 0 chunks loaded" before importing the KeepChunks
  chunks, and did not mention imported chunks of worlds that are not loaded yet.

## [1.1.0] - 2026-10-12

### Added
- Named areas: groups of chunks that are kept and released as a whole, next to single chunks.
  - `keepregion ... --name <name>` and `keeprail ... --name <name>` create one.
  - `/chunkkeeper area list | info <name> | release <name> | rename <name> <new name>`.
  - `area convert <name> (coords ... | worldedit | worldguard <region> <world> | world <world>)`
    turns existing single chunks into a named area, for example the chunks imported from KeepChunks.
  - `area dissolve <name>` turns an area back into single chunks.
  - New permission `chunkkeeper.area`.
- A smoke test that runs every pull request on real Paper and Folia servers.

### Changed
- A chunk stays loaded as long as anything keeps it: itself or at least one named area.
  `chunkinfo` shows what keeps a chunk, `list` shows the named areas, `reload` and `releaseall`
  include them. Converting and dissolving never unload a chunk.

## [1.0.1] - 2026-10-11

### Fixed
- `/chunkkeeper` no longer disappears when the plugin is reloaded with PlugMan (reload, or unload
  and load). Kept chunks stay loaded during a reload.

### Changed
- `/kc` and `/ck` are aliases again, next to `/keepchunks` and `/chk`. EssentialsX leaves them to
  ChunkKeeper now.

### Added
- `./gradlew runServer` starts a development server with hotswap support.

## [1.0.0] - 2026-10-11

First release, a replacement for the discontinued KeepChunks plugin with Folia support.

### Added
- Keep single chunks, rectangular areas, WorldEdit/FastAsyncWorldEdit selections, WorldGuard
  regions (exact shape, also polygons) and whole minecart railways loaded.
- `chunkinfo`, `list`, `reload`, `releaseall confirm`, `import`, `version` and `help`.
- One permission per subcommand (`chunkkeeper.<subcommand>`); the old `keepchunks.<subcommand>`
  permissions keep working.
- Automatic import of KeepChunks' chunks and its `releaseallprotection` setting on the first start.
- Safety limits `max-area-chunks` and `rail-search-limit`.
- Paper and Folia 26.2 support.

[Unreleased]: https://github.com/1Thillo/ChunkKeeper/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/1Thillo/ChunkKeeper/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/1Thillo/ChunkKeeper/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/1Thillo/ChunkKeeper/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/1Thillo/ChunkKeeper/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/1Thillo/ChunkKeeper/releases/tag/v1.0.0
