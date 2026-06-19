# Building From Source

Standard **Fabric + Gradle (Loom)** project — no separate Gradle install needed, the
wrapper handles it.

## Prerequisites

- **JDK 25** (Loom auto-provisions it via the Foojay resolver in `settings.gradle`;
  locally you can point `org.gradle.java.installations.paths` in
  `~/.gradle/gradle.properties` at a JDK 25).
- **Git**
- Internet access for the first build.

## Build

```bash
git clone https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI.git
cd Nexus-Minecraft-AI
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

Output: `build/libs/blockpal-<version>.jar`

## Dev tasks

```bash
./gradlew runClient   # dev client with the mod loaded
./gradlew runServer   # dev server
./gradlew clean       # wipe build/ for a fresh rebuild
```

Always run a real `./gradlew clean build` before committing a jar.

## Where versions live

| File | Holds |
|------|-------|
| `gradle.properties` | Minecraft, Fabric Loader, Fabric API, Loom, `mod_version` |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle itself |

## Build artifacts → `builds/`

Tested jars are copied into the repo's `builds/` folder so they're available without
compiling. History is kept — every released `mod_version` keeps its own
`builds/blockpal-<version>.jar`; old builds are never deleted. (`builds/` is not
gitignored; only `build/` is.)
</content>
