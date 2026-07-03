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

Two different things can look like "the key didn't save":

- **The box emptying after Save/Apply is normal.** For privacy the server never
  sends your key back to the menu, so the box is always blank when the screen
  (re)opens. Look for the **"✔ API key saved"** line under the box (3.16.1) and the
  green **"Settings saved ✓"** chat message — leaving the box blank later *keeps*
  the saved key.
- **Before 3.16.1, singleplayer saves could be silently refused.** The owner of a
  singleplayer world without cheats didn't count as an "admin", so the server
  rejected the save and reset the menu — wiping what you'd typed. Fixed: the world
  owner is now always an admin of their own world, and if a save ever fails you get
  a red chat message with the reason instead of silence.

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
- Free-form messages (not starting with a name/keyword) also need **Active analysis**
  on (Behavior tab) and a working API key.
- Remember **owner-only obedience** — only the player who summoned it is obeyed.

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
