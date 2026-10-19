# Contributing to ChunkKeeper

Thanks for helping! Bug reports, ideas, translations and code are all welcome.

## Before you start

- Look at the [roadmap](https://github.com/users/1Thillo/projects/1) and the open issues. Issues
  labelled `good first issue` are small and self-contained.
- Comment on the issue you want to work on, so two people don't do the same thing. For something
  that has no issue yet, open one first and describe what you have in mind.
- Security problems are reported privately:
  [new security advisory](https://github.com/1Thillo/ChunkKeeper/security/advisories/new).

## Building and testing

You need Java 25. The Gradle wrapper downloads everything else.

```bash
./gradlew build
```

This compiles the plugin, runs the unit tests and writes `build/libs/ChunkKeeper-<version>.jar`.
For a quick test server with the plugin, `./gradlew runServer` starts Paper (see the README for
attaching a debugger).

Every pull request is also checked by GitHub Actions: the build, and a smoke test that starts real
Paper and Folia servers, runs the commands in `.github/smoke-test/commands.txt` and expects the
lines in `.github/smoke-test/expected.txt` in the log.

## What a pull request should contain

- **One topic per pull request**, split into small commits that each do one thing and build on
  their own. Commit messages and pull request descriptions are in English.
- **Paper and Folia.** ChunkKeeper runs on both. On Folia, force-load flags may only be changed on
  the global region thread and blocks only be read on the thread of their region; `ChunkLoader`
  has helpers for both. Changes to what is kept go through `Keeper`, whose methods are
  `synchronized`.
- **Texts in `en.yml`.** Every message is a key in `src/main/resources/messages/en.yml`, never a
  string in the code. Placeholders are `{name}`, plural forms `{count|chunk|chunks}`. English is
  the only language that has to be complete: the other files in `messages/` (German and any
  other language) may miss new texts, and ChunkKeeper shows the English text until someone
  translates it. If you do add a translation, keep the placeholders of the English text; the
  tests check that, and that no file has keys that `en.yml` does not have.
  Avoid `on`, `off`, `yes` and `no` as keys: YAML reads them as true or false.
- **Translations.** A new language is a new file in `messages/` named after the language code
  (e.g. `fr.yml`), added to `MessageFiles.BUILT_IN`. Please only translate languages you speak
  well; mark a file that is partly machine-translated at the top.
- **Tests.** Logic that does not need a server (like `Keeper`, `ChunkStore`, `Area`) gets unit
  tests. New commands get a line in the smoke test where that is possible from the console.
- **Changelog and docs.** Add a line to the "Unreleased" section of `CHANGELOG.md`, and update the
  README when commands, permissions or options change.
- **Existing data keeps working.** Servers update by replacing the jar, so older `chunks.yml` and
  `config.yml` files must still load. New config options need a default in `config.yml`.

## Style

Match the code around your change: plain Java, short methods, comments that explain *why*. The code
is meant to be readable for people who are still learning Java, so prefer the obvious way over the
clever one.

## License

By contributing you agree that your contribution is published under the [MIT License](LICENSE).
