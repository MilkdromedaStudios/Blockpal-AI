# Welcome to the Blockpal Wiki

**Blockpal** is a Minecraft **Fabric** mod that drops a friendly AI companion entity
(default name **Ethan**) into your world. It reads your chat, plans multi-step tasks
through a language model, fights back on reflex, manages its own inventory and gear,
and configures itself from a real in-game settings screen.

> **Blockpal** was previously released as *Nexus AI*. As of **3.0.0** everything was
> renamed: the mod id is now `blockpal`, the texture namespace is `blockpal:`, and
> settings live in `config/blockpal/`. Configs and skins from older `ai-assistant`
> builds are **not** carried over — set 3.0.0 up fresh.

## New here? Start with these

1. **[Installation](Installation)** — download the jar (or build it), install Fabric.
2. **[Getting Started](Getting-Started)** — summon Ethan and add an AI token.
3. **[Talking to Your Assistant](Talking-to-Your-Assistant)** — just type in chat.

## What it can do

- **Named companion** — spawns as **Ethan** with a live nametag; rename any time.
- **Reads the room** — *active analysis* runs chat through an LLM so "*can you clear
  these trees?*" just works, no command words needed.
- **AI-planned tasks** — natural-language requests become structured step-by-step plans.
- **Instant quick-commands** — come / follow / stay / stop / where are you, no token needed.
- **Always reacting** — combat and retreat run on an instant reflex layer in every
  mode, with no API call; planning is fully async.
- **Runs commands** — `/setblock`, `/fill`, `/give`, `/summon`, `/clone`, `/effect`…
  safely gated. See [Running Commands](Running-Commands).
- **Inventory & gear** — picks up drops, auto-equips the best weapon/armor, eats food.
- **Custom skins** — built-in palettes or your own PNG. See [Custom Skins](Custom-Skins).
- **Emergency FPS kill-switch** — auto-disables the mod if framerate collapses.
- **Works in every gamemode** — survival, adventure and creative.

## Full page index

| Page | What's on it |
|------|--------------|
| [Installation](Installation) | Download, install, version compatibility |
| [Getting Started](Getting-Started) | First summon, AI token setup |
| [Commands](Commands) | Every `/ai` command |
| [Talking to Your Assistant](Talking-to-Your-Assistant) | Chat, quick intents, addressing by name |
| [Settings](Settings) | Settings GUI + `/ai settings` keys |
| [Performance Presets](Performance-Presets) | Normal / Opus / Potato |
| [Custom Skins](Custom-Skins) | Built-in and drop-in skins |
| [Running Commands](Running-Commands) | Command execution, permission levels, denylist |
| [AI Actions](AI-Actions) | The 16 action types the planner can use |
| [Developer Menu](Developer-Menu) | What every high-risk developer setting does |
| [More Info](More-Info) | How it works, architecture, changelog, precautions |
| [Troubleshooting](Troubleshooting) | Fixes for common problems |
| [Building From Source](Building-From-Source) | Gradle / Loom build docs |
| [FAQ](FAQ) | Quick answers |
</content>
