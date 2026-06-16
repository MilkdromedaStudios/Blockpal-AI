# AI Assistant — project notes for Claude

A Minecraft **Fabric** mod that adds a friendly AI companion entity (default
name **Ethan**). Tasks are planned by an LLM over an OpenAI-compatible API.

## Build artifacts → `builds/` (keep history)

Whenever a jar is built and verified during testing, copy it into the repo's
**`builds/`** folder so it's available without compiling from source.

- **Keep a full history.** Never delete or overwrite older jars on a version
  bump — every released `mod_version` keeps its own
  `builds/ai-assistant-<version>.jar`. Bump `mod_version` in
  `gradle.properties` when shipping a new build so the new jar lands alongside
  the old ones instead of replacing them.
- `builds/` is intentionally **not** gitignored (only `build/` is).

## Building

- Standard Fabric + Gradle (Loom) project; use the wrapper (`./gradlew build`).
- Requires **JDK 25**. Loom auto-provisions it via the Foojay resolver in
  `settings.gradle`; locally `~/.gradle/gradle.properties` can point
  `org.gradle.java.installations.paths` at a JDK 25.
- Key versions live in `gradle.properties` (Minecraft, Fabric Loader/API, Loom)
  and `gradle/wrapper/gradle-wrapper.properties` (Gradle itself).
- Verify with a real `./gradlew clean build` before committing a jar.

## Layout

```
src/main/java        # common mod: entity, AI planner, commands, chat, networking
src/client/java      # client-only: rendering and the settings GUI
src/main/resources   # fabric.mod.json, lang files, skins, assets
builds/              # tested, ready-to-use jars (full version history, no deleting old builds.)
```
