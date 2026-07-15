# Blockpal for Bedrock — the single-player Add-On

Blockpal also exists as a native **Minecraft Bedrock Edition Add-On** — a
behavior pack + resource pack that puts an AI companion (default name
**Ethan**) into **your own single-player Bedrock world**. Phone, tablet,
console*, or the Windows Bedrock app: no server, no Geyser, no Java Edition,
nothing to host.

> **Two different Bedrock stories — pick yours:**
> - You want to join a **friend's modded Java server** from Bedrock →
>   [Bedrock (Geyser)](Geyser-Bedrock). The full Java feature set, LLM and all.
> - You want the companion **in your own Bedrock world, offline** → this page.
>
> \* Consoles can't import `.mcaddon` files directly; the usual routes are the
> Windows app or sharing a world that already has the packs applied.

---

## Install

1. Get **`builds/blockpal-bedrock-<version>.mcaddon`** from the repo (or build
   it yourself — see below).
2. Open the file — Minecraft launches and imports the behavior pack and
   resource pack together.
3. Edit your world → **Behavior Packs** → activate **Blockpal Companion [BP]**.
   The resource pack is a listed dependency, so it activates automatically.
4. Play. You'll get a one-time chat hint; type **`!ai help`** any time.

**Requirements:** Minecraft Bedrock **1.21.60 or newer**. No experimental
toggles — the add-on sticks to stable Script APIs. As with *any* behavior
pack, Minecraft disables achievements in that world.

---

## Meet your companion

- **`!ai summon`** spawns **Ethan** (or `!ai summon Robo` for a custom name).
  There's also a **Blockpal Companion spawn egg** in the creative inventory.
- He **follows** you by default, **fights back** when hurt, floats in water,
  opens doors while pathing, calls for help when badly hurt, and never
  despawns.
- **Right-click / tap** him to toggle **follow ↔ stay**.
- **Talk to him**: `Ethan, follow me` · `Ethan: where are you` ·
  `Ethan, build a 5x5 floor` — addressing by name works exactly like the
  Java mod. Quick intents (come/follow/stay/stop/guard/where) answer
  instantly.

## Commands

All chat-based, so they work identically on every Bedrock device:

| Chat | Effect |
|------|--------|
| `!ai help` | Show help |
| `!ai summon [name]` | Spawn a companion (up to 4 per player) |
| `!ai dismiss` | Send it home |
| `!ai come` / `follow` / `stay` / `guard` / `stop` | Orders |
| `!ai where` / `locate` | Find it |
| `!ai name <name>` | Rename |
| `!ai skin <default\|robot\|ember\|void>` | Change skin |
| `!ai personality [<id>]` | List / set the personality |
| `!ai bots` | List your companions (mode, spot, health) |
| `!ai inv` | What's in its 10-slot backpack |
| `!ai say <text>` | Make it speak |
| `!ai <task>` | A natural-language task (next section) |

Prefer slash commands? `/scriptevent blockpal:ai <anything above, minus the !ai>`
does the same (requires cheats enabled; the `!ai` chat form doesn't).

## Tasks — the offline planner

The Java mod plans tasks with a cloud LLM. **Bedrock's Script API has no
network access in single-player worlds** (`@minecraft/server-net` exists only
on Bedrock Dedicated Server), so this add-on recreates the planning layer
**on-device**: an instruction parser turns natural phrasing into the same
kind of step-by-step plan, executed **one block per tick** with a **5-minute
watchdog** — the exact execution rules the Java mod converged on after its
early lag lessons.

Things it understands (sizes and materials are flexible):

- `build a 5x5 floor of stone` · `build a platform 9 by 9 of oak planks`
- `build a wall 8 long 3 high of stone bricks`
- `build a tower 12` · `build a bridge 16 of cobblestone`
- `build a house` (a little hut with a doorway, facing you)
- `mine a 3x3 hole` · `dig down 10` (staircase, never straight down)
- `collect items` (walks to drops and stores them in its backpack)
- `kill the monsters` / `guard` (hostile-mob targeting within 16 blocks)
- `wait 10 seconds` · `jump` · `say <anything>`

Floors and bridges pave over terrain; walls, towers and houses only fill air
so the companion **never overwrites your builds**. Mining uses real block
drops. Unknown requests get a personality-voiced "I didn't get that" with
examples — nothing fails silently.

## Personalities

The six Java personalities, recreated: **friendly** (default), **cheerful**,
**grumpy**, **stoic**, **heroic**, **shy**. `!ai personality grumpy` and every
line he says — greetings, task chatter, calls for help, even the where-am-I
report — switches voice. Per-companion, persisted with the world.

## Skins

Four built-ins (`default`, `robot`, `ember`, `void`) switchable live with
`!ai skin <id>`. They use the **classic 64×64 player-skin layout**, so to add
your own: drop any standard skin PNG over one of the files in
`bedrock/packs/resource/textures/entity/companion/` and rebuild the
`.mcaddon` (custom skins can't be hot-loaded from a folder on Bedrock the way
the Java mod does — packs are sealed at import).

## What's NOT here (and why)

| Java feature | Status on Bedrock |
|--------------|-------------------|
| Cloud LLM planning | No HTTP in single-player scripts → replaced by the offline planner above |
| Settings GUI panels | No custom GUI API in stable scripts → everything is `!ai` chat commands |
| Voice (push-to-talk / TTS) | No microphone/audio APIs in the sandbox |
| Possession, hosting, parties, minigames | Out of scope for the single-player v1 |
| Per-player API keys / admin panel | No API to manage; single-player has one player |

A future phase could restore real LLM planning for worlds hosted on a
**Bedrock Dedicated Server** via `@minecraft/server-net`.

## Building from source

```
python3 bedrock/tools/gen_assets.py   # (only if you changed skins/icons)
python3 bedrock/build.py              # → builds/blockpal-bedrock-<version>.mcaddon
```

Pure Python 3 standard library — no Gradle, no Node, no dependencies. The
pack source lives under `bedrock/packs/`; the scripts are plain JavaScript
(`behavior/scripts/`), and version/UUIDs live in the two `manifest.json`
files.

## Troubleshooting

- **"Ethan ignores chat"** — make sure the **behavior** pack is active on the
  world (not just the resource pack), and that you're typing in chat, not the
  command bar. `!ai help` should always answer.
- **Companion slides instead of walking during tasks** — script-driven
  movement is positional (Bedrock scripts can't drive the native pathfinder
  to arbitrary points); following uses real pathfinding, task movement uses
  smoothed steps. Cosmetic, documented, same honesty as the Java possession
  "tug".
- **No blocks appear when building a floor** — it only *replaces* terrain
  with a *different* block; a stone floor on stone ground is already done.
- **Invisible/broken companion** — the resource pack didn't attach; re-import
  the `.mcaddon` or add **Blockpal Companion [RP]** manually under Resource
  Packs.
