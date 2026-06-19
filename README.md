<div align="center">

# ⬢ NEXUS AI ⬢

**A Minecraft AI companion that builds, fights, and thinks**

[![Mod Version](https://img.shields.io/badge/mod-v2.14.0-6c63ff?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyTDIgN2wxMCA1IDEwLTV6TTIgMTdsOSA1IDktNXYtN0wxMiAxMiAyIDd6Ii8+PC9zdmc+)](builds/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62b96e?style=for-the-badge)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Fabric_Loader-0.19.3+-dbb74b?style=for-the-badge)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25+-e76f51?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-264653?style=for-the-badge)](LICENSE)

*Drop a friendly AI character named **Ethan** into your world. It reads your chat,
plans tasks through an LLM, fights back on reflex, and configures itself from a
real in-game settings screen — no mods folder archaeology required.*

</div>

---

## ⬇ Download & Install

> **Latest stable build: `v2.14.0`** — Nexus AI rebrand, tabbed settings GUI, custom skin support, versioned config, FPS kill-switch

### Option A — Pre-built jar (recommended)

| Step | Action |
|------|--------|
| 1 | **[Browse `builds/`](https://github.com/MilkdromedaStudios/minecraft-ai-test/tree/main/builds)** and click the latest `ai-assistant-<version>.jar` |
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

> **Server install:** drop the same jar into the server's `mods/` folder alongside Fabric API. Players also need it client-side to open the settings screen.

### Option B — Build from source

```bash
git clone https://github.com/MilkdromedaStudios/minecraft-ai-test.git
cd minecraft-ai-test
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

Output lands in `build/libs/ai-assistant-<version>.jar`. JDK 25 is auto-downloaded by Gradle if you don't have it. Full build docs → [Building from source](#-building-from-source).

### Version compatibility

| Mod version | Minecraft | Fabric Loader | Fabric API |
|-------------|-----------|---------------|------------|
| **2.14.0** *(latest)* | 26.1.2 | 0.19.3+ | 0.151.0+ |
| 2.13.x | 26.1.2 | 0.19.3+ | 0.151.0+ |
| 2.12.x | 26.1.2 | 0.19.3+ | 0.151.0+ |

> Pre-built jars in [`builds/`](builds/) are kept for every released version — older builds are never deleted.

---

## ✦ Features

- **Named & tagged** — spawns as **Ethan** with a visible nametag above its head. Rename any time with `/ai name <name>` and the tag updates live.
- **Proactive — reads the room** — with *active analysis* on (default), it runs **every** chat message past the language model to decide whether you need it and what you want, so "*can you clear these trees?*" or "*I need a shelter*" just work — no name, no exact command words. Toggle with `/ai active on|off`.
- **Real settings GUI** — tabbed in-game screen (Identity · Behavior · AI · Combat · Developer) for all tokens, model params, toggles and sliders. Open with `/ai menu` or sneak-right-click.
- **Talk to it in chat** — no slash command needed. It actively listens and reacts to natural language (`/ai listen on|off`).
- **Instant quick-commands** — "come here", "follow me", "stay", "stop", "where are you" work immediately, with no API token.
- **AI-planned tasks** — anything else ("build a 5×5 floor", "mine this tree") is sent to a language model that returns a structured step-by-step plan.
- **Never just stands there** — combat and retreat run on an instant **reflex layer in every mode, with no API call**. Planning is fully async, so the assistant keeps fighting while it thinks.
- **Runs commands** — with command execution on, plans can use `/setblock`, `/fill`, `/give`, `/summon`, `/clone`, `/effect`… safely gated → [Letting it run commands](#-letting-it-run-commands).
- **Solves puzzles & escape rooms** — sees nearby levers, buttons, doors, pressure plates and redstone in its context and can flip/press/open them (`USE_BLOCK`).
- **Looping activities** — patrol, guard, keep mining or explore loop continuously, re-planning each round with fresh context.
- **Custom skins** — built-in palette skins (`robot`, `void`, `slate`, `ember`, `forest`, `amethyst`), or drop your own 64×64 PNG into `config/ai-assistant/skins/` and apply with `/ai skin <name>`. No rebuild needed.
- **Emergency FPS kill-switch** — auto-disables the whole mod when framerate collapses; re-enable with `/ai resume`.
- **Works in every gamemode** — survival, adventure and creative.

---

## 🔑 One-time setup: add an AI token

Quick commands work out of the box, but **AI-planned tasks need an API token.** The mod uses HuggingFace's router (OpenAI-compatible) by default.

1. Create a free token at <https://huggingface.co/settings/tokens>.
2. In-game, run:
   ```
   /ai token <your_token>
   ```

That's it. To use a different model or provider, see [Settings](#-settings).

---

## 🗣 Talking to your assistant

| You type in chat | What happens |
|------------------|--------------|
| `follow me` | Follows you *(instant, no token)* |
| `come here` | Comes to you, teleports if far *(instant)* |
| `stay` / `wait` | Holds position *(instant)* |
| `stop` | Cancels current task *(instant)* |
| `where are you?` | Reports location *(instant)* |
| `Ethan, build a wall` | Sends "build a wall" to the AI planner |
| `can you clear out these trees?` | Active analysis → mines the trees |
| `ugh, I really need a shelter` | Active analysis → builds a shelter |

Addressing it by name (`Ethan, ...`) always works; the prefix is stripped before the rest is run.

---

## ⌨ Commands

| Command | Description |
|---------|-------------|
| `/ai help` | Show the in-game command list |
| `/ai menu` · `/ai config` | Open the settings screen |
| `/ai summon [name]` | Spawn an assistant (default: **Ethan**) |
| `/ai dismiss` | Remove your assistant |
| `/ai come` | Call it to you |
| `/ai follow` | Have it follow you |
| `/ai stay` | Hold position and keep watch |
| `/ai stop` | Cancel the current task |
| `/ai resume` · `/ai enable` | Re-enable after FPS kill-switch tripped |
| `/ai locate` · `/ai where` | Distance, direction and coords |
| `/ai name <name>` | Rename it |
| `/ai skin <name>` | Change skin (built-in or custom PNG) |
| `/ai token <token>` | Set API token |
| `/ai listen on\|off` | Toggle chat listening |
| `/ai active on\|off` | Toggle proactive AI analysis |
| `/ai commands on\|off` | Allow/block command execution |
| `/ai inventory` · `/ai inv` | Show carried items |
| `/ai settings [key] [value]` | View or change any setting |
| `/aiskins list\|reload` | *(client)* List or hot-reload skins folder |
| `/ai <task>` | Give a natural-language task |

---

## ⚙ Settings

### Settings screen — `/ai menu`

A five-tab screen reachable via `/ai menu` or sneak-right-click on the assistant:

| Tab | What's here |
|-----|-------------|
| **Identity** | Name, skin, open skins folder |
| **Behavior** | Chat listening, active analysis, sneak-to-open-menu, follow distance, guard radius |
| **AI** | API URL, model, token, temperature, max tokens |
| **Combat** | Allow commands, permission level, flee health |
| **Developer** | Action tick delay, task watchdog timeout *(high-risk settings)* |

Hit **Save** to apply, **Cancel** (or **Esc**) to discard. Token field stays blank when one is set — leave blank to keep current, or type a new one.

**Performance preset** (top of screen) — cycle through **Normal**, **Opus** (high-end), or **Potato** (low-end) to auto-fill all related sliders at once.

### Command-line settings

Every option is also addressable via `/ai settings <key> <value>`:

```
/ai settings                            # show all current values
/ai settings model mistralai/Mistral-7B-Instruct-v0.2
/ai settings api_url http://localhost:11434/v1/chat/completions
/ai settings temperature 0.7
/ai settings max_tokens 512
/ai settings follow_distance 4
/ai settings guard_radius 16
/ai settings sneak_menu false           # disable sneak-right-click
/ai settings preset potato              # apply a performance preset
```

Full list of keys: `name` · `skin` · `model` · `api_url` · `token` · `temperature` · `max_tokens` · `follow_distance` · `guard_radius` · `command_level` · `max_task_seconds` · `action_tick_delay` · `flee_health` · `chat_listening` · `active_mode` · `allow_commands` · `debug_logging` · `sneak_menu` · `preset`

Settings persist in `config/ai-assistant/config.json`. The file is versioned — new fields are added with defaults on upgrade without wiping existing values (your API key survives).

---

## 🧠 What the AI can do

| Action | What it does |
|--------|--------------|
| `MOVE_TO` | Walk/path to a position |
| `PLACE_BLOCK` / `BREAK_BLOCK` | Place or break a single block |
| `MINE_AREA` | Clear every block in a box (digging, tunnels) |
| `USE_BLOCK` | Flip a lever, press a button, open a door/trapdoor |
| `RUN_COMMAND` | Execute a Minecraft command |
| `ATTACK_NEAREST` | Strike the nearest hostile |
| `FOLLOW_PLAYER` | Path to and follow a player |
| `LOOK_AT` | Face a position |
| `JUMP` / `SET_SNEAK` | Hop (parkour) / crouch |
| `CHAT` | Say something |
| `WAIT` / `COLLECT_ITEM` / `STOP` | Pause / pick up / end the plan |

Setting `"loop": true` on a plan makes it an ongoing activity — the assistant re-plans continuously with fresh context each round.

---

## ⚡ Letting it run commands

`RUN_COMMAND` is what turns the assistant from "places blocks one by one" into "builds a working redstone contraption". It's gated:

- **Toggle:** on by default. Turn off with `/ai commands off` or the **Allow commands** switch in the menu.
- **Permission level (default 2):** the command-block tier — allows `/setblock`, `/fill`, `/clone`, `/give`, `/summon`, `/tp`, `/effect`, `/time`, `/weather`, but **not** server-admin commands. Adjust with the **Command perm level** slider.
- **Denylist:** `stop`, `op`, `ban`, `whitelist`, `reload` and similar are always refused regardless of level.

---

## 🎨 Custom skins

```bash
/ai skin robot           # built-in (also: void, slate, ember, forest, amethyst)
/ai skin default         # vanilla Steve
/ai skin minecraft:textures/entity/player/wide/steve.png   # any texture id
/ai skin my_skin         # PNG in config/ai-assistant/skins/my_skin.png
```

**Drop-in skins:** place a standard 64×64 player skin PNG in `config/ai-assistant/skins/` and apply with `/ai skin <filename-without-extension>`. Use the **Open skins folder** button in `/ai menu → Identity` to jump straight there. `/aiskins reload` hot-reloads the folder — no restart needed.

Resolution order: `default`/`steve` → `namespace:path` → skins folder PNG → baked-in skin.

---

## 🛠 Troubleshooting

**"Can't connect to the AI service"**
Reset the URL to the supported HuggingFace router endpoint:
```
/ai settings api_url https://router.huggingface.co/v1/chat/completions
```

**"My API token is missing or invalid"**
```
/ai token <your_huggingface_token>
```

**"That model wasn't found"**
```
/ai settings model <model-id>
```

**It doesn't react to chat**
- Check listening is on: `/ai listen on`
- Free-form messages (not starting with a name/keyword) also need active analysis and a token: `/ai active on`, `/ai token <token>`

**Using Ollama / LM Studio / other local model?**
```
/ai settings api_url http://localhost:11434/v1/chat/completions
/ai settings model <local-model-name>
```

**FPS tanked and the assistant went silent**
The emergency kill-switch tripped. Once framerate recovers, run `/ai resume`.

---

## 🏗 Building from source

Standard **Fabric + Gradle (Loom)** project — no separate Gradle install needed, the wrapper handles it.

**Prerequisites:** JDK 25 (auto-downloaded if missing), Git, internet access for the first build.

```bash
git clone https://github.com/MilkdromedaStudios/minecraft-ai-test.git
cd minecraft-ai-test
./gradlew build
```

Output: `build/libs/ai-assistant-<version>.jar`

```bash
./gradlew runClient   # dev client with mod loaded
./gradlew runServer   # dev server
./gradlew clean       # wipe build/ for fresh rebuild
```

### Project layout

```
src/main/java     # entity, AI planner, commands, chat, networking
src/client/java   # rendering, settings GUI
src/main/resources # fabric.mod.json, lang, skins, assets
builds/           # tested ready-to-use jars (full version history)
```

Key versions: [`gradle.properties`](gradle.properties) (Minecraft, Fabric Loader, Fabric API, Loom) · [`gradle/wrapper`](gradle/wrapper) (Gradle itself)

---

## ⚠ Precautions

This mod is actively developed with Claude Code. Working builds are tagged when tested. You can test any build yourself and open a pull request to tag it as "working".

---

## 📄 License

MIT — see [LICENSE](LICENSE).
