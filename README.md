# AI Assistant — a Minecraft companion that builds, fights, and helps

A Fabric mod that drops a friendly AI character into your world. Its name is
**Ethan** by default, it wears a floating **nametag** so you always know who it
is, and it's **proactive** — it reads your chat and figures out when you need it,
so you don't have to address it by name or memorise any commands. You can still
boss it around with plain chat ("*Ethan, follow me*") or `/ai` commands, and
everything is configurable from a **real in-game settings menu**.

Tasks like building, mining, or fighting are planned by a large language model
through an OpenAI-compatible API (HuggingFace by default), so the assistant can
turn a sentence into a sequence of in-game actions.

---

## ✨ Features

- **Named & tagged** — spawns as **Ethan** with a visible nametag above its
  head. Rename it any time with `/ai name <name>` and the tag updates live.
- **Proactive — reads the room** — with *active analysis* (on by default) it
  runs **every** chat message past the language model to decide whether you need
  it and what you want, so "*can you clear these trees?*" or "*I need a shelter*"
  just work — no name, no exact command words. Toggle with `/ai active on|off`.
- **In-game settings menu** — a real GUI (not just chat commands) for the token,
  model, behaviour toggles and tuning sliders. Open it with the **K** key or
  `/ai menu`.
- **Talk to it in chat** — no slash command needed. It actively listens to chat
  and reacts to natural language (toggle with `/ai listen on|off`).
- **Instant quick-commands** — "come here", "follow me", "stay", "stop", and
  "where are you" work immediately, even with no API token.
- **AI-planned tasks** — anything else ("build a 5×5 floor", "mine this tree")
  is sent to a language model that returns a step-by-step plan.
- **Never just stands there** — combat and retreat are handled by an instant
  **reflex layer that runs in every mode, with no API call**. Planning is fully
  asynchronous, so the assistant keeps fighting and dodging *while* it thinks —
  it won't freeze mid-generation while a creeper walks up to it.
- **Runs commands** — with command execution on, plans can use `/setblock`,
  `/fill`, `/give`, `/summon`, `/clone`, `/effect`… This is its key to doing
  *almost anything*, including precise **redstone** (block states via
  `/setblock`). Safely gated — see [Letting it run commands](#-letting-it-run-commands).
- **Solves puzzles & escape rooms** — it sees nearby **levers, buttons, doors,
  pressure plates and redstone** in its context and can flip/press/open them
  (`USE_BLOCK`), or walk onto plates, to progress.
- **Keeps going (live activities)** — ongoing jobs like *patrol*, *guard the
  area*, *keep mining* or *explore* loop continuously, re-planning each round
  with fresh context instead of stopping after one batch.
- **Follows you** — keeps pace and teleports to you if it falls too far behind.
- **Fights & flees** — engages hostile mobs near you and your assistant, and
  retreats toward you when badly hurt instead of dying in place.
- **Builds, breaks, digs & parkours** — places/breaks blocks, clears areas
  (`MINE_AREA`), jumps (`JUMP`) and crouches (`SET_SNEAK`).
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

If chat listening is on (it is by default), just type in chat.

**Two layers of understanding:**

1. **Instant matching (no API).** Messages that **start with the assistant's
   name** or a **command word** (help, come, follow, mine, build, attack, stop, …)
   are handled immediately, with no API call and no token needed.
2. **Active analysis (uses the AI).** With *active analysis* on (the default) and
   a token set, **every other message** is quietly run past the language model,
   which decides whether you're talking to the assistant and what you want. You
   don't have to use its name or any exact words.

| You type in chat                     | What happens                                  |
|--------------------------------------|-----------------------------------------------|
| `follow me`                          | It follows you (instant)                      |
| `come here`                          | It comes to you, teleports if far (instant)   |
| `stay` / `wait`                      | Holds position and keeps watch (instant)      |
| `stop`                               | Cancels the current task (instant)            |
| `where are you?`                     | Tells you where it is (instant)               |
| `Ethan, build a wall`                | Sends "build a wall" to the AI planner        |
| `can you clear out these trees?`     | Active analysis → mines the trees             |
| `ugh, I really need a shelter`       | Active analysis → builds you a shelter        |

Addressing it by name (`Ethan, ...`) always works; the name prefix is stripped
before the rest is run as a command or task.

Prefer pure commands? Turn chat listening off with `/ai listen off`, or keep
listening but turn off the proactive AI with `/ai active off`.

---

## ⌨️ Commands

| Command                | Description                                              |
|------------------------|----------------------------------------------------------|
| `/ai help`             | Show the in-game command list                            |
| `/ai menu` / `/ai config` | Open the in-game settings menu (or press **K**)       |
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
| `/ai active on\|off`   | Turn proactive AI analysis of every message on or off    |
| `/ai commands on\|off` | Allow/forbid the assistant running Minecraft commands    |
| `/ai settings`         | Show advanced configuration                              |

---

## 🔧 Settings

### The settings menu (a real screen)

Press **K** in-game (rebindable under *Options → Controls → AI Assistant*) or
run `/ai menu` to open a proper settings screen with:

- **Toggles** — chat listening, active analysis, debug logging, allow commands
- **Text fields** — assistant name, API token, API URL, model
- **Sliders** — temperature, max tokens, follow distance, guard radius,
  command permission level

Hit **Save** to apply, **Cancel** (or **Esc**) to discard. The token field stays
blank when one is already set — leave it blank to keep the current token, or type
a new one to replace it. Changes are sent to the server, so the menu works in
both singleplayer and on a server (where you also need the mod installed locally
to open the screen).

### Advanced settings via chat

Every option is also available as a command:

```
/ai settings                          # show everything
/ai active on|off                     # proactive analysis of every message
/ai listen on|off                     # react to chat at all
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
building-block actions, which the assistant performs in order:

| Action | What it does |
|--------|--------------|
| `MOVE_TO` | Walk/path to a position |
| `PLACE_BLOCK` / `BREAK_BLOCK` | Place or break a single block |
| `MINE_AREA` | Clear every block in a small box (digging, tunnels) |
| `USE_BLOCK` | Flip a lever, press a button, open a door/trapdoor/gate |
| `RUN_COMMAND` | Execute a Minecraft command (redstone, fills, gives, summons…) |
| `ATTACK_NEAREST` | Strike the nearest hostile |
| `FOLLOW_PLAYER` | Path to and follow a player |
| `LOOK_AT` | Face a position |
| `JUMP` / `SET_SNEAK` | Hop (parkour) / crouch |
| `CHAT` | Say something |
| `WAIT` / `COLLECT_ITEM` / `STOP` | Pause / pick up / end the plan |

The planner can also set **`"loop": true`** on a plan to mark an *ongoing*
activity (patrol, guard, keep building) — the assistant then re-plans and keeps
going with fresh context each round. Combat is **not** something it has to plan:
the reflex layer handles it automatically, in every mode, so plans focus on the
actual task.

---

## ⚡ Letting it run commands

`RUN_COMMAND` is what turns the assistant from "places blocks one by one" into
"can build a working redstone contraption" — the model can emit, e.g.,
`/setblock ~ ~ ~ minecraft:repeater[facing=east,delay=2]` or `/fill` a whole
structure at once.

Because that's powerful, it's gated:

- **Toggle:** on by default. Turn off with `/ai commands off` or the **Allow
  commands** switch in the menu.
- **Permission level (default 2):** the assistant runs commands at this vanilla
  level. Level 2 is the *command-block tier* — it allows `/setblock`, `/fill`,
  `/clone`, `/give`, `/summon`, `/tp`, `/effect`, `/time`, `/weather`, but **not**
  server-admin commands (`/op`, `/stop`, `/ban`… need level 3–4). Adjust with the
  **Command perm level** slider in the menu.
- **Denylist:** a handful of dangerous commands (`stop`, `op`, `ban`, `whitelist`,
  `reload`, …) are always refused, regardless of level.

On a shared server, set the level to taste (or turn it off) — and remember the
assistant is driven by an LLM, so only grant what you're comfortable with it
improvising.

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
- Make sure listening is on: `/ai listen on`. Instant commands work with no
  token — start the message with its name ("Ethan, …") or a command word
  ("help …", "build …", "follow …").
- For free-form messages that don't use those words, you need **active analysis
  on** (`/ai active on`) **and** an API token set (`/ai token <token>`), since
  that path asks the language model what you meant.

**Using a local model (Ollama, LM Studio, …)?**
- Point the mod at it: `/ai settings api_url http://localhost:11434/v1/chat/completions`
  and `/ai settings model <local-model-name>`.

---

## 🏗️ Building from source

The mod is a standard **Fabric + Gradle (Loom)** project, so you don't need to
install Gradle yourself — the included wrapper (`gradlew`) downloads the right
version for you.

### Prerequisites

- **JDK 25** (Temurin/Adoptium recommended). Check with `java -version`.
- **Git** (to clone the repo).
- An internet connection for the **first** build — Loom downloads Minecraft,
  the Fabric toolchain, and dependencies, then caches them.

### Build the jar

```bash
git clone https://github.com/MilkdromedaStudios/minecraft-ai-test.git
cd minecraft-ai-test

# Linux / macOS
./gradlew build

# Windows (PowerShell or cmd)
gradlew.bat build
```

The finished mod lands in **`build/libs/`**:

- `ai-assistant-<version>.jar` ← **this is the one** you drop into `mods/`
- `ai-assistant-<version>-sources.jar` — source jar, not needed to play

Copy the main jar into the `mods/` folder of a Fabric-enabled client or server
(you also need **Fabric API** there).

### Run it in a dev environment

Loom generates ready-to-go run configurations — no separate install needed:

```bash
./gradlew runClient   # launch a dev client with the mod loaded
./gradlew runServer   # launch a dev server (accept the EULA on first run)
```

### Handy Gradle tasks

| Task                     | What it does                                            |
|--------------------------|----------------------------------------------------------|
| `./gradlew build`        | Compile, run checks, and produce the jars in `build/libs`|
| `./gradlew runClient`    | Start a development Minecraft client                     |
| `./gradlew runServer`    | Start a development Minecraft server                     |
| `./gradlew clean`        | Delete `build/` for a from-scratch rebuild               |
| `./gradlew --refresh-dependencies build` | Rebuild and re-resolve dependencies     |

> **JDK note:** if Gradle can't find Java 25, point it at your JDK by adding
> `org.gradle.java.home=/path/to/jdk-25` to `gradle.properties`, or set a
> `JAVA_HOME` that targets JDK 25.

### Project layout

```
src/main/java        # common mod: entity, AI planner, commands, chat, networking
src/client/java      # client-only: rendering, keybind, the settings GUI
src/main/resources   # fabric.mod.json, lang files, assets
build.gradle         # Loom build script   ·   gradle.properties — versions
```

Key versions live in [`gradle.properties`](gradle.properties) (Minecraft, Fabric
Loader, Fabric API, Loom).

---

## 📄 License

MIT — see [LICENSE](LICENSE).
