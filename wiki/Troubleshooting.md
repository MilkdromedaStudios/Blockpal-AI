# Troubleshooting

> Configuration is done in the in-game panel (`/ai menu` / `/ai panel`) — open it and
> use the **AI**, **Behavior** and **Developer** tabs. New to the mod? Run
> **`/ai tutorial`**.

### "Can't connect to the AI service"

Open `/ai menu` → **AI** tab and reset **API URL** to the supported HuggingFace router
endpoint: `https://router.huggingface.co/v1/chat/completions`, then **Save**.

### "My API token is missing or invalid"

Open `/ai menu` → **AI** tab → paste a fresh token into **API token** → **Save**.
Create a free token at <https://huggingface.co/settings/tokens>. (Server owners can
instead set the `BLOCKPAL_API_TOKEN` environment variable.) Each player can set their
own key in `/ai mymenu`.

### My API key won't save / the token box goes empty

A few different things can look like "the key didn't save":

- **The box emptying after Save/Apply is normal.** For privacy the server never
  sends your key back to the menu, so the box is always blank when the screen
  (re)opens. Look for the **"✔ API key saved"** line under the box (3.16.1) and the
  green **"Settings saved ✓"** chat message — leaving the box blank later *keeps*
  the saved key.
- **In the file, the key is in `hfTokenObf`, not `hfToken`.** If you open
  `config.json` to check, the `"hfToken"` line is *always* empty on disk by
  design; a saved key is stored obfuscated in `"hfTokenObf"`. An empty
  `hfTokenObf` means no key is saved; a long garbled value means it is.
- **Before 3.17.1, moving around the menu could silently drop a typed key.**
  Pasting the key and then switching to another tab and back, resizing the
  window, or clicking a top-bar panel (Admin / Bots / My Settings) rebuilt the
  key box empty and discarded what you'd typed — Save then saved everything
  *except* the key while still reporting "Settings saved ✓". Fixed: a
  typed-but-unsaved key now stays in the box across tab switches and resizes, a
  **"➤ Key typed but not saved yet"** status line shows while it's pending, and
  panel switches apply pending edits first.
- **Before 3.16.1, singleplayer saves could be silently refused.** The owner of a
  singleplayer world without cheats didn't count as an "admin", so the server
  rejected the save and reset the menu — wiping what you'd typed. Fixed: the world
  owner is now always an admin of their own world, and if a save ever fails you get
  a red chat message with the reason instead of silence.
- **On a multiplayer server, the key is saved on the SERVER.** The "Settings
  saved ✓" message shows the server-side path — your own `.minecraft/config/`
  folder won't (and shouldn't) contain the server's key.

### I can't find `config/blockpal/` in `.minecraft`

The config folder is created inside the **game directory of the launcher you use**.
Third-party launchers (Lunar, custom launcher profiles) often point at a different
folder than vanilla's `.minecraft`. The green "Settings saved ✓" chat message
(3.16.1) shows the full path of `config.json`, and the game log prints
`Blockpal config file: …` at startup — that's where your settings and key live.

### "That model wasn't found"

Open `/ai menu` → **AI** tab and set **Model** to a valid id, then **Save**. Players
choose from the allowed list in `/ai mymenu` or with `/ai model <id>`.

### It doesn't react to chat

- Open `/ai menu` → **Behavior** tab and make sure **Chat listening** is on.
- Messages that start with its name (`Ethan, come`) or a command word (`come`,
  `follow`, `build …`, `mine …`) work with **no API at all** — if even those are
  ignored, check: is the bot within ~128 blocks and in the same dimension? Did
  the [FPS kill-switch](Performance-Presets) trip (`/ai resume`)?
- **Free-form** messages (not starting with a name/keyword) go through **Active
  analysis** (Behavior tab) and need a *working* AI: a saved key, or the free
  built-in AI (3.17.0). If the analysis call fails — the free service having an
  outage or rate-limiting, a bad key — the bot stays **silent** by design (it
  can't tell a failed check from "not talking to me"). Named/keyword commands
  still work during an outage, and failed *tasks* do get a spoken error.
- Remember **owner-only obedience** — only the player who summoned it (plus
  players it [trusts](Trust-and-Per-Bot) and admins) is obeyed; others get a
  polite refusal only when they address it by name.

### Voice: "the free voice service has no speech model" / the agent doesn't speak

Voice input and output are separate network services from the text AI, and the
action bar tells you which one failed:

- **Voice input (push-to-talk):** with an API key it uses Whisper
  large-v3-turbo on HuggingFace (a **free** HF token is enough — `/ai mykey`).
  Keyless input relies on the free voice service, which **currently has no
  speech model** (it went text-only in July 2026) — so if the action bar says
  so, add a free key and talk away. A "key was rejected" message means the
  token itself is bad — re-check `/ai mykey`.
- **Agent speech (TTS):** synthesis goes to the `freeApiUrl` endpoint with an
  OpenAI-style audio request. While that service has no audio model, Blockpal
  logs one clear explanation, pauses speech attempts for 10 minutes at a time,
  and everything else (chat text, tasks, voice input) keeps working. To get
  speech back now, point `freeApiUrl` at any audio-capable OpenAI-compatible
  endpoint (a local server with an audio model, or a keyed voice service).
- The text AI is unaffected either way — it stays fully keyless via the free
  fallback.

### Using Ollama / LM Studio / another local model

In `/ai menu` → **AI** tab set **API URL** (e.g. `http://localhost:11434/v1/chat/completions`)
and **Model** to your local model name, then **Save**.

### FPS tanked and the assistant went silent

The [emergency FPS kill-switch](Performance-Presets) tripped. Once framerate recovers,
run `/ai resume` (or `/ai enable`).

### Lag spikes or a server freeze during tasks

You may have lowered a [Developer-tab](Developer-Menu) setting too far. Open
`/ai menu` → **Behavior** and pick the **Potato** (or **Normal**) preset to restore
safe values in one click.

### The settings menu opens when I don't want it to

Sneak-right-click opening the menu can trip accidentally. Turn off
**Sneak-click opens menu** on the **Behavior** tab. `/ai menu` always opens it regardless.

### My custom skin doesn't show up

- It must be a **64×64** PNG in `config/blockpal/skins/`.
- Apply it by filename without extension: `/ai skin my_skin`.
- After editing the file, run `/aiskins reload`.

Still stuck? [Open an issue](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/issues).
