# AI Assistant — a Minecraft companion that builds, fights, and helps

A Fabric mod that drops a friendly AI character into your world. Its name is
**Ethan** by default, it wears a floating **nametag** so you always know who it
is, and you can boss it around either with plain chat ("*Ethan, follow me*",
"*help me mine this tree*") or with simple `/ai` commands.

Tasks like building, mining, or fighting are planned by a large language model
through an OpenAI-compatible API (HuggingFace by default), so the assistant can
turn a sentence into a sequence of in-game actions.

---

## ✨ Features

- **Named & tagged** — spawns as **Ethan** with a visible nametag above its
  head. Rename it any time with `/ai name <name>` and the tag updates live.
- **Talk to it in chat** — no slash command needed. It actively listens to chat
  and reacts to natural language (toggle with `/ai listen on|off`).
- **Instant quick-commands** — "come here", "follow me", "stay", "stop", and
  "where are you" work immediately, even with no API token.
- **AI-planned tasks** — anything else ("build a 5×5 floor", "mine this tree")
  is sent to a language model that returns a step-by-step plan.
- **Follows you** — keeps pace and teleports to you if it falls too far behind.
- **Fights for you** — automatically engages hostile mobs near you, and retreats
  when badly hurt.
- **Builds & breaks** — places and breaks blocks to carry out building tasks.
- **Find it anywhere** — `/ai locate` reports distance, direction, and coords.
- **Friendly errors** — connection problems give clear, actionable advice
  instead of raw `java.net.ConnectException` stack traces.

---

## 📦 Requirements

- Minecraft **26.1.2**
- **Fabric Loader** ≥ 0.19.0 and **Fabric API**
- Java **25+**

Drop the built jar into your `mods/` folder (server and/or client).

---

## 🔑 One-time setup: add an AI token

Quick commands work out of the box, but **AI-planned tasks need an API token.**
The mod uses HuggingFace's router (an OpenAI-compatible endpoint) by default.

1. Create a free token at <https://huggingface.co/settings/tokens>.
2. In-game, run:
   ```
   /ai token <your_token>
   ```

That's it. To use a different model or provider, see [Settings](#-settings).

---

## 🗣️ Talking to your assistant (no slash needed)

If chat listening is on (it is by default), just type in chat. A message is
treated as a command when it **starts with the assistant's name** or with a
**command word** (help, come, follow, mine, build, attack, stop, …).

| You type in chat                     | What happens                                  |
|--------------------------------------|-----------------------------------------------|
| `follow me`                          | It follows you                                |
| `come here`                          | It comes to you (teleports if far)            |
| `stay` / `wait`                      | Holds position and keeps watch                |
| `stop`                               | Cancels the current task                      |
| `where are you?`                     | Tells you where it is                         |
| `Ethan, build a wall`                | Sends "build a wall" to the AI planner        |
| `help me mine this tree`             | Sends the request to the AI planner           |

Addressing it by name (`Ethan, ...`) always works; the name prefix is stripped
before the rest is run as a command or task.

Prefer pure commands? Turn listening off with `/ai listen off`.

---

## ⌨️ Commands

| Command                | Description                                              |
|------------------------|----------------------------------------------------------|
| `/ai help`             | Show the in-game command list                            |
| `/ai summon [name]`    | Spawn an assistant (defaults to **Ethan**)               |
| `/ai dismiss`          | Send your assistant away (removes it)                    |
| `/ai come`             | Call it to you (teleports if far away)                   |
| `/ai follow`           | Have it follow you                                       |
| `/ai stay`             | Hold position and keep watch                             |
| `/ai stop`             | Cancel the current task, stand by                        |
| `/ai locate` / `/ai where` | Report its distance, direction, and coordinates      |
| `/ai <task>`           | Give it a task in plain language (e.g. `/ai build a 3x3 platform`) |
| `/ai name <name>`      | Rename it (nametag updates instantly)                    |
| `/ai token <token>`    | Set your AI service API token                            |
| `/ai listen on\|off`   | Turn chat listening on or off                            |
| `/ai settings`         | Show advanced configuration                              |

### Advanced settings

```
/ai settings                          # show everything
/ai settings model <model-id>         # e.g. mistralai/Mistral-7B-Instruct-v0.2
/ai settings api_url <url>            # any OpenAI-compatible chat endpoint
/ai settings temperature <0.0–2.0>    # creativity of the planner
/ai settings max_tokens <32–2048>     # max response length
/ai settings follow_distance <1–32>   # how close it follows
/ai settings guard_radius <4–64>      # how far it looks for hostiles
```

Settings are saved to `config/ai-assistant.json` in your game directory.

---

## 🧠 What the AI can do (actions)

When you give a task, the language model replies with a plan made of these
building-block actions, which the assistant then performs in order:

`MOVE_TO`, `PLACE_BLOCK`, `BREAK_BLOCK`, `ATTACK_NEAREST`, `FOLLOW_PLAYER`,
`LOOK_AT`, `CHAT`, `WAIT`, `COLLECT_ITEM`, `STOP`.

---

## 🛠️ Troubleshooting

**"I can't connect to the AI service" / it used to say `java.net.ConnectException`**
- This almost always meant the old, deprecated HuggingFace endpoint. The mod now
  targets the supported router endpoint by default. Make sure your `api_url` is
  `https://router.huggingface.co/v1/chat/completions` (the default) — reset it
  with `/ai settings api_url https://router.huggingface.co/v1/chat/completions`.
- Check that the machine running the game/server actually has internet access
  and isn't behind a firewall blocking outbound HTTPS.

**"My API token is missing or invalid."**
- Set a valid token with `/ai token <token>`.

**"That model wasn't found."**
- Pick a model your provider supports: `/ai settings model <model-id>`.

**It doesn't react to chat.**
- Make sure listening is on: `/ai listen on`. Start the message with its name
  ("Ethan, …") or a command word ("help …", "build …", "follow …").

**Using a local model (Ollama, LM Studio, …)?**
- Point the mod at it: `/ai settings api_url http://localhost:11434/v1/chat/completions`
  and `/ai settings model <local-model-name>`.

---

## 📄 License

MIT — see [LICENSE](LICENSE).
