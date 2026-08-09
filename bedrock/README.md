# Blockpal for Bedrock — single-player AI companion Add-On [Experimental, will break]

A from-scratch recreation of the Blockpal companion as a **Minecraft Bedrock
Edition Add-On** (behavior pack + resource pack, Script API), so Bedrock
players get **Ethan in their own single-player worlds** — no server, no
Geyser proxy, no Java Edition needed. Not published anywhere; it lives in
this repo only.

> **How is this different from [Geyser support](../wiki/Geyser-Bedrock.md)?**
> The Java mod already works for Bedrock players *joining a modded Java
> server*. This add-on is the opposite: it runs entirely **on the Bedrock
> device**, inside any Bedrock world.

## Install

1. Grab **`builds/blockpal-bedrock-<version>.mcaddon`** (or build it: `python3 bedrock/build.py`).
2. Open the file on your device — Minecraft imports both packs automatically.
3. In your world's settings, add **Blockpal Companion [BP]** under *Behavior
   Packs* (the resource pack is pulled in automatically as a dependency).
4. No experimental toggles are required (the add-on uses stable APIs only,
   Minecraft **1.21.60+**). Note: any behavior pack disables achievements —
   that's a Minecraft rule, not a Blockpal one.

## Use

Type in chat:

| Chat | Effect |
|------|--------|
| `!ai help` | Show all commands |
| `!ai summon [name]` | Spawn your companion (default **Ethan**) |
| `!ai dismiss / come / follow / stay / guard / stop / where` | The everyday orders |
| `!ai name <name>` · `!ai skin <default\|robot\|ember\|void>` | Identity |
| `!ai personality <friendly\|cheerful\|grumpy\|stoic\|heroic\|shy>` | How it talks |
| `!ai bots` · `!ai inv` · `!ai say <text>` | Info & fun |
| `!ai <task>` | Natural-language task (see below) |

Or just talk — **`Ethan, follow me`** — exactly like the Java mod.
Right-click (tap) the companion to toggle follow/stay, and there's a
**spawn egg** in the creative inventory. `/scriptevent blockpal:ai <command>`
works too if you prefer slash commands (needs cheats).

Tasks the offline planner understands: `build a 5x5 floor of stone`,
`build a wall 8 long 3 high`, `build a tower 10`, `build a bridge 12`,
`build a house`, `mine a 3x3 hole`, `dig down 10`, `collect items`,
`kill the monsters`, `wait 10 seconds`, `jump`, `say <anything>`.

## Why there's no LLM in this version

Bedrock's Script API has **no network access in single-player worlds**
(`@minecraft/server-net` only exists on Bedrock Dedicated Server), so the
Java mod's cloud LLM planning physically can't run here. Instead the add-on
recreates the planning layer **on-device**: the same quick-intent handling,
plus a natural-language parser that turns build/mine/collect phrasing into
step-by-step action plans (one block per tick, watchdog-limited — the same
execution rules the Java mod converged on). Wiring the LLM back in for
worlds hosted on a Bedrock Dedicated Server is a possible future phase.

## Layout & tooling

```
bedrock/packs/behavior/   # entity behavior + all scripts (the "brain")
bedrock/packs/resource/   # model, animations, 4 skins, spawn egg, icons
bedrock/build.py          # zip both packs → builds/blockpal-bedrock-<v>.mcaddon
bedrock/tools/gen_assets.py  # regenerates skins + pack icons (pure stdlib)
```

Skins use the classic 64×64 player-skin layout — replace any PNG in
`packs/resource/textures/entity/companion/` with a standard skin file and
rebuild to customize.

Full docs: [`wiki/Bedrock-Add-On.md`](../wiki/Bedrock-Add-On.md).

## Requirements

**Minecraft Bedrock 1.21.80 or newer.** The add-on uses the `@minecraft/server` **2.x**
script API, which arrived in 1.21.80. On older versions Minecraft cannot provide that API
and will drop the script module without loading it.

## Commands

**Use `/blockpal:ai <command>`** on current Minecraft versions, or
**`/scriptevent blockpal:ai <command>`** on older ones. Cheats must be on, as for any
command.

```
/blockpal:ai help          /blockpal:ai summon [name]
/blockpal:ai come | follow | stay | guard | stop | where
/blockpal:ai name <name>   /blockpal:ai skin <default|robot|ember|void>
/blockpal:ai personality <friendly|cheerful|grumpy|stoic|heroic|shy>
/blockpal:ai bots | inv | say <text>
/blockpal:ai build a 5x5 floor of stone      (and other plain-language tasks)
```

Right-clicking a companion still toggles follow/stay, and the spawn egg claims it to
whoever is nearest.

### About typing `!ai` in chat

`!ai …` works **only if the world has "Beta APIs" switched on**. That isn't a choice
Blockpal makes: reading chat needs `world.beforeEvents.chatSend`, which Mojang ships as an
experimental-only API. Without the toggle there is no way for any add-on to see chat.

The add-on tells you which entry points came up when the world loads, so you never have to
guess — check the content log, or just read the hint the first time you spawn.

## If nothing happens at all

When the add-on loads it announces itself in chat:

```
Blockpal loaded ✓  Type /blockpal:ai help to begin.
```

**No message at all means the script module never ran.** Check:

1. **Minecraft 1.21.80+** (see Requirements above).
2. **Both packs applied to the world** — behaviour *and* resource. The behaviour pack
   depends on the resource pack and won't activate alone.
3. **Cheats on**, for any command to work.

### Two bugs that caused exactly this, now fixed

- **1.0.0** called an experimental-only chat API without checking it existed. That threw
  while the module was loading and took the whole add-on down — no `!ai`, no
  `/scriptevent`, no right-click, and no error anywhere.
- **1.0.x** declared `@minecraft/server` **1.17.0**, the Minecraft 1.21.60 API line.
  `2.0.0` was a breaking major, so on any current Minecraft that version does not exist
  and the script module was **dropped before running a single line** — which is why there
  were no logs at all, not even an error.

  Fixed in **1.1.0**: the pack targets `@minecraft/server` 2.0.0 with
  `min_engine_version` 1.21.80, and `bedrock/validate.py` fails the build if the declared
  API line is one Minecraft no longer ships.
