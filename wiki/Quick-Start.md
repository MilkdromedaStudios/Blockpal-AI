# Quick Start

> **In a hurry?** Here's everything you need in under a minute.
> The full guide is at [Getting Started](Getting-Started).

---

## Step 1 — Spawn your companion

```
/ai summon
```

A companion named **Ethan** appears next to you. Rename it any time:

```
/ai name <name>
```

---

## Step 2 — Talk to it

Just type in chat — **no slash needed**:

| Say… | What it does |
|------|--------------|
| `follow me` | Follows you |
| `come` | Calls it to you |
| `stay` | Guards its position |
| `stop` | Cancels the current task |
| `Ethan, build a 5×5 floor` | Sends it to the AI planner |
| `do it yourself` | Hands off control — it picks its own tasks |

Or use `/ai <task>` for a direct command without addressing it by name.

---

## Step 3 — Add an API key (for AI-planned tasks)

Simple chat commands (`follow`, `come`, `stop`, etc.) work instantly with no key.
For the AI planner (building, mining, complex tasks) you need an API key.

**Fastest way (your own key):**

1. Get a free token at <https://huggingface.co/settings/tokens>
2. Run `/ai mymenu` → paste your token → **Save**

**Server owners — shared key:**

Run `/ai menu` → **AI** tab → paste the token → **Save**.  
Or set `BLOCKPAL_API_TOKEN` as an environment variable (never written to disk).

---

## Step 4 — Try an AI task

```
/ai build a small house
/ai mine 10 iron ore
/ai clear the trees around me
```

The bot thinks for a moment, then gets to work. Use `stop` or `/ai stop` any time to cancel.

---

## Your AI Manual

You received an **AI Manual** on first join. **Right-click it** to browse the full
in-game reference — commands, personalities, settings, skins, and more.

> Can't find it? It won't be given again, but `/ai tutorial` reopens the short
> how-to, and all the same content lives here in the wiki.

---

## Useful commands at a glance

| Command | What it does |
|---------|--------------|
| `/ai summon` | Spawn your companion |
| `/ai dismiss` | Remove it |
| `/ai follow` | Follow you |
| `/ai stay` | Guard position |
| `/ai come` | Come to you |
| `/ai stop` | Cancel task |
| `/ai locate` | Find it |
| `/ai skin <name>` | Change skin |
| `/ai personality <id>` | Change personality |
| `/ai mymenu` | Your settings (model + key) |
| `/ai panel` | Open the full settings panel |
| `/ai tutorial` | Reopen the how-to tutorial |

---

## Next steps

- [Commands](Commands) — full `/ai` command reference
- [Personalities](Personalities) — give Ethan a character
- [Settings](Settings) — AI model, behaviour, and more
- [Custom Skins](Custom-Skins) — drop in your own PNG skin
- [Admin Menu](Admin-Menu) — server-wide controls (ops)
- [Per-Player Keys & Models](Per-Player-Keys-and-Models) — everyone brings their own key
