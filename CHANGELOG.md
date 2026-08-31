# Changelog

User-facing release notes for **Blockpal**. Keep the top entry written for players.

(Historical note: these sections used to be published verbatim as the Modrinth version
description. Releases now go to CurseForge, and `publish-one-curseforge` sends a short
fixed changelog line instead, so nothing here is uploaded automatically — this file is
for humans reading the repo.)

## 3.27.0

### The free AI is now a model that runs on your own graphics card

Blockpal's "works without an API key" option used to be a small free service on the
internet — shared with everybody, rate-limited, and occasionally just down. It has been
replaced with something better: Blockpal downloads a small model and runs it **on your
machine, on your GPU**.

```
/ai local setup      # shows exactly what it would download — downloads nothing
/ai local accept     # the yes. Only this starts a download.
```

- **Free forever.** No key, no account, no bill, no rate limit.
- **Private.** Prompts, and the pictures from your companion's eyes, never leave your
  computer.
- **Offline.** It works with the internet unplugged.
- **Fast.** About a second, against several for a cloud round trip.

**It asks first, every time.** `setup` tells you the model, its size, what it will run on
and where it goes; `accept` is you agreeing. Nothing downloads when you switch
connection, when the server starts, or when you open the settings panel — and a settings
packet cannot agree on your behalf.

### The models — all under 3 GB

| Pick | Model | Size | Good for |
|------|-------|-----:|----------|
| `qwen3b` | Qwen2.5 3B Instruct | 1.8 GB | **Default.** Best all-rounder that fits a 4 GB card. |
| `coder3b` | Qwen2.5 Coder 3B | 1.8 GB | Better at the bot's script language. |
| `llama3b` | Llama 3.2 3B Instruct | 1.9 GB | Chatty and friendly. |
| `qwen1.5b` | Qwen2.5 1.5B Instruct | 0.9 GB | Laptops and integrated graphics. |

Three gigabytes is a hard ceiling, enforced by the build rather than by good intentions.

### It picks the right build for your machine

Under the hood it runs llama.cpp's `llama-server`, and which build you get is the whole
difference between using your graphics card and grinding along on the CPU:

- **Windows + NVIDIA** → CUDA, with its GPU runtime libraries
- **Windows, other GPU** → Vulkan
- **Linux** → Vulkan (there is no Linux CUDA release; NVIDIA's Vulkan driver handles it)
- **Apple Silicon** → Metal, which is built into the standard macOS build
- **Anything else** → CPU, and it tells you it will be slow

### If you're already using the free service

Nothing changes. Your server stays on it and it keeps working — switching you over would
have meant a surprise two-gigabyte download on somebody else's connection. Move when you
want to with `/ai connection set local`.

### Also

- `/ai local` for status, `start`, `stop`, `models`, `log`.
- Settings → AI & API has the model picker, context window, GPU layers and port.
- Config schema 14 → 15.

## 3.26.0

### It learns by watching you play (PVT)

**PVT** is pre-video training: your companion learns how to *act* from watching
people play, instead of asking a language model what to do next. It follows
OpenAI's VPT — the work that learned Minecraft from unlabelled video — adapted to
what a mod can observe from inside the game.

```
/ai pvt watch on      # let it learn from how you play (off by default)
   ... just play ...
/ai pvt train         # (ops) train it on what people have played
/ai pvt use on        # (ops) let companions act on what they learned
```

Why it matters: a language model round trip takes seconds. A learned policy takes
tens of microseconds, so the bot reacts **within a tick** of something happening
in front of it — with no API key, no internet and no cost. It is also shallower:
it reproduces what it saw and nothing else. So the two work together. The learned
policy handles moment-to-moment movement and reaction; when it isn't confident it
hands the tick back and the model decides what to actually go and do.

**Recording is opt-in, per player, always.** Nobody is recorded until they run
`/ai pvt watch on` themselves. What's stored is which way somebody walked and
what was roughly in front of them — no chat, no names, no coordinates, no
inventory. Moments a walking companion could never reproduce (creative flight,
riding, spectating) are dropped rather than learned from. `/ai pvt watch off`
stops it immediately and `/ai pvt clear` deletes the lot.

Full detail: the **PVT — learning by watching** wiki page.

### It's much faster

*"Its actions are too slow"* — they were, and it wasn't one thing. A dozen
separate invented delays had accumulated: a pause between plan steps, a
randomised reaction jitter, a head that could only turn 22° a tick, a five-second
gap between thoughts, a rate limit on looking. Worst of all, the think cooldown
was measured from when the **previous** round started, so a bot that finished its
script in a second then stood still for four.

They all come from one setting now:

- **Instant** — no waiting at all. Turns on a dime, re-plans the moment it
  finishes, mines at double speed. Quickest, least lifelike.
- **Fast** *(new default)* — snappy, but still turns its head believably.
- **Human** — the old, deliberate feel, if you liked it.

```
/ai speed fast
```

Nothing here makes the bot cheat: mining still costs real block-hardness time at
Fast and Human, and reach, tool speed and pathfinding are untouched. Existing
worlds are moved to Fast automatically unless you had deliberately raised the
delay yourself, in which case your value is kept.

### It can actually fight

The old behaviour was "notice a monster, walk at it, swing". Now it holds a range
instead of standing inside the enemy's swing, circles on an irregular beat,
raises a shield between its own swings, times crits, uses a bow at range, and
backs off and eats when it's losing. Three levels: `basic` (the old behaviour),
`skilled` (default), `expert`.

```
/ai combat expert
```

**Fighting players is off by default and narrow when on.** Even with
`/ai admin pvp on`, a companion only fights someone who attacked it or its owner
in the last ten seconds, or who its owner named with `/ai attack <player>` — and
never its owner or anyone they trust, never someone in creative or spectator. A
server with PvP disabled overrides all of it anyway. There is no setting under
which a companion picks a fight.

### 24 new things it can do

The script language its AI writes went from 62 verbs to 86:

- **Real crafting** — `craft("stick", 8)` uses the server's own recipe book, so
  data-pack and modded recipes work too. Anything bigger than 2×2 needs a
  crafting table within reach, exactly as it would for you.
- **Proper jobs** — `tunnel(20)` digs a corridor, `stairsDown(15)` digs a
  staircase (and checks for liquid behind each block before breaking it, rather
  than dropping you into lava), `bridge(12)` walks forward laying blocks across a
  gap, `pillarUp(10)` jump-places upward, `mineVein(x,y,z)` follows a seam of ore
  instead of taking one block out of it.
- **Farming** — `harvest(radius)` breaks every fully grown crop nearby,
  `plant(radius)` sows seeds on empty farmland.
- **A memory** — `remember`/`recall`/`forget` and named waypoints, kept when the
  world is saved. Your companion can be told where home is and go back to it.
- **More senses** — inventory, nearest player, weather, time of day, dimension,
  armour, line of sight.
- **`defend(ticks)`**, **`sleep()`**, **`torch()`**.

All still done by hand: real break progress, real blocks out of the backpack, and
bridging that stops when they run out.

### A queue

```
/ai queue chop the oaks by the river
/ai queue build a wall along the north side
/ai queue list
```

Line up an afternoon's work instead of standing there feeding it one order at a
time. The list survives logging off.

### Also

- Every new setting is in the panel (**Settings → Behavior**), not just the
  config file.
- Outside AI apps connected over MCP get `pvt_status`, `pvt_train`,
  `combat_status` and `queue_task`.
- Config schema 13 → 14. Upgrading keeps everything, and never turns PvP on.

## 3.25.3
- **Bedrock add-on 1.1.0 — the real reason nothing worked.** 1.0.1 fixed a crash, but the
  add-on still did nothing *and produced no logs at all*, which turned out to be a second,
  separate bug: the pack asked Minecraft for script API `@minecraft/server` **1.17.0**.
  That is the Minecraft 1.21.60 line, and **2.0.0 was a breaking major** — so on any
  current version that API simply doesn't exist, and Minecraft **drops the script module
  before running a single line**. No commands, no error, no log entry. Nothing to see,
  which is exactly what was reported.
  - The pack now targets **`@minecraft/server` 2.0.0** with **`min_engine_version`
    1.21.80** (where 2.x arrived). **Minecraft Bedrock 1.21.80 or newer is required.**
  - The build now **fails** if the declared API line is one Minecraft no longer ships, so
    this class of silent death can't come back.
- **It tells you it loaded.** A few seconds after the world starts, Blockpal says
  `Blockpal loaded ✓` in chat, along with which command style to use. The content log is
  off by default for most players, so a console line was never going to be enough — "did
  it even start?" is now answerable without changing any settings.

## 3.25.2
- **The MCP server now hosts itself at `http://localhost:8000/blockpal`.** Set the AI
  connection to MCP and that address is live the moment your world loads — nothing to
  launch, nothing to keep open in another window. (The old `/mcp` path still works, so an
  AI app you already set up keeps running. If you were on the old default port, you're
  moved to 8000 automatically; a port you picked yourself is left alone.)
- **Bedrock add-on 1.0.1 — the commands actually work now.** In 1.0.0 *nothing* responded,
  and here is why: reading chat needs an API Minecraft only ships behind the "Beta APIs"
  experiment. The add-on called it without checking, which threw while the pack was
  loading and took the **entire** add-on down with it — no `!ai`, no `/scriptevent`, no
  right-click, nothing, and no error message anywhere.
  - Every entry point now registers independently, so one unavailable API costs that one
    feature and nothing else.
  - **`/blockpal:ai <command>`** is a real slash command on current versions, with
    **`/scriptevent blockpal:ai <command>`** always available as the fallback.
  - `!ai` in chat still works, but only with Beta APIs on — and the add-on now *says* so
    at world load instead of failing silently.
- **The Bedrock build refuses to package a broken pack.** Minecraft says nothing when an
  add-on is malformed, so `bedrock/build.py` now validates first: JSON, manifests and
  UUIDs, script syntax, every import and imported name, every entity event the scripts
  trigger, and every texture. It then runs the add-on against a stubbed Minecraft and
  drives the real commands — summon, follow, guard, rename, re-skin, build a floor — in
  three different world configurations. Any failure and no `.mcaddon` is written.

## 3.25.1
- **Everything is in the settings menu now.** A pile of options that could only be reached
  by hand-editing `config.json` (or not at all) have proper controls in `/ai menu`:
  - **AI & API** — an **Open setup guide** button next to the MCP settings, so connecting
    Claude/ChatGPT/Grok/Gemini is a click rather than remembering `/ai mcp`. Also the
    endpoint boxes for the **Player2 app** and the **free keyless service**, which
    previously existed only in the config file.
  - **Behavior → Its eyesight** — **picture width**, **picture height**, **how far it can
    see** and the **script time limit**. These decide how much your companion can make out
    and how long one of its plans may run.
  - **Behavior** — **Do things by hand** (build and mine like a survival player instead of
    reaching for commands) and **Human-like pauses** (small reaction delays so it doesn't
    act at inhuman speed).
  - **Behavior → Growth village** — how many villagers a game starts with, and the
    population that counts as "as big as ever".
- **Wiki: what each AI app actually needs.** The [MCP server](MCP-Server) page now spells
  out the thing that trips everyone up — apps that run on **your PC** (Claude Desktop,
  Claude Code, Gemini CLI, Cursor) need nothing but the address and token, while apps that
  run in **the cloud** (ChatGPT, grok.com, Google AI Studio) can't see `localhost` at all
  and need `/ai mcp remote on` plus an HTTPS tunnel, with copy-paste `ngrok` and
  `cloudflared` commands to set one up.

## 3.25.0
- **Connect the AI you already pay for — Blockpal now runs an MCP server.** This is the
  easiest way to give your companion a brain, and it's the headline of this release. Run
  `/ai connection set mcp` and then **`/ai mcp`**: an in-game guide opens with your world's
  address, your access token, and the exact config to paste for **Claude**, **ChatGPT**,
  **Grok** or **Gemini**, with copy buttons for each. Your AI app connects *to your world*,
  looks through your companion's eyes and drives it — no API key stored in the game, no
  second subscription. (Desktop apps like Claude Desktop and the Gemini CLI reach it
  straight away; cloud apps like ChatGPT and AI Studio need a tunnel plus `/ai mcp remote
  on`, and the guide says so.) The server is localhost-only and token-protected by default.
- **One AI connection at a time — no more guessing.** A key, Player2, Ollama and the free
  service could all be "on" at once, with a hidden priority order deciding which actually
  answered. Now there's a single **AI connection** picker at the top of **Settings → AI &
  API** (or `/ai connection set <mcp|key|player2|ollama|free|off>`), and choosing one turns
  the others off. A live line tells you outright what will answer. Upgrading servers keep
  whichever provider they were already using.
- **Your companion looks at the world and writes code.** The way it thinks has been
  rebuilt. It now gets a **picture rendered from its own eyes** — a real view, about 90°,
  nothing behind it and nothing through walls — decides what to do, and writes a short
  script that presses its own movement keys and mouse buttons. It mines at the speed its
  tool allows (with the cracks showing), reaches about as far as you do, and can only place
  blocks it's actually carrying. It is **a bit dumber** than the old planner that was handed
  coordinates. That's the trade, and it's deliberate: everything it manages now, a player
  could have managed. Prefer the old behaviour? **Settings → Behavior → Thinking style →
  Classic action plan (JSON)**.
- **It uses chests — and furnaces, barrels, shulkers and hoppers.** Standing within reach,
  it opens the container (lid animation and click included), takes and stores stacks, and
  drops ore and fuel into the right furnace slots by itself.
- **It never teleports.** Companions used to blink to your side when they fell behind. They
  don't any more — they walk, swim and climb like something actually living in the world.
  `/ai come` sends it walking and tells you how far it has to go. Because a flying player
  can out-run that in seconds, you get a **one-time warning screen** the first time you
  enter creative with a companion out.
- **It lives on its own.** With no AI connected at all — MCP idle, connection set to "off",
  or the service down — it still eats when hurt, swims up when it's drowning, runs out of
  fire, hops when it's wedged, and makes its own way back to you.
- **New commands.** `/ai look` reads out what your companion can actually see right now.
  `/ai code <script>` hands it a script in the same little language its AI writes (and
  `/ai code stop` takes the controls back). `/ai connection` and `/ai mcp …` are above.

## 3.24.0
- **Pick your AI in one click — HuggingFace, ChatGPT, Claude, Gemini or Grok.** Open
  `/ai menu` → **AI & API** and you'll find a new **AI provider** switch at the top. Flip it
  and Blockpal fills in the right web address and a good starting model for that company — no
  more hunting down and typing an API URL. **ChatGPT** even comes with a public demo key
  already filled in (it's probably out of juice, so swap in your own OpenAI key for real use);
  the others just need you to paste that provider's own key in the token box. Editing the URL
  yourself is still fine — the switch simply reads **"Custom"** then. Playing on Bedrock or a
  vanilla client? Use `/ai admin provider chatgpt` (or `huggingface`/`claude`/`gemini`/`grok`);
  bare `/ai admin provider` lists them and shows which one you're on.

## 3.23.0
- **Mini-games are now `/ai minigame`.** The old `/game` command moved under `/ai`, so it
  lives with everything else: `/ai minigame start <mode>`, `/ai minigame list`, and
  `/ai minigame stop` (just `/ai minigame` lists the modes). Same five modes — **Chained,
  Same Health, One Block, Fusion,** and **Growth** — and the Growth village is now
  `/ai minigame start growth` (or `/village start`). The standalone `/game` command is gone.
- **Player2 local AI actually works now.** If you turned on **Use Player2** with the free
  Player2 app but bots stayed silent or complained about the AI, this is why: the app needs
  Blockpal to sign in through it first, and the mod wasn't doing that — so every request was
  turned away. Now Blockpal does Player2's built-in login for you automatically (nothing to
  paste), so with the **Player2 app running and signed in**, your companion just works.
  Player2 problems now tell you what's wrong ("make sure the app is running and you're
  signed in") instead of a generic error.

## 3.22.0
- **Ollama and Player2 are now in the settings menu.** Open `/ai menu` → **AI & API** and
  you'll find a new **"Local & easy AI"** section with **Use Player2** and **Use local
  Ollama** switches, their model/URL boxes, and a live line telling you exactly which AI
  your bots will use. No more needing the `/ai admin …` chat commands to turn them on.
- **Cleaner, Sodium-style tabs.** The settings tabs (Identity, Behavior, AI & API, Combat,
  Developer) now run **down the left side** with the options beside them — the same tidy
  layout as Sodium.
- **Village AIs work together.** In the **Growth** game, a village with lots of different
  jobs filled gets a **teamwork bonus**, and villagers now **team up on shared jobs**
  ("teaming up to raise the walls — together we get twice as much done"), so they visibly
  cooperate to grow the town.

## 3.21.0
- **Run your own AI locally — or the easiest AI ever.** Two new ways to power your
  companion, no HuggingFace key needed:
  - **Ollama (custom local models).** Point Blockpal at a local Ollama and run **any
    model you've pulled** — `llama3.2`, `qwen2.5`, `phi3`, whatever — with **no key and
    no internet**. Turn it on with `/ai admin ollama on` (set the model with
    `/ai admin ollama model <id>`).
  - **Player2 (player2.game).** The lowest-effort option: **install the free Player2
    app** and it just works, keyless, on your machine. Or go **online** — set a
    `PLAYER2_KEY` and Blockpal uses Player2's cloud with the strong **`gpt-oss-120b`**
    model. `/ai admin player2 on`. (The key is read from your environment and is never
    written to disk or shipped inside the mod.)
  A real HuggingFace key still takes priority if you've set one.
- **It plays more like a real player now.** The companion **does things by hand** and
  only runs commands when it truly has to — so it builds, digs and gathers like a
  survivor instead of `/fill`-ing everything. It also knows how to **use work stations**,
  not just crafting tables: furnaces, smithing tables, anvils, brewing stands, looms,
  stonecutters and more. And it **no longer snatches items instantly** — small, human
  reaction pauses make it feel alive (toggle with the new *humanize* setting).
- **New game mode: Growth — an AI village that grows or dies on its own.**
  `/village start` (or `/game start growth`) grows a living village of AI people around
  you. Each villager has a **job** (builder, farmer, teacher, trader, guard, scholar),
  its **own personality**, and — with local models — its **own brain**, so they think
  differently. Days run at **2× speed**; they **build houses, farm, teach and trade**,
  welcome newcomers when they thrive and lose people when they starve or get raided —
  and they **talk about what they're doing**. You can **`/village join <role>`** and be
  one of them. **If the village dies out, you win. If it grows as big as ever, you can
  `/village surrender`.** Check on it any time with `/village status`.

## 3.20.0
- **The AI chat now lives right in the ESC menu.** Press ESC and a mini chat panel
  is sitting on the right side of the pause menu — recent conversation, a text box,
  Enter to send. It also appears when you open the **chat screen** (`T`). No more
  clicking a button into a separate menu for a quick question; the **"Full chat &
  history ⛶"** button still opens the full box when you want scrolling and threads.
- **Model errors finally tell you what's wrong.** An HTTP 400 from the AI service
  now shows **the service's actual error message** and the **model id in use**
  instead of a generic guess. Model ids are **scrubbed automatically** everywhere
  you enter them (stray spaces, quotes and invisible paste characters were a silent
  cause of "my valid model returns 400"). And if you set an id like
  `Qwen/Qwen2.5-Coder-3B-Instruct-GGUF`, Blockpal warns you right away: `-GGUF` /
  `-GPTQ` / `-AWQ` repos are **download bundles for local apps** (llama.cpp,
  LM Studio, Ollama) — hosted APIs serve the **base** model
  (`Qwen/Qwen2.5-Coder-3B-Instruct`) instead.
- **"Host current world" copies much faster.** World files are now copied **in
  parallel** instead of one at a time — a big world that took minutes now takes a
  fraction of that — and the Host screen shows **live progress**
  (`Copying "MyWorld" into the server… 62% (410/660 MB)`), both when hosting starts
  and when your changes are synced back afterwards. No more staring at a frozen
  "Copying…" line.

## 3.19.0
- **Your companion has a voice now.** Hold **V** (rebindable) and *speak* — your
  words are transcribed with **Whisper large-v3-turbo** and go straight to **your
  own companion**, never public chat. Quick orders ("follow me", "stay") are
  instant; anything else becomes an AI task, exactly like typing.
- **It talks back — privately.** Everything the agent says is also spoken out
  loud with a natural text-to-speech voice. Only **you** hear your agent by
  default. Pick its voice with `/ai voice set <id>` (`nova`, `onyx`, `shimmer`…)
  and your client default with `/aivoice voice <id>`.
- **Share & link voices.** `/ai voice share <player>` lets a friend hear your
  agent too (and `unshare` / `clear` / `list` manage it). Sharing links your
  agents into one conversation.
- **Advanced talking.** Linked agents **take turns** — a server-side conversation
  queue means one speaks while the others wait, so shared companions never talk
  over each other; your client also plays speech one line at a time.
- **Server management.** Ops gate the whole feature with `/ai admin voice on|off`
  or the new **"Allow agent voice"** toggle (Settings → Behavior tab).
- Privacy: the microphone is only open while the key is held (30 s cap), audio is
  transcribed from your own machine and **never sent to the game server** — only
  the final text is. With no API key, voice falls back to the free voice service,
  so it works out of the box. New wiki page: **Voice**. Config schema → v10.

## 3.17.2
- **API key fields now mask like a password box.** The **API token** field
  (Settings → AI & API tab) and the personal key field (**My Settings** /
  `/ai mymenu`) now show dots (••••••) instead of plaintext by default. Press
  the new **Show key** toggle beneath either box to switch it to an editable
  plaintext field for typing or pasting your key; toggle it off again to
  re-mask (your typing is kept, just hidden). This only ever affects text
  you're currently typing — an already-saved key is still never sent back to
  the menu at all, so the security model from 3.16.1/3.17.1 is unchanged.

## 3.17.1
- **Fixed: a typed API key could silently vanish before it was ever saved.** The
  settings menu holds your edits in a draft while you move around it, but the API
  key box was rebuilt **empty** every time its tab re-appeared — so if you pasted
  your key and then clicked another tab and came back (say, to check *Chat
  listening* while setting up the AI), resized the window, or toggled fullscreen,
  the key was quietly dropped and **Save saved everything except the key**, while
  still reporting "Settings saved ✓". The config file then showed no token, which
  looked exactly like "the key won't save". Now:
  - a key you've typed **stays in the box** when you switch tabs and come back,
    and survives window resizes — until you Apply/Save it (a key that's already
    saved is still never shown back; that's privacy, not loss);
  - a new **"➤ Key typed but not saved yet — press Apply or Save"** status line
    shows under the box whenever a key is pending, so saved vs. not-saved is
    always visible;
  - switching to another panel in the top bar (Admin / Bots / My Settings) now
    **applies pending edits first** instead of discarding them with the screen.
- Reminder while checking the file: a saved key lives in `config.json` as
  `hfTokenObf` (obfuscated at rest) — the `hfToken` field is *always* empty on
  disk by design, so don't judge by that line. The AI & API tab's
  "✔ API key saved" status is the source of truth.

## 3.17.0
- **The AI now works with no API key at all.** With no key set anywhere, Blockpal
  automatically falls back to a **free built-in AI service** (Pollinations, keyless
  and OpenAI-compatible), so your companion can plan, build, mine and chat from the
  moment it spawns — zero setup. HuggingFace stays the configured default: the
  moment you add a key (shared or personal) it takes over, and removing it brings
  the free fallback back. Ops can turn the fallback off ("Free AI fallback" toggle
  on the AI & API tab) to strictly require a key. The startup log, tutorial, in-game
  wiki and the AI & API tab's status line all now tell you which mode you're in.
- **Settings saves are crash-safe.** The config file is now written atomically
  (fully serialized in memory, written to a temp file, then swapped into place), so
  a crash, full disk or antivirus interruption can never leave a half-written
  `config.json` behind. The previous good file is kept as `config.json.prev`, and a
  transient write failure (e.g. a virus scanner briefly locking the file) is
  retried automatically. This fixes the "settings sometimes don't save" reports.
- **A new look: dark, futuristic, blue.** Every Blockpal screen — Settings, Admin,
  Bots, My Settings, the Possession console, the Tutorial, the in-game Wiki and the
  Host screen — now draws a shared "holo-terminal" theme: a deep space-navy backdrop
  with a faint hologram grid, console plates with cyan edge lights and bracketed
  corners, and neon-cyan headings (the old yellow/gold accents are gone). Same
  layout and widgets, so everything is where it was — it just looks like the future.

## 3.16.1
- **Fixed: settings (and your API key) not saving in singleplayer.** The owner of a
  singleplayer or LAN world now always counts as a Blockpal admin — even with cheats
  off. Before, the server could silently refuse the settings you saved from the menu
  and re-sync the old values, which wiped the API key you'd just typed and meant
  `config/blockpal/` was never written.
- **You can now SEE that the key saved.** The AI & API tab shows an
  **"✔ API key saved"** line once a key is stored — the key box still empties after
  Apply, but that's privacy (your key is never sent back to the menu), not the key
  being lost. Leaving the box blank keeps the saved key. The "Settings saved ✓" chat
  message now also shows exactly **where** the config file was written (helpful on
  launchers like Lunar that don't use the vanilla `.minecraft` folder), and a failed
  write shows a red error instead of failing silently.
- **Hosting starts much faster after the first run.** "Host with Blockpal" now reuses
  previously downloaded components (the Minecraft server jar by checksum; Fabric,
  Geyser and Floodgate re-checked at most once a day) instead of re-downloading
  ~60 MB on every Start — and when hosting your current world, the world is copied
  **first**, before any downloading, so it's captured moments after the save closes.
- **Pause-menu button moved.** "Host with Blockpal" now sits in the bottom-left
  corner of the pause menu, where it can no longer overlap "Save and Quit to Title"
  at larger GUI scales.
- **Lunar Client awareness.** When Blockpal detects Lunar Client, the Host screen
  points out Lunar's own built-in world hosting for Java-only friends (Lunar has no
  API a mod could call to start it automatically) — Blockpal hosting remains the way
  to add Bedrock cross-play.

## 3.8.0
- **Play with Ethan from Bedrock (iPad, console, phone).** Blockpal now works for
  **Minecraft Bedrock Edition** players who join through a [Geyser](https://geysermc.org)
  proxy. Because the companion, chat and commands all run on the server, your Bedrock
  friends can summon Ethan, talk to it, and give it tasks — with **no mod to install on
  their device**. Set your server up with **Geyser-Fabric + Floodgate-Fabric**; Blockpal
  treats Floodgate as **optional**, so a server without it still runs exactly as before.
- **Bedrock-aware fallbacks.** The visual menus and FPS watchdog are Java-client
  features a Bedrock device can't run, so Blockpal now recognises Bedrock players and
  points them to a clear text/command alternative instead of a menu they can't open.
- **Configure the AI without the GUI.** New ops-only text commands so a Bedrock (or
  vanilla) admin can set everything from chat: **`/ai admin token <key>`**,
  **`/ai admin apiurl <url>`**, **`/ai admin model <id>`**.
- **New wiki page:** [Bedrock (Geyser)](Geyser-Bedrock) — full setup, what works, and the
  one known limitation (Geyser has no general custom-entity support, so Ethan's
  *appearance* may render oddly on Bedrock even though it works fully).

## 3.7.0
- **In-game AI Manual.** Every player gets a one-time **AI Manual** book on first join —
  right-click it for a 5-page in-game guide (Quick Start, Commands, Personalities,
  Settings & API key, Custom Skins). The first-run tutorial gained two pages, and a new
  [Quick Start](Quick-Start) wiki page gives the shortest path to a working companion.

## 3.6.0
- **Custom personalities.** Beyond the six built-ins, you can now write your *own*
  personality in plain words — "a wise old wizard", "a sarcastic robot butler", etc.
  Set it with **`/ai personality custom <text>`** or in the **My Settings** screen
  (`/ai mymenu`), where there's now a Personality picker and a custom text box.
- **Kept family-friendly automatically.** Custom text is checked by the AI before it's
  applied — anything with profanity, slurs, adult or otherwise unsafe content is
  rejected with a reason, so it stays appropriate for all ages.
- **In the settings panel, not just commands.** The Settings → Identity tab now has a
  **Default personality** picker (the personality new bots spawn with), and ops get an
  **"Allow custom personalities"** toggle (Behavior tab) to restrict players to the
  built-ins if they want.

## 3.5.0
- **Your companion now has a personality.** Pick how it talks *and* how it acts with
  **`/ai personality <id>`** — choose from **friendly** (the classic Ethan),
  **cheerful**, **grumpy**, **stoic**, **heroic** or **shy**. Run `/ai personality`
  on its own to see the list and which one your bot is using.
- Each bot remembers its own personality, so different companions can have different
  vibes. The personality flavours every quick reply (follow, come, stay, gear pick-ups,
  …) and is woven into the AI planner, so the things it *says* mid-task stay in
  character too.
- Server owners can set the default for newly summoned bots (the new
  `defaultPersonality` setting; defaults to **friendly**, so existing worlds sound
  exactly as before).

## 3.4.1
- **Behind-the-scenes / docs only — no gameplay changes.** The mod itself is identical
  to 3.4.0.
- Release, wiki and build automation now only run **after a pull request is merged**
  (never when one is just opened), so work-in-progress that gets closed never ships.
- The wiki and developer docs were brought up to date with the 3.2–3.4 changes.

## 3.4.0
- **Everything's in one panel now.** Open it with **`/ai panel`** (or `/ai menu`).
  Tabs across the top switch between **Settings** (admins), **Admin** (ops) and
  **My Settings** (everyone), so you no longer hunt for separate menus.
- **No more confusing setting commands.** `/ai settings`, `/ai token`, `/ai listen`,
  `/ai active` and `/ai commands` are gone — change everything in the panel instead.
  Your everyday commands (summon, follow, come, stay, `/ai mykey`, `/ai model`, …)
  are unchanged.
- **Admins can change more from the panel** — allow-commands, permission levels,
  admin level, the bot cap, bring-your-own-key and model choice are now toggles in
  the Admin panel, no commands or file-editing needed.
- **New first-run tutorial.** Fresh installs greet you and open a short how-to
  walkthrough on first join. Reopen it any time with **`/ai tutorial`**.

## 3.3.0
- **Bring your own API key.** Server owners can now make players use their *own*
  API key (so one person isn't stuck with the whole bill). Turn it on with
  `/ai admin requirekey on`; players set their key with `/ai mykey <token>` or
  privately in the new `/ai mymenu` screen. Keys are stored scrambled and never
  shown to anyone else.
- **Key whitelist.** `/ai admin keylist add <player>` lets trusted players keep
  using the server's shared key even when "bring your own key" is on.
- **Pick your AI model.** Admins curate a list of models
  (`/ai admin models add|remove|list <id>`), and players choose which one their
  companion uses with `/ai model <id>`, `/ai models`, or the `/ai mymenu` screen.
  Turn player choice off with `/ai settings allow_model_choice false`.

## 3.2.0
- **New admin menu (ops only).** `/ai admin menu` opens a built-in admin panel —
  see and manage **every bot on the server**, kill them all at once, flip bots
  off/on for everyone, and set how many bots are allowed at a time. `/ai admin stats`
  and `/ai admin list` show the same info as text.
- **Live server stats.** The admin menu shows total bots vs. the cap, who owns how
  many bots, and each player's **FPS**, plus mod status and whether an API key is set.
- **Bot limit.** Owners/ops can cap how many Blockpal companions exist at once
  (`/ai admin maxbots <0-50>`, default 8). `/ai summon` politely refuses past the cap.
- **Tighter security.** Only operators can now change server-wide settings (API key,
  API URL, model, command permissions) or use the admin tools — this closes a hole
  where any player with the mod could change them. Everyday commands (summon, follow,
  come, stay, etc.) are unchanged for everyone. Who counts as an "op" is adjustable
  with `/ai settings admin_level <0-4>`.
- **Better API-key protection.** Your token is no longer stored as plain text in the
  config file (it's obfuscated), and you can instead provide it through the
  `BLOCKPAL_API_TOKEN` environment variable so it never touches disk at all. It's
  still never shown to other players or written to the log.

## 3.1.0
- Updated Blockpal to **Minecraft 26.2** (the "All En" update).
- Now published for both the **Fabric** and **Quilt** loaders.
- Fixed the in-game `/ai menu` settings screen against the 26.2 client API change.

## 3.0.0
- Renamed the mod to **Blockpal**: new mod id, texture namespace and
  `config/blockpal/` config folder. This is a fresh setup — configs and skins
  from older "Nexus AI" / "AI Assistant" installs are not carried over.

## 2.14.0
- Rebranded the display name to **Nexus AI** (later renamed again to Blockpal).
