# Getting Started

Once the mod is [installed](Installation), getting going takes about a minute.

> **In a hurry?** See the [Quick Start](Quick-Start) page for the shortest path
> to a working companion.

> **First time?** On your first join, Blockpal greets you, opens a short in-game
> tutorial, and gives you an **AI Manual** item. Right-click the manual any time
> to open the full in-game wiki. Reopen the tutorial with **`/ai tutorial`**.

## 1. Summon your assistant

In-game, run:

```
/ai summon
```

A companion named **Ethan** spawns next to you with a nametag. Rename it any time
with `/ai name <name>`.

## 2. Add an AI token (one-time)

Quick commands (come, follow, stay…) work out of the box, but **AI-planned tasks need
an API token.** Blockpal uses HuggingFace's router (OpenAI-compatible) by default.

1. Create a free token at <https://huggingface.co/settings/tokens>.
2. Open the panel with **`/ai menu`** → **AI** tab → paste it into the **API token**
   field → **Save**. (Server owners can instead set the `BLOCKPAL_API_TOKEN`
   environment variable so it never touches disk.)

Prefer everyone to use their own key? Each player can set theirs in **`/ai mymenu`**
(or `/ai mykey <token>`) — see [Per-Player Keys & Models](Per-Player-Keys-and-Models).
Your token persists in `config/blockpal/config.json` (obfuscated) and survives updates.

## 3. Try it out

| Try typing in chat | What happens |
|--------------------|--------------|
| `follow me` | Follows you *(instant, no token)* |
| `Ethan, build a 5×5 floor` | Sends the task to the AI planner |
| `can you clear these trees?` | Active analysis figures out you want them mined |

See **[Talking to Your Assistant](Talking-to-Your-Assistant)** for the full chat system,
or **[Commands](Commands)** for the slash-command reference.

## Using a different model or provider

Point it at any OpenAI-compatible endpoint (Ollama, LM Studio, OpenAI…) from the
panel's **AI** tab — set the **API URL** and **Model** fields, then **Save**
(e.g. URL `http://localhost:11434/v1/chat/completions`).

More in **[Settings](Settings)**.
</content>
