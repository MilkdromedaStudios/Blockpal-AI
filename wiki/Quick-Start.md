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

## Step 3 — (Optional) Add an API key for better AI

Simple chat commands (`follow`, `come`, `stop`, etc.) work instantly with no key —
and as of 3.17.0 the AI planner works **out of the box too**: with no key set,
Blockpal automatically uses a **free built-in AI service**, so your companion can
build and mine from the moment it spawns.

Adding your own key gets you faster, higher-quality models (HuggingFace is the
configured default):

**Fastest way (your own key):**

1. Get a free token at <https://huggingface.co/settings/tokens>
2. Run `/ai mymenu` → paste your token → **Save**

**Server owners — shared key:**

Run `/ai menu` → **AI & API** tab → paste the token → **Save**.  
Or set `BLOCKPAL_API_TOKEN` as an environment variable (never written to disk).
The moment a key is set it takes over from the free service; remove it and the
free fallback quietly steps back in.

---

## Step 4 — Try an AI task

```
/ai build a small house
/ai mine 10 iron ore
/ai clear the trees around me
```

The bot thinks for a moment, then gets to work. Use `stop` or `/ai stop` any time to cancel.

---

## The In-Game Wiki

The full reference is built right into the mod — no need to tab out:

- Open `/ai panel` → **Settings** → scroll to the bottom of the **Identity** tab → **Open In-Game Wiki**
- Or click **Open Wiki** on any page of the tutorial (`/ai tutorial`)

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
