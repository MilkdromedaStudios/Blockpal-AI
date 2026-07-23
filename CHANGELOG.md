# Changelog

User-facing release notes for **Blockpal**. The section matching the current
`mod_version` is published to Modrinth as that version's description, so keep the
top entry written for players.

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
