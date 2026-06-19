# Installation

Nexus AI is a **Fabric** mod for Minecraft **26.1.2**. You need the
[Fabric Loader](https://fabricmc.net/use/installer/) and
[Fabric API](https://modrinth.com/mod/fabric-api) installed too.

## Option A — Pre-built jar (recommended)

| Step | Action |
|------|--------|
| 1 | **[Browse `builds/`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/tree/main/builds)** and pick the latest `ai-assistant-<version>.jar` |
| 2 | Hit **Download raw file** (the download icon) |
| 3 | Copy the jar into your `mods/` folder |
| 4 | Make sure **[Fabric Loader 0.19+](https://fabricmc.net/use/installer/)** and **[Fabric API](https://modrinth.com/mod/fabric-api)** are also in `mods/` |
| 5 | Launch Minecraft — run `/ai summon` to meet Ethan |

```
mods/
├── ai-assistant-2.14.0.jar   ← this mod
├── fabric-api-0.151.0+26.1.2.jar
└── ...
```

> **Server install:** drop the same jar into the server's `mods/` folder alongside
> Fabric API. Players also need it client-side to open the settings screen.

## Option B — Build from source

See **[Building From Source](Building-From-Source)** for the full walkthrough.

```bash
git clone https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI.git
cd Nexus-Minecraft-AI
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

Output lands in `build/libs/ai-assistant-<version>.jar`.

## Version compatibility

| Mod version | Minecraft | Fabric Loader | Fabric API |
|-------------|-----------|---------------|------------|
| **2.14.0** *(latest)* | 26.1.2 | 0.19.3+ | 0.151.0+ |
| 2.13.x | 26.1.2 | 0.19.3+ | 0.151.0+ |
| 2.12.x | 26.1.2 | 0.19.3+ | 0.151.0+ |

> Pre-built jars in [`builds/`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/tree/main/builds)
> are kept for every released version — older builds are never deleted.

**Next:** [Getting Started »](Getting-Started)
</content>
