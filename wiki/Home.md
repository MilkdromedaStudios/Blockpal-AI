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

1. **[Quick Start](Quick-Start)** — up and running in under a minute.
2. **[Installation](Installation)** — download the jar (or build it), install Fabric.
3. **[Getting Started](Getting-Started)** — summon Ethan and add an AI token.
4. **[Talking to Your Assistant](Talking-to-Your-Assistant)** — just type in chat.

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
- **Personalities** — pick how it talks & acts: friendly, cheerful, grumpy, stoic,
  heroic, shy. See [Personalities](Personalities).
- **Trust & per-bot management** — own several companions, set each up differently,
  and trust friends to command a specific one. See [Trust & Per-Bot Management](Trust-and-Per-Bot).
- **Emergency FPS kill-switch** — auto-disables the mod if framerate collapses.
- **Admin menu (ops)** — manage/kill all bots, set a bot cap, and see live stats
  (bots, players, FPS). See [Admin Menu](Admin-Menu).
- **Locked-down config & key protection** — only operators can change server-wide
  settings; your API key is obfuscated at rest and can live in an env var. See
  [Security](Security).
- **Works in every gamemode** — survival, adventure and creative.
- **Bedrock-friendly** — friends on iPad/console/phone can join and play with Ethan
  through a [Geyser](Geyser-Bedrock) proxy; no client mod needed on Bedrock.
- **One-click hosting** — a Java player can download & launch a Bedrock-ready server
  (Minecraft + Fabric + the latest Geyser & Floodgate) from the pause menu or `/aihost`.
  See [Bedrock (Geyser)](Geyser-Bedrock).

## Full page index

| Page | What's on it |
|------|--------------|
| [Installation](Installation) | Download, install, version compatibility |
| [Getting Started](Getting-Started) | First summon, AI token setup |
| [Commands](Commands) | Every `/ai` command |
| [Talking to Your Assistant](Talking-to-Your-Assistant) | Chat, quick intents, addressing by name |
| [Settings](Settings) | The in-game settings panel (no more setting commands) |
| [Performance Presets](Performance-Presets) | Normal / Opus / Potato |
| [Custom Skins](Custom-Skins) | Built-in and drop-in skins |
| [Personalities](Personalities) | How your companion talks & acts |
| [Trust & Per-Bot Management](Trust-and-Per-Bot) | Own several bots; let friends command one |
| [Running Commands](Running-Commands) | Command execution, permission levels, denylist |
| [AI Actions](AI-Actions) | The 16 action types the planner can use |
| [Bedrock (Geyser)](Geyser-Bedrock) | Let Bedrock players join via Geyser & Floodgate, plus one-click "Host with Blockpal" |
| [Per-Player Keys & Models](Per-Player-Keys-and-Models) | Bring-your-own-key, exemptions, selectable models |
| [Admin Menu](Admin-Menu) | Ops-only panel: manage/kill all bots, bot cap, live stats |
| [Security](Security) | Permission gating, API-key protection, what `.gitignore` can't do |
| [Terms & Policy](Terms-and-Policy) | Fair-use / no-cheating policy and disclaimers |
| [Developer Menu](Developer-Menu) | What every high-risk developer setting does |
| [More Info](More-Info) | How it works, architecture, changelog, precautions |
| [Troubleshooting](Troubleshooting) | Fixes for common problems |
| [Building From Source](Building-From-Source) | Gradle / Loom build docs |
| [FAQ](FAQ) | Quick answers |
</content>
