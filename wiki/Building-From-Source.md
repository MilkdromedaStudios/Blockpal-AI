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
git clone https://github.com/MilkdromedaStudios/Blockpal-AI.git
cd Blockpal-AI
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

The four GitHub Actions workflows are deliberately consistent: real work happens on
**merge to `main`**, never on a freshly opened PR (so a PR you later close has no
side effects).

| Workflow | When it runs | What it does |
|----------|--------------|--------------|
| `build.yml` | pushes to `main` and `claude/**` branches (so a PR's head commit still gets a compile check) | `./gradlew build` + uploads the jar artifact |
| `wiki.yml` | push to `main` that touches `wiki/**` (i.e. after a merge), plus an hourly backup sync | publishes `wiki/` to the GitHub Wiki |
| `release.yml` | a **merged** PR **that touches `gradle.properties`**, a `v*` tag, or manual dispatch | publishes the jar to CurseForge |

## Releasing to CurseForge

The **Release to CurseForge** workflow (`.github/workflows/release.yml`) runs when a
**pull request that changes `gradle.properties` is merged** (not when it's opened, and
not if it's closed without merging), on a `v*` tag push, and on a manual dispatch. That
`paths:` filter is deliberate: only a version bump ships a release, so ordinary PRs don't
publish. It builds the mod and uploads the jar through
`.github/actions/publish-one-curseforge`, which wraps `Kira-NT/mc-publish`.

Each release is published:

- for the **Fabric** loader,
- as a **`beta`** version type,
- named `Blockpal <version> (MC <mcversion>)`, with the Minecraft version read out of
  the jar's own `fabric.mod.json` (`depends.minecraft`) rather than guessed, and
- with Fabric API (`306612`) recorded as a required dependency.

It is **idempotent** — a given version uploads at most once. The workflow keeps its own
marker: after a successful upload it pushes a `curseforge-published/<version>_mc<mc>` git
tag, and the gate skips whenever that tag already exists. Bump `mod_version` in
`gradle.properties` to ship a new one.

> **A release is only as good as `builds/`.** The one-time backfill workflows published
> from the `builds/` folder, so any version whose jar was never committed there simply
> never reached CurseForge — 14 versions are missing for exactly that reason. If you ship
> a version, commit its jar; if you can't build locally, take it from the `build`
> workflow's `blockpal-jar` artifact.

One-time setup (repo **Settings ▸ Secrets and variables ▸ Actions**):

| Kind | Name | Value |
|------|------|-------|
| Secret | `MODRINTH_TOKEN` | **the CurseForge upload API token.** The name is legacy — it is not a Modrinth token |
| Variable | `MODRINTH_PROJECT_ID` | **the numeric CurseForge project ID.** Also a legacy name |

> Those two names were kept on purpose when the project moved from Modrinth to
> CurseForge: renaming them means creating the new secret and variable in repo settings
> first, and any release in between would fail. The comment at the top of `release.yml`
> records the same mapping.

There is **no description-sync workflow**. CurseForge's public API has no
project-description endpoint, so the project page text is edited by hand on CurseForge.

```bash
git tag v3.1.0
git push origin v3.1.0   # triggers the release
```
</content>
