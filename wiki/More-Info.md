# More Info

A deeper look at how Blockpal works under the hood, for the curious and for contributors.

## How a task becomes actions

1. You speak in chat or run `/ai <task>`.
2. **Quick intents** (come/follow/stay/stop/where) short-circuit here with no API call.
3. Otherwise the request (plus context about nearby blocks, entities, the assistant's
   inventory and state) is sent to an **OpenAI-compatible** LLM on a **background
   thread**.
4. The model returns a structured JSON plan of **5–15 steps**, each one of the
   [16 action types](AI-Actions).
5. The executor runs steps with a small delay between them (`actionTickDelay`), guarded
   by the [task watchdog](Developer-Menu) (`maxTaskSeconds`).
6. If the plan has `"loop": true`, the assistant re-plans continuously with fresh context.

Because planning is async, the **reflex layer** (combat/retreat) keeps running every
tick with no API call — the assistant never freezes while it's "thinking".

## Entity modes

The assistant is always in one of six modes:

**IDLE · FOLLOWING · BUILDING · FIGHTING · GUARDING · EXECUTING**

A top-priority `SurvivalReflexGoal` overrides all of them: it always scans for threats,
retaliates, and retreats below the flee-health threshold regardless of the current mode.
To keep things cheap, pathfinding is recomputed at most about every 0.5 s rather than
every tick, and idle threat scans are rate-limited.

## Inventory & equipment

- A **10-slot backpack** plus four armor slots and a main hand.
- Auto-collects nearby dropped items while idle/following.
- Auto-equips the best weapon and armor it finds (scored by attribute modifiers),
  re-evaluating every couple of seconds.
- Eats food when health drops below ~60%, drinks beneficial potions first, and refuses
  / tosses harmful items (spider eyes, poison potions, etc.).

## Safety systems

| System | What it protects against | Where |
|--------|--------------------------|-------|
| Task watchdog | Runaway / stuck plans | [Developer Menu](Developer-Menu) |
| Emergency FPS kill-switch | Framerate collapse / lag death-spiral | [Performance Presets](Performance-Presets) |
| Command denylist + permission level | Dangerous admin commands | [Running Commands](Running-Commands) |
| Owner-only obedience | Other players hijacking your assistant | [Talking to Your Assistant](Talking-to-Your-Assistant) |
| `MINE_AREA` one-block-per-tick queue | Block-update cascades freezing the server | (built in) |

## Project layout

```
src/main/java       # common mod: entity, AI planner, commands, chat, networking
src/client/java     # client-only: rendering and the settings GUI
src/main/resources  # fabric.mod.json, lang files, skins, assets
builds/             # tested, ready-to-use jars (full version history)
wiki/               # source for this wiki
```

## Naming note

The mod is now **Blockpal**, and as of **3.0.0** the internal identifiers match: mod id
`blockpal`, Java package `com.milkdromeda.blockpal`, texture namespace `blockpal:` and
config folder `config/blockpal/`. It was previously released as **Nexus AI** (and before
that "AI Assistant") under the `ai-assistant` id. Because 3.0.0 renamed all of those,
configs/skins from older installs are not read — set it up fresh.

## Changelog

The full version history is maintained in
[`CLAUDE.md`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/blob/main/CLAUDE.md)
in the repo. Highlights:

- **3.0.0** — Renamed the whole mod to **Blockpal** (mod id, Java package, texture
  namespace, config folder and jar name). Breaking change for existing installs.
- **2.14.0** — Rebranded the display name to **Nexus AI**.
- **2.13.0** — Tabbed settings menu; toggleable sneak-to-open; one generic settings
  command; versioned config.
- **2.12.x** — Drop-in custom skins, `/aiskins` command, "Open skins folder" button,
  four new built-in skins.
- **2.11.0** — Scrollable settings menu; emergency FPS kill-switch.
- **2.10.0** — Performance presets (Normal / Opus / Potato).
- **2.9.0** — Developer Mode GUI + this menu's settings.
- **2.8.x** — Always-busy autonomous behavior, silent task execution, crash/perf fixes.
- **2.4.0–2.7.0** — Real inventory system, item consumption, expressive chat,
  owner-only obedience, autonomous mode.
- **2.1.0** — Initial public release.

## Precautions

This mod is actively developed with Claude Code. Working builds are tagged when tested.
You can test any build yourself and open a pull request to tag it as "working".

## Contributing

Wiki pages are versioned in the [`wiki/`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/tree/main/wiki)
folder and published to the GitHub Wiki automatically — edit the markdown there and open
a PR rather than editing the wiki in place. See
[`wiki/README.md`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/blob/main/wiki/README.md).
</content>
