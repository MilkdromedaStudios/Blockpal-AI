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

## Workflows (CI/CD)

The three GitHub Actions workflows are deliberately consistent: real work happens on
**merge to `main`**, never on a freshly opened PR (so a PR you later close has no
side effects).

| Workflow | When it runs | What it does |
|----------|--------------|--------------|
| `build.yml` | pushes to `main` and `claude/**` branches (so a PR's head commit still gets a compile check) | `./gradlew build` + uploads the jar artifact |
| `wiki.yml` | push to `main` that touches `wiki/**` (i.e. after a merge), plus an hourly backup sync | publishes `wiki/` to the GitHub Wiki |
| `release.yml` | a **merged** PR, a `v*` tag, or manual dispatch | publishes the jar to Modrinth |

## Releasing to Modrinth

The **Release to Modrinth** workflow (`.github/workflows/release.yml`) runs when a
**pull request is merged** (not when it's opened, and not if it's closed without
merging), on a `v*` tag push, and on a manual dispatch. It builds the mod, renames
the jar to `Blockpal-<mod_version>-<minecraft_version>.jar`
(e.g. `Blockpal-3.4.0-26.2.jar`) and uploads it to Modrinth via
`Kir-Antipov/mc-publish`.

Each release is published:

- for the **Fabric and Quilt** loaders (Quilt runs Fabric mods, so it's just tagged compatible),
- as a **`beta`** version type,
- with the matching `## <version>` section of [`CHANGELOG.md`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/blob/main/CHANGELOG.md) as the version description, and
- with the project kept in the **`technology`** category.

It is **idempotent** — a given version uploads at most once. Modrinth itself does
*not* enforce unique version numbers, so the workflow keeps its own marker: after
a successful publish it pushes a `modrinth-published/<version>` git tag, and the
gate skips the publish whenever that tag already exists (it also does a
best-effort Modrinth API check to catch versions uploaded by hand). Bump
`mod_version` in `gradle.properties` to ship a new one. (Publishing is also
skipped automatically on fork PRs, where the secrets aren't available.)

One-time setup (repo **Settings ▸ Secrets and variables ▸ Actions**):

| Kind | Name | Value |
|------|------|-------|
| Secret | `MODRINTH_TOKEN` | a Modrinth PAT with the *Create versions* scope (add *Read/Write projects* too if you want the workflow to set the `technology` category) |
| Variable | `MODRINTH_PROJECT_ID` | the Modrinth project's ID or slug — must match the real project |

> The `MODRINTH_PROJECT_ID` must be the **actual** project slug/ID on Modrinth. If
> it's wrong, the existence check can't find anything and the marker tag becomes
> the only thing stopping duplicate uploads.

```bash
git tag v3.1.0
git push origin v3.1.0   # triggers the release
```
</content>
