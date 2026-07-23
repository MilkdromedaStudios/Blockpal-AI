# Blockpal — project notes for Claude

**Blockpal** is a Minecraft **Fabric** mod that adds a friendly AI companion
entity (default name **Ethan**). Tasks are planned by an LLM over an
OpenAI-compatible API.

> **Naming note:** the mod is **Blockpal**. As of **3.0.0** the internal
> identifiers were renamed to match: mod id `blockpal`, Java package
> `com.milkdromeda.blockpal`, texture namespace `blockpal:` and config folder
> `config/blockpal/`. It was previously released as **Nexus AI** (display-name
> rebrand in 2.14.0) and originally "AI Assistant", both under the `ai-assistant`
> id. The 3.0.0 rename is a **breaking change** — configs/skins from older
> installs are not read. The default companion name is still **Ethan**.
> Note: only the *mod* was renamed; the GitHub repo is still `Nexus-Minecraft-AI`.

---

## Maintenance rule — keep these sections current

**Every time a new build is shipped (new `mod_version`):**

1. Update the **Features** section below to reflect any added, changed, or
   removed capabilities.
2. Add a new entry at the top of the **Changelog** section with the version
   number and a bullet list of what changed.
3. Bump `mod_version` in `gradle.properties` and copy the new jar into
   `builds/` (see *Build artifacts* below).

Do not skip this. These two sections are the living record of what the mod
can do and how it evolved.

**When adding Java implementation files:**

- Add each new `*.java` filename/path to `.gitignore` or confirm it is covered by
  the existing `*.java` ignore rule before staging anything.
- If a Java source file must be tracked, review it for API-key/secret handling
  risks and force-add it deliberately rather than relying on normal `git add`.

---

## Features

### Companion entity
- Spawns as a player-model entity named **Ethan** (configurable).
- Survives in all gamemodes; right-click toggles follow/stay in adventure mode.
- Custom skin support: built-in `default`, `steve`, `robot`, `void`, `slate`,
  `ember`, `forest`, `amethyst`, or **your own PNG** dropped into
  `config/blockpal/skins/` and applied with `/ai skin <name>` (loaded as a
  dynamic texture at runtime — no rebuild needed). `/aiskins list` and
  `/aiskins reload` (client-side) manage the folder.

### Personalities (3.5.0+)
- Each bot has a **personality** that drives both *how it talks* and *how it acts*.
  Six are built in (`Personality` enum): **friendly** (the historical default Ethan),
  **cheerful**, **grumpy**, **stoic**, **heroic**, **shy**.
- Change a nearby bot's personality with **`/ai personality <id>`**; `/ai personality`
  with no argument lists them and marks the bot's current one. The chosen one is
  persisted per-bot in NBT (`Personality` tag), so companions can differ.
- A personality supplies the quick, no-API chat-response pools (come/follow/stay/stop,
  autonomous hand-off, name acknowledgement, gear pick-ups and junk-tossing) **and** a
  `style()` line appended to the planner's system prompt, so any `CHAT` action the LLM
  writes stays in voice (without changing the JSON schema or chosen actions).
- The server default for freshly summoned bots is `defaultPersonality` (config, default
  `friendly`). Resolution: a bot's stored personality wins; an unknown/missing one falls
  back to the server default.

#### Custom personalities (3.6.0+)
- Players can write a **free-text custom personality** ("a wise old wizard", "a
  sarcastic robot butler") with **`/ai personality custom <text>`** or in the **My
  Settings** screen (`/ai mymenu`), which now has a Personality cycler (built-ins +
  `custom`) and a custom text box. Stored per-bot in NBT (`CustomPersonality`); a
  non-blank custom text overrides the built-in's `style()` in the planner via
  `entity.getPlanStyle()`, while the built-in `personality` still supplies the quick
  no-API reply pools (a neutral base voice). `getPersonalityLabel()` reads "Custom".
- **AI moderation** — custom text is safety-checked by the language model
  (`HuggingFaceClient.moderatePersonality` → `Moderation(allowed, reason)`,
  family-friendly prompt, temp 0) **before** it's applied. Rejections (profanity,
  slurs, adult/unsafe content) come back with a short reason; if there's no usable API
  key the text is refused (can't verify). The flow lives in
  `AiAssistantEntity.requestCustomPersonality(text, issuer)` (async, applies on the
  server thread), shared by the command and the `PlayerPrefsPayload` handler.
- **Ops limit** — `allowCustomPersonality` (config, default true) gates the whole
  feature; a "Allow custom personalities" toggle sits on the Settings → Behavior tab.
- **In the panel** — the Settings → **Identity** tab has a **Default personality**
  picker (writes `defaultPersonality`). Config schema → v6 (upgrading installs default
  `allowCustomPersonality` true).

### AI / LLM planning
- Connects to any **OpenAI-compatible** API (HuggingFace, Ollama, OpenAI,
  LM Studio, etc.) via `apiUrl` + `hfToken`.
- **Local & easy AI providers (3.21.0)** — beyond a key and the free service, two
  opt-in providers slot into `ApiAuth.resolveFor` (priority: real key → Player2 →
  Ollama → free):
  - **Ollama / custom local models** — `ollamaEnabled` points the bot at a **local
    Ollama** (`ollamaUrl`, default `http://localhost:11434/v1/chat/completions`;
    `ollamaModel`, default `llama3.2`) or any keyless local OpenAI-compatible server,
    so **custom local models** run with no key or internet.
    `/ai admin ollama on|off|url|model|models …`.
  - **Player2 (player2.game)** — `player2Enabled`. **Keyless local**: install the
    Player2 app (`player2Url`, `localhost:4315`). **Online cloud** when a
    **`PLAYER2_KEY`** is set (`player2OnlineUrl` `https://api.player2.game/v1/chat/
    completions`, `Authorization: Bearer`, default model **`gpt-oss-120b`**). The key
    comes from the **`PLAYER2_KEY` env / `-Dplayer2.key`** (used, never written to disk
    or baked into the jar; a config value is obfuscated at rest). Player2 endpoints also
    get the `player2-game-key` header. `/ai admin player2 on|off|url …`.
  - A keyless **local** endpoint is "usable" via `ApiAuth`'s new `local` flag.
- **Free keyless fallback (3.17.0)** — when *no* key resolves for a request (no
  shared key, no personal key), the bot automatically uses a **free keyless
  OpenAI-compatible service** (`freeApiUrl`, default Pollinations
  `https://text.pollinations.ai/openai`, model `freeModel` default `openai`), so
  the AI works out of the box. HuggingFace stays the shown/configured default and
  **always wins the moment a token is set**. Resolution lives in
  `HuggingFaceClient.ApiAuth.resolveFor(owner, name)` (token+model+url+free flag);
  requests go to `auth.url()`. Gate checks use `ModConfig.aiAvailable()` /
  `aiAvailableFor(owner, name)` (`hasApiToken()` stays strictly "key set" for
  display). Ops toggle: `freeAiFallback` (AI & API tab, default true; off = a real
  key is strictly required). Config schema → v8.
- Natural-language tasks (`/ai build a 5×5 floor`) are converted to a
  structured JSON action plan (5–15 steps) on a background thread. The request uses
  OpenAI-style `response_format: {"type":"json_object"}` and the reply is parsed
  leniently (`parseStep`) — a missing/lower-case `action`, the alternate
  `{"ACTION": {…}}` shape, or one malformed step never fails the whole plan — so the
  small/free models the keyless fallback and cheap keys use plan reliably.
- **Model-id hygiene + real API errors (3.20.0)** — `ai/ModelIds` scrubs every
  entered model id (trim, wrapping quotes/backticks, internal whitespace and
  zero-width/BOM paste artifacts) at all entry points (`/ai admin model`,
  `/ai admin models add`, `/ai model`, the Settings GUI via `ConfigData.applyTo`,
  `PlayerPrefsPayload`, and `ModConfig.normalize()` on load so a broken saved id
  self-heals). Ids naming **quantized download bundles** (`…-GGUF`/`-GPTQ`/`-AWQ`
  etc. — hub file repos that hosted APIs don't serve) get an immediate warning with
  the suggested base id. HTTP error handling (`HuggingFaceClient.friendlyHttpError`)
  now parses the **error body's actual message** (OpenAI-style `error.message`, flat
  `error`, `message`, `detail`) and reports it plus the model id in use, so a 400 is
  actionable instead of a guess.
- 16 available actions: `MOVE_TO`, `PLACE_BLOCK`, `BREAK_BLOCK`, `MINE_AREA`,
  `USE_BLOCK`, `RUN_COMMAND`, `JUMP`, `SET_SNEAK`, `ATTACK_NEAREST`,
  `FOLLOW_PLAYER`, `LOOK_AT`, `CHAT`, `WAIT`, `COLLECT_ITEM`, `STOP`.
- **Survival-first, "use tables", commands last (3.21.0)** — the planner prompt does
  things **by hand** (move/place/break/mine/collect) and treats **RUN_COMMAND as a last
  resort** (only when a task truly can't be done by hand). It uses **work stations** —
  not just crafting tables: furnace/blast_furnace/smoker, smithing_table, anvil,
  brewing_stand, loom, stonecutter, cartography_table, grindstone, enchanting_table (added
  to `AiTaskManager.isInteresting`'s context scan). `preferSurvivalActions` (default true;
  off re-enables liberal command use via a system-prompt note).
- **Human-like pacing (3.21.0)** — `humanizeActions` (default true) adds small randomised
  reaction pauses so the bot doesn't act instantly: a jittered inter-step delay
  (`ExecuteTaskGoal.interStepDelay`), a "reach out" dwell before COLLECT_ITEM, and a
  "notice" pause before `CollectItemsGoal` pursues loot. Off = snappy/instant.
- Looping tasks (patrol, guard, farm) re-plan continuously with fresh context.
- Planning is async — entity stays responsive and fights back during planning.
- **Per-bot model override (3.21.0)** — `AiAssistantEntity.setAiOverride(ApiAuth)` (transient)
  lets a bot use a specific model/endpoint instead of resolving from its owner; the Growth
  village game uses it to give each villager a different small local model.

### Per-player API keys & selectable models (3.3.0+)
- **Bring-your-own-key** — `requireOwnApiKey` (off by default) makes each bot use
  *its owner's* personal key instead of the shared server key, so one server owner
  isn't stuck with everyone's API bill. Players set their key with `/ai mykey
  <token>` (or privately in `/ai mymenu`); it's stored **obfuscated per-UUID**
  (`playerApiKeysObf`), never shown or logged.
- **Exemption whitelist** — `ownKeyWhitelist` lists players who may keep using the
  shared key even when `requireOwnApiKey` is on (`/ai admin keylist add|remove|list
  <player>`; stored as lowercased usernames). Resolution: a player's personal key
  always wins; else if BYOK is required and they're not whitelisted → no AI (a
  friendly "set your own key" prompt); else the shared key.
- **Selectable models** — `allowedModels` is an admin-curated list (a model
  "whitelist") players pick from via `/ai model <id>`, `/ai mymenu`, or the picker
  (`playerModels`, keyed by UUID; falls back to `hfModel`). `allowPlayerModelChoice`
  toggles player choice off (everyone uses the server default). Admins manage the
  list with `/ai admin models add|remove|list`; the server default is always kept in
  it. Resolution happens per-bot from the owner in `ModConfig.resolveTokenFor` /
  `resolveModelFor`, threaded through `HuggingFaceClient.ApiAuth`. Player prefs ride
  `PlayerPrefsSyncPayload` (S→C) / `PlayerPrefsPayload` (C→S).

### Per-bot management & trust (3.9.0+)
- **Visual Bots panel (3.11.0)** — `/ai bots` (Java client) opens a **Bots** panel: a
  scrollable picker of **every** bot on the server, each showing **who owns it**, so on a
  busy server you can find and manage a specific companion instead of "the nearest one".
  Selecting a bot shows its details (owner, mode, dimension, position, health, personality,
  trusted count) and gives buttons to **command** it (come/follow/stay/stop) and **manage**
  it (rename, re-skin, change personality, dismiss). The panel is server-authoritative: it
  carries per-viewer `canCommand`/`canManage` flags (so disallowed buttons are greyed) and
  the server **re-checks permission again when an action runs** (`BotActionPayload` →
  `AiNetworking.applyBotAction`), so a modified client can't bypass it. It's a tab in the
  shared `PanelNav` (`Settings · Admin · Bots · My Settings`). Backed by `BotListData`
  (`gather(server, viewer)`), `BotListRequestPayload` (C→S), `BotListSyncPayload` (S→C),
  `BotActionPayload` (C→S); UI in `client/gui/BotManagerScreen.java`. Bedrock/vanilla
  clients can't open it, so `/ai bots` falls back to the text listing for them.
- **Manage bots individually** — `/ai bots` lists every companion **you** own across
  all dimensions (name, mode, dimension, position, health, personality and trusted
  count), so they're no longer an indistinguishable group. The everyday management
  commands (`/ai name`, `/ai skin`, `/ai personality`, `/ai trust`) act on the
  companion you're standing next to, so each can be set up differently — or pick any
  bot directly in the visual Bots panel above.
- **Trust** — the owner can let other players command a specific bot. `/ai trust
  <player>` (player must be online) adds them; `/ai untrust <player>` removes them
  (by current name, or stored name if they're offline); `/ai trust list` shows the
  list and `/ai trust clear` empties it. Trust is **per bot**, stored in the entity's
  NBT (`Trusted`, a `TrustEntry(uuid, name)` list — see `entity/TrustEntry.java`), so
  each companion keeps its own trusted circle.
- **Two authority tiers.** `AiAssistantEntity.canCommand(player)` = owner **or** a
  trusted player; server admins (`AdminAccess`) are always allowed on top. Trusted
  players (and admins) may give a bot **orders** — come/follow/stay/stop, locate,
  inventory, and AI tasks — in chat and via `/ai …`. **Managing** a bot (rename, skin,
  personality, dismiss, and editing the trust list itself) stays owner-or-admin only.
  The chat owner-gate and the `/ai` command handlers both enforce this server-side
  (`ensureCanCommand` / `ensureCanManage`), closing the old gap where any nearby player
  could `/ai follow`/`dismiss` someone else's bot.

### Chat system
- **Chat listening** — monitors all server chat; trigger words activate the
  assistant without using its name.
- **Direct addressing** — `"Ethan, follow me"` / `"Ethan: follow me"`.
- **Quick intents** — common phrases (`come`, `follow`, `stop`, `stay`,
  `where are you`) handled instantly with no API call.
- **Active analysis** — LLM classifies every 5+ char message within 48 blocks;
  rate-limited to ~once per 3 seconds to avoid API spam.
- **Expressive responses** — all chat messages use natural first-person dialogue
  in `Name: "message"` format; randomised response pools so replies vary naturally.
- **Calls for help** — when health drops critically low in combat, the assistant
  calls out to nearby players for help before retreating.
- **Owner-only (plus trust)** — by default only the player who spawned the assistant
  can give it orders; other players are politely turned away. The owner can **trust**
  specific players (see *Per-bot management & trust* below) so they may command it too;
  server admins can always command/moderate any bot.
- **Autonomous mode** — owner can say "do it yourself" to hand off control; the
  bot self-directs, picks its own tasks, and narrates its decisions every ~30 s.
  Cancelled by "stop", "follow me", or "stay".

### Possession mode (3.16.0+)
- **Hand your character to the bot.** `/ai possess` lets a player give control of
  **their own** character to their nearby owned companion; the AI then drives the
  *player's body* (move, mine, place, use, fight, collect, run commands, chat) from
  typed instructions. Only ever self-possession with a bot you own — no cross-player
  control, so there's nothing to grief.
- **Fully server-authoritative → no client mod needed to be controlled.** All planning
  and driving runs on the server, so it works on **any server with Blockpal** and in
  **singleplayer**. Locomotion pushes the player with server-side velocity + teleport
  catch-up (the same technique `MinigameManager` uses on players), so it needs no
  client-side movement mod; the trade-off is feel (a "tug", documented as such).
- **The console (the "little textbox").** On a Java client `/ai possess` opens
  `client/gui/PossessionConsoleScreen` — a text box with a live status log. Typing an
  instruction sends `PossessionInputPayload` (`instruction`); the server streams status
  back via `PossessionSyncPayload`, appended in place so typing isn't interrupted.
  Bedrock/vanilla clients (no GUI) steer it entirely by text: `/ai possess <instruction>`
  and `/ai possess stop`, with status delivered as chat (`AiNetworking.sendPossession`
  falls back to a system message when the client can't receive the packet).
- **Code.** `possession/PossessionManager.java` (server-tick driving loop keyed by
  player UUID; start/stop/queue; disconnect cleanup — wired in `AiAssistantMod`) and
  `possession/PossessionSession.java` (per-session planning via `HuggingFaceClient` with
  a player-centric context + the owner's key/model, and the per-tick action executor
  against the `ServerPlayer`). The possessing bot's own AI stands down while
  `AiAssistantEntity.isPossessing()` (its `possessing` UUID field is transient — never
  saved to NBT). Reuses the same [action vocabulary](#ai--llm-planning) and command
  denylist as the bot.
- **Ops gate.** `allowPossession` (config, default true) gates the whole feature;
  toggle it on the Settings → **Behavior** tab or with `/ai admin possession on|off`.
  Config schema → v7 (upgrading installs default it true).

### Client-side assistant — works on ANY server, even without Blockpal (3.18.0+)
Everything above needs Blockpal on the **server**. This section is the opposite: a
**client-only** layer that runs on the player's own machine, so it works on a vanilla
server, someone else's modded server, or singleplayer. It never depends on the server
having Blockpal. Code lives under `client/assist/` + two GUI screens.

- **Private AI chat box (mini wiki assistant).** `/aichat` (or a tiny **`✦`** button
  injected top-right into the inventory and any container screen — anywhere
  the mouse is free) opens `client/gui/AssistantChatScreen`: a small, scrollable,
  word-wrapped, theme-styled panel to chat with the AI for tips/recipes/strategy. It is
  **private** — replies render only in this box (never in server chat). History is
  persisted client-side by `client/assist/ChatMemory` to `config/blockpal/assistant-chats.json`,
  **capped at 100 messages per conversation and 100 conversations** (oldest roll off);
  `＋`/`◀`/`▶` switch threads, `▲`/`▼` and the wheel scroll. The chat is **advice only** —
  it never controls the player and can't type in server chat.
- **Mini chat panel embedded in the ESC menu & chat screen (3.20.0).**
  `client/gui/MiniChatPanel` injects the assistant chat **directly into the pause menu**
  (right side, clear of the centered button column) and into the **vanilla chat screen**:
  the recent conversation (bottom-anchored, word-wrapped `StringWidget`s), an input box
  (Enter or Send submits) and a **"Full chat & history ⛶"** button that opens the full
  `AssistantChatScreen`. Same `ClientAi`/`ChatMemory` backend — private, advice-only,
  works on any server. Injection rides the proven Fabric `ScreenEvents.AFTER_INIT` +
  `Screens.getWidgets` path (no vanilla screen subclassed); message lines are recreated
  in place on updates while the input box is never rebuilt, so focus/typing survive; a
  typed draft survives screen switches. On GUI scales too narrow for the panel the pause
  menu falls back to the old **`✦`** button (containers always keep `✦`).
- **On-screen tips ("mini wiki").** `client/assist/ScreenWatcher` samples your situation
  (~1×/s, ≥90 s between tips) and, on a notable trigger (low health, starving, on fire,
  drowning, a new dimension), asks the model for one short survival tip and drops it into
  the private assistant chat box (`ChatMemory.addTip`, local-only, never server chat).
  Toggle with `/aitips on|off` or the box's Tips button (`assistantTips` config, default
  on). Purely informational, so it's safe everywhere. (A live chat-HUD flash was dropped:
  this MC version renamed the client chat-HUD accessor and it couldn't be compile-verified
  in this environment, so the box is the single safe surface.)
- **Off-server possession (drive your own character).** On a server **without** Blockpal,
  `/aidrive` opens `client/gui/PossessionDriveScreen` (the "little textbox"); `/aidrive
  <instruction>` / `/aidrive stop` steer it by text. `client/assist/ClientPossession`
  turns instructions into an action plan (via the local key / free AI) and drives the
  player by **simulating your own inputs** (`Options` key mappings + look) — so the server
  sees ordinary player packets and no client movement mod is needed. Deliberately limited
  to **basic survival tasks** (walk, mine, `MINE_AREA`, place, use, collect, jump, sneak);
  disallowed actions are stripped by `sanitize()`.
- **Anti-ban design (the important part).** Client-side automation can break some servers'
  rules even when it isn't a *cheat*, so the mod is conservative:
  - It **never attacks players or mobs** — the attack key is released the instant the
    crosshair is on an entity — so it gives **no PvP/combat advantage**; and it **never
    chats or runs commands** from a plan.
  - `client/assist/ServerGuard` **hard-blocks** driving on known no-automation networks
    (Hypixel & co., substring denylist) *regardless of settings*; **your own world**
    (singleplayer/LAN host) is always allowed; a **Blockpal server** routes you to the
    server-side `/ai possess` instead; any other third-party server is allowed only with
    `allowClientPossession` on **and** an up-front "may break this server's rules" warning.
    It also pauses driving below 30% health. The always-safe chat box + tips work
    everywhere (they touch nothing in the world).
- **Config (client-local, schema v9):** `allowClientPossession` (default true — gates the
  driver; the anti-cheat denylist overrides it) and `assistantTips` (default true). These
  live in the player's own `config/blockpal/config.json` and never sync to the server.

### Voice — talk to your agent, hear it talk back (3.19.0+)
- **Push-to-talk keybind.** Hold **V** (raw GLFW code `voicePushToTalkKey`, default 86;
  rebind with `/aivoice key <code>`; read straight from `GLFW.glfwGetKey` — deliberately
  not a registered KeyMapping so it can't clash — and only while no GUI is open, tracked
  via Fabric `ScreenEvents` because 26.2 renamed the current-screen accessor) to record
  the mic (`client/voice/VoiceCapture`, javax.sound.sampled, 16 kHz mono WAV, 30 s cap).
  Release → transcription (`client/voice/SpeechToText`): **Whisper large-v3-turbo** by
  default (`sttModel`/`sttApiUrl`, HF serverless raw-audio POST with the player's token);
  with no token it falls back to the free voice-capable service (`input_audio` chat part
  on `freeApiUrl`). The **text** rides `VoiceInputPayload` (C→S) — raw audio never
  touches the game server — and lands in `ChatListener.handleAddressed` on the sender's
  **own** bot only (`findOwnedFor`, re-checked server-side), so voice commands are always
  private and can't order anyone else's bot. Quick intents stay no-API.
- **The agent speaks — privately.** Every `broadcastMessage` line also goes to
  `voice/VoiceCoordinator.speak`; the server sends `VoiceSpeakPayload` (S→C) only to the
  owner + players the owner shared with, and the client synthesizes it
  (`client/voice/TextToSpeech`: OpenAI-style audio-out chat request, `modalities
  ["text","audio"]`, format **wav** — the JDK plays WAV natively, no MP3 decoder needed —
  on the free keyless service) and plays it via `client/voice/VoicePlayback`
  (javax.sound queue, strictly one utterance at a time). Per-bot voice id in NBT
  (`VoiceId`, `/ai voice set <id>`); client default `ttsVoice` ("alloy",
  `/aivoice voice <id>`); `/aivoice on|off|stop|test`.
- **Sharing & linking.** `voice/VoiceLinkManager` (in-memory, like parties): `/ai voice
  share|unshare|clear|list <player>` controls who hears your agent. A share in either
  direction links owners into one **conversation group** (connected component).
- **Advanced talking (no interruptions).** `VoiceCoordinator` queues utterances per link
  group and plays one at a time (duration estimated from text length, ~15 chars/s,
  capped), so linked agents take turns and never talk over each other; the client
  playback queue is the second guarantee.
- **Server management.** `allowVoice` (config, default true) gates the whole layer —
  `/ai admin voice on|off` or the Settings → Behavior "Allow agent voice" toggle (rides
  `ConfigData`); disabled = no speech packets and push-to-talk politely refused. Config
  schema → v10 (also: client-local `voiceResponses`, `voicePushToTalkKey`, `sttApiUrl`,
  `sttModel`, `ttsVoice`). Wiki: `wiki/Voice.md`.

### Commands (`/ai …`)
| Command | Effect |
|---------|--------|
| `/ai` / `/ai help` | Show help |
| `/ai summon [name]` | Spawn assistant |
| `/ai dismiss` | Remove assistant |
| `/ai menu` / `/ai config` | Open the settings GUI (admins) |
| `/ai panel` | Open the unified panel (admins → Admin, players → My Settings) |
| `/ai tutorial` | Open the how-to walkthrough |
| `/ai come` | Call to player |
| `/ai follow` | Follow player |
| `/ai stay` | Guard position |
| `/ai stop` | Cancel current task |
| `/ai possess` | Hand your character to your companion (opens the console) |
| `/ai possess <instruction>` / `/ai possess stop` | Steer possession by text / end it |
| `/ai resume` / `/ai enable` | Re-enable after the FPS kill switch tripped |
| `/ai locate` / `/ai where` | Find assistant |
| `/ai name <name>` | Rename |
| `/ai skin <name>` | Change skin (built-in or your own PNG) |
| `/ai personality [<id>]` | List / set how the bot talks & acts |
| `/ai personality custom <text>` | Give it your own (AI-moderated) personality |
| `/ai bots` | List every companion **you** own (mode, place, health, trust count) |
| `/ai trust <player>` / `/ai untrust <player>` | Let / stop another player command this bot |
| `/ai trust list` / `/ai trust clear` | Show / clear this bot's trusted players |
| `/ai voice` | Voice status — hold **V** to talk to YOUR companion |
| `/ai voice share\|unshare\|clear\|list [<player>]` | Who may **hear** your agent (sharing links agents) |
| `/ai voice set <id>` | Give your nearby bot its own TTS voice |
| `/aiskins list\|reload` | (client) list/reload skins in `config/blockpal/skins/` |
| `/aichat` | **(client)** open the private AI chat box (works on any server) |
| `/aidrive [<instruction>\|stop]` | **(client)** off-server possession console / steer / end |
| `/aitips [on\|off]` | **(client)** toggle the private on-screen survival tips |
| `/aivoice [on\|off\|stop\|key <code>\|voice <id>\|test <text>]` | **(client)** hear-agent toggle, push-to-talk rebind, default voice |
| `/ai inventory` / `/ai inv` | Show carried items |
| `/ai mykey <token>\|clear` | Set/clear **your own** API key (any player) |
| `/ai model [<id>]` / `/ai models` | Pick your bot's model / list the allowed models |
| `/ai mymenu` | Personal settings screen (model + your own key) |
| `/ai admin …` | **(ops only)** admin panel — see *Admin menu* below |
| `/ai admin ollama on\|off\|url\|model\|models …` | **(ops)** use custom LOCAL models (Ollama) |
| `/ai admin player2 on\|off\|url …` | **(ops)** easiest AI: Player2 (local app, or online w/ `PLAYER2_KEY`) |
| `/village start\|status\|join <role>\|leave\|surrender\|stop` | **Growth** — an AI village that grows or collapses |
| `/game start growth` | Same as `/village start` |
| `/ai <task>` | Give a natural-language task |

**No more setting commands (3.4.0).** The confusing per-setting commands were
removed — there is **no** `/ai settings`, `/ai token`, `/ai listen`, `/ai active` or
`/ai commands` any more. All configuration now lives in the **in-game panel**
(`/ai menu` / `/ai panel`), which is admin-gated (`adminPermissionLevel`, default
2 = ops); the sneak-click menu is too. Everyday commands (summon, follow, come, stay,
stop, locate, inventory, skin, name) and the personal `/ai mykey` / `/ai model` /
`/ai mymenu` stay open to everyone. Ops on a **vanilla** client can still use the
text-based `/ai admin …` tree (and the `BLOCKPAL_API_TOKEN` env var) to configure.

### Admin menu (ops only)
- `/ai admin menu` opens a built-in **admin panel** GUI (`AdminScreen`,
  server-authoritative); `/ai admin stats` / `list` give the same info as text for
  vanilla clients.
- **Manage all bots globally** — `/ai admin killall` removes every Blockpal entity
  on the server; `/ai admin list` shows each bot's owner, mode, dimension, health
  and position. (Backed by static `AiAssistantEntity.all/countAll/countOwnedBy/killAll`.)
- **Global controls** — `/ai admin disable|enable` toggles the mod-wide kill switch
  for everyone; `/ai admin reload` re-reads config from disk.
- **Text AI config (3.8.0)** — `/ai admin token <key>`, `/ai admin apiurl <url>` and
  `/ai admin model <id>` set the shared key, endpoint and default model from chat, so a
  Bedrock or vanilla admin (no Java GUI) can fully configure the AI. The visual panel
  covers the same fields.
- **Stats** — total bots vs. cap, mod status, per-player bot counts and **live FPS**
  (clients report FPS ~1×/s via `ClientStatsPayload`; the server stores it in
  `PlayerStatsTracker`), plus token/command status.
- **Edit settings in the GUI (3.4.0)** — the admin panel now has in-place controls
  (toggles / level cyclers) for allow-commands, command level, **admin level**,
  max bots, require-own-key and model-choice, so ops change them without commands
  or editing files. (These ride `AdminActionPayload`; setting toggles don't trigger
  a re-sync so the scroll position is kept.)
- **Bot cap** — the panel's "Max bots" cycler or `/ai admin maxbots <0-50>` sets
  `maxBotsPerServer`; `/ai summon` refuses past the cap. 0 = unlimited.
- **Per-player keys & models** — `/ai admin requirekey on|off` makes players bring
  their own API key; `/ai admin keylist add|remove|list <player>` manages the
  exemption whitelist; `/ai admin models add|remove|list <id>` curates the model
  list players may pick from. (See *Per-player API keys & selectable models*.)
- **Possession** — `/ai admin possession on|off` toggles `allowPossession` (the ops
  gate for possession mode). (See *Possession mode*.)
- **Voice** — `/ai admin voice on|off` toggles `allowVoice` (the ops gate for the whole
  agent-voice layer: push-to-talk + spoken replies). (See *Voice*.)
- Who counts as admin is `adminPermissionLevel` (vanilla tiers 0/2/4), changed with
  the Admin panel's admin-level control. Data flows over `AdminSyncPayload` (S→C) and
  `AdminActionPayload` (C→S), re-checked server-side in `AiNetworking`.

### Security & API-key protection
- **Authoritative permission checks** — every state-changing server-bound packet
  (`ConfigUpdatePayload`, `AdminActionPayload`) re-checks the sender's permission via
  `AdminAccess`, so a modified client can't rewrite the token / API URL / command
  tier or run admin actions even by forging a packet or hiding the UI. This closed a
  real privilege-escalation hole where any client could overwrite global config.
  `ConfigRequestPayload` is gated too (3.16.1) — non-admins asking for the Settings
  panel are routed to My Settings instead of a screen whose saves would be refused.
- **Singleplayer owner is always admin (3.16.1)** — `AdminAccess` short-circuits for
  the integrated-server host (`MinecraftServer.isSingleplayerOwner`), so the owner of
  a singleplayer/LAN world can always configure it, cheats on or off. (Before this,
  a cheats-off world owner's settings saves — including the API key — were silently
  rejected and the menu re-synced, wiping what they'd typed.)
- **Saves are never silent (3.16.1)** — `ModConfig.save()` reports success; the
  "Settings saved ✓" message shows the config file's absolute path (third-party
  launchers may not use vanilla's `.minecraft`), failures show a red chat error, and
  the path is logged at mod init (`ModConfig.configPath()`).
- **Token never leaves the server** — config sync to clients already omits the token
  (`ConfigData` sends only `tokenSet`); it's never logged.
- **Token at rest** — stored **obfuscated** in `config.json` (`hfTokenObf`, reversible
  XOR — *obfuscation, not encryption*; a mod jar is decompilable). Legacy plaintext
  tokens migrate to obfuscated on first save.
- **Env-var override** — set `BLOCKPAL_API_TOKEN` (or `-Dblockpal.apiToken`) and the
  token is used but **never written to disk** (`isTokenFromEnv()`) — the strong option.
- **`.gitignore`** hardened to keep secrets/config out of git and to ignore new
  Java source files by default unless they are deliberately force-added after review.

### Inventory & equipment
- **10-slot backpack** plus four armor slots and main hand.
- Auto-collects nearby dropped items while idle/following.
- Auto-equips best weapon and armor found (scored by attribute modifiers);
  re-evaluates every 2 seconds.
- Item consumption: eats food when health < 60%, drinks beneficial potions
  first, refuses and tosses harmful items (spider eyes, poison potions, etc.).

### Task watchdog
- Hard timeout (`maxTaskSeconds`, default 300 s, 0 = unlimited) stops runaway
  plans automatically and reverts the entity to FOLLOWING mode.
- Configurable on the **Developer** tab of the settings panel.

### Emergency FPS kill switch
- A client-side **frame-rate watchdog** ("extreme" watchdog) samples FPS every
  tick; if it stays below a preset-dependent floor (Potato 3, Normal 4, Opus 5)
  for ~3 s straight, it trips a mod-wide kill switch on the server.
- While tripped, the assistant entity stays in the world but does **nothing** —
  no planning, task execution, gear management, or chat analysis.
- All players are notified; re-enable with `/ai resume` (or `/ai enable`) once
  the frame-rate recovers. The watchdog re-arms automatically after recovery.

### Combat & survival
- Six modes: IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING.
- `SurvivalReflexGoal` (top priority) — always scans for threats, retaliates,
  retreats when health < 25% regardless of current mode.
- Path recomputed at most every ~0.5 s (not every tick) to prevent lag.

### Settings & config
- All settings persist in **`config/blockpal/config.json`**
  (auto-migrated from the old flat `config/blockpal.json`).
- The file carries a **`configVersion`** stamp. If it's missing or corrupt it's
  regenerated from defaults; if it's from an older mod version, newly-added
  fields are filled with their intended defaults (rather than Java's
  false/0) via a `migrate()` step, while existing values like `hfToken` are
  preserved. So your API key carries across mod updates, and a deleted file just
  comes back as defaults.
- **Crash-safe saves (3.17.0)** — `ModConfig.save()` serializes fully in memory,
  writes a temp file and atomically moves it over `config.json` (never a
  half-written file), keeps the previous good file as `config.json.prev`, retries
  once on a transient IO failure, and is `synchronized`.
- Full list of settings: `hfToken`/`hfTokenObf`, `hfModel`, `apiUrl`,
  `freeAiFallback`, `freeApiUrl`, `freeModel`,
  `ollamaEnabled`, `ollamaUrl`, `ollamaModel`, `ollamaModels`,
  `player2Enabled`, `player2Url`, `player2OnlineUrl`, `player2Model`, `player2KeyObf`,
  `preferSurvivalActions`, `humanizeActions`,
  `villageTargetPopulation`, `villageStartPopulation`, `maxNewTokens`,
  `temperature`, `debugLogging`, `actionTickDelay`, `followDistance`,
  `guardRadius`, `fleeHealthPercent`, `allowCommands`,
  `commandPermissionLevel`, `adminPermissionLevel`, `maxBotsPerServer`,
  `requireOwnApiKey`, `ownKeyWhitelist`, `playerApiKeysObf`, `allowPlayerModelChoice`,
  `allowedModels`, `playerModels`,
  `chatListening`, `activeMode`, `defaultName`,
  `defaultSkin`, `defaultPersonality`, `allowCustomPersonality`, `allowPossession`,
  `allowVoice`, `voiceResponses`, `voicePushToTalkKey`, `sttApiUrl`, `sttModel`,
  `ttsVoice`, `allowClientPossession`, `assistantTips`,
  `maxTaskSeconds`, `performancePreset`, `sneakToOpenMenu`, `configVersion`.
- **Settings are configured in the panel, not via commands (3.4.0).** The old
  `/ai settings <key> <value>` generic setter (and `/ai token|listen|active|commands`)
  were removed as too confusing. The **Settings** panel (`/ai menu`) covers the
  player/AI/behaviour/combat/developer fields; the **Admin** panel covers the
  server-wide ones (admin level, command level/toggle, max bots, require-own-key,
  model choice). All panel writes are **admin-gated** (`adminPermissionLevel`).
- `adminPermissionLevel` (default 2) decides who may change settings / use the admin
  menu; `maxBotsPerServer` (default 8, 0 = unlimited) caps `/ai summon`. The token is
  persisted obfuscated (`hfTokenObf`) and can be supplied via the `BLOCKPAL_API_TOKEN`
  env var instead (then it's never written to disk). See *Security & API-key protection*.
- **First-run tutorial** — on the first player join after a fresh install
  (`tutorialShown` false), Blockpal greets the player, gives them the **AI Manual**
  item, and opens a paged `TutorialScreen` (also on demand via `/ai tutorial`).
  Upgrading installs are marked seen by `migrate()`. Config schema → v4.
- **AI Manual item** — `blockpal:ai_manual`, given once on first join, not craftable,
  not in any creative tab. Right-clicking sends `OpenManualPayload` to the client,
  which opens `AiManualScreen`: 5 pages covering Quick Start, Commands, Personalities,
  Settings & API key, and Custom Skins.

### In-game settings GUI
- **"Holo-terminal" theme (3.17.0)** — every Blockpal screen shares a dark,
  futuristic look drawn by `client/gui/TechTheme.java` from each screen's
  `extractBackground(...)` (the 26.x render-state pass below all widgets): deep
  space-navy gradient + faint hologram grid backdrop, console plates with a cyan
  edge light and bracketed corners, and neon-cyan (`TechTheme.title/header/accent`)
  headings replacing the old yellow/gold. Layout and vanilla widgets unchanged.
- Opened via `/ai menu` (or `/ai panel`) or — unless disabled — sneak-right-click on
  the assistant. The sneak shortcut can trip accidentally, so it's toggleable
  (`sneakToOpenMenu`, on the Behavior tab); `/ai menu` always works regardless.
- **Unified panel with a shared tab bar (3.4.0)** — every Blockpal screen carries a
  top **panel switcher** (`PanelNav`): **Settings** (admins), **Admin** (ops), and
  **My Settings** (everyone). Switching a tab asks the server for that panel's data
  (`ConfigRequestPayload` / an admin refresh / a no-op `PlayerPrefsPayload`) and the
  matching sync packet opens the right screen, so the three panels feel like one place.
- **Tabbed categories** — the Settings panel is split into **Identity**, **Behavior**,
  **AI & API**, **Combat** and **Developer** sub-tabs, shown one at a time. The
  current tab reads as "pressed" in the pinned tab bar. Each setting has a hover
  **tooltip** explaining it.
- Values are held in a pending draft and `capture()`d at the top of `init()`
  (3.17.1), so edits survive **every** widget rebuild — sub-tab switches, window
  resizes, fullscreen/GUI-scale changes; nothing is lost until you Cancel. A
  typed-but-unsaved API key stays visible in its box (with a "➤ Key typed but
  not saved yet" status line) until Apply/Save sends it — only a *saved* key is
  never echoed back. Switching panels in the top `PanelNav` bar auto-applies
  dirty edits first (a `beforeSwitch` hook, used only by the Settings screen).
- **API key fields are password-masked (3.17.2)** — the token box (here and on
  the personal key field in **My Settings**) shows dots by default and is
  **read-only** until you press **Show key**, which switches it to editable
  plaintext for typing/pasting; toggling Show key off re-masks it (capturing
  what you typed into a plain string field first — `pToken` here, `typedKey` in
  `PlayerSettingsScreen`). This is a mask-then-reveal-to-edit design rather than
  live per-keystroke masking, since this MC version's `EditBox` has no formatter
  hook to substitute display characters without altering the stored value. This
  only ever un-masks in-progress text — an already-saved key is still never
  sent back to a client, so the "token never leaves the server" guarantee is
  unchanged.
- **Save / Apply / Cancel** action bar pinned at the bottom; ESC auto-saves.
- **Scrollable body** — each tab lives in a `ScrollableLayout` (mouse wheel +
  scrollbar) so it fits on any screen size; title, tab bar and action bar stay pinned.
- Changes sync to the server via `ConfigUpdatePayload`.
- **Open skins folder** button (Identity tab, under the skin field) opens
  `config/blockpal/skins/` in the OS file browser for drop-in custom skins.
- **Developer tab** exposes low-level settings (`actionTickDelay`,
  `maxTaskSeconds`, `fleeHealthPercent`) with an inline warning. Documented on
  the **Developer Menu** wiki page (`wiki/Developer-Menu.md`).
- **Performance preset** — cycle button on the Behavior tab:
  **Normal** (default), **Opus** (high-end, full AI), **Potato** (low-end,
  reduced AI activity). Selecting a preset auto-fills temperature, max tokens,
  active analysis toggle, and all developer-tab fields at once (applied instantly
  by the Behavior-tab preset button).

### Command execution
- Can run `/setblock`, `/fill`, `/give`, `/tp`, `/effect`, and similar
  commands (permission level 2 by default = command-block tier).
- Denylist blocks dangerous admin commands (`op`, `ban`, `whitelist`, etc.).
- Toggled from the settings/admin panel (the "Allow commands" control).

### Bedrock / Geyser compatibility (3.8.0+)
- Blockpal is mostly **server-authoritative** (entity, chat, commands, AI planning), so
  **Bedrock Edition** players who connect through a **Geyser** proxy can summon, talk to,
  and task the companion with **no client mod on their device** (Bedrock can't run Fabric
  mods). Admins add **Geyser-Fabric + Floodgate-Fabric** to the server; Blockpal does not
  bundle them.
- **Floodgate is an optional (`suggests`) dependency**, accessed only through reflection
  in `compat/BedrockSupport.java` (gated on `FabricLoader.isModLoaded("floodgate")`), so
  the mod compiles against nothing and loads/runs identically on servers without Geyser.
  `BedrockSupport.isBedrockPlayer(player)` reflectively calls
  `FloodgateApi.getInstance().isFloodgatePlayer(uuid)`, failing safe to `false`.
- **Graceful fallbacks** — the Java-client features (GUI panels, FPS watchdog) can't run
  on Bedrock. The menu commands already guard with `ServerPlayNetworking.canSend`; the
  fallback messages are now Bedrock-aware (`AiCommands.noGuiHint`) and point to the text
  alternative instead of "install the mod on your client".
- **Text-based AI config** so a Bedrock/vanilla admin can configure without the GUI:
  `/ai admin token <key>`, `/ai admin apiurl <url>`, `/ai admin model <id>` (ops-only,
  in the existing `/ai admin` tree). Players still use `/ai mykey` / `/ai model`.
- **Known limitation:** Geyser has no general custom-entity translation, so the custom
  `blockpal:ai_assistant` entity may render incorrectly / invisibly on Bedrock even
  though it's fully functional server-side. Improving rendering (Geyser resource pack /
  player-type representation) is a future phase. Docs: `wiki/Geyser-Bedrock.md`.

### Bedrock Edition Add-On — single-player companion, no server (bedrock/ 1.0.0+)
Everything above is the Java/Fabric mod. This is a **from-scratch recreation for
Minecraft Bedrock Edition** — a behavior pack + resource pack (`.mcaddon`) so Bedrock
players get the companion **in their own single-player worlds**: no server, no Geyser,
no Java Edition. Deliberately **repo-only** (never published to Modrinth); it does not
share code or versioning with the Java mod. Source in `bedrock/`, packaged artifact in
`builds/blockpal-bedrock-<version>.mcaddon`.

- **Companion entity** `blockpal:companion` — player-shaped custom model (classic
  64×64 skin layout), spawn egg, persistent, never despawns; follows (native
  `follow_owner` pathfinding via script-side taming, with a script-walk fallback),
  fights back (`hurt_by_target` + melee), floats, opens doors, calls for help when
  badly hurt, guards (hostile-mob targeting within 16 blocks). Modes
  (follow/stay/guard) are component groups switched by entity events; right-click
  toggles follow/stay; up to 4 companions per player.
- **All chat-driven** (`!ai …` — Bedrock has no client GUI API in stable scripts):
  summon/dismiss/come/follow/stay/guard/stop/where/name/skin/personality/bots/inv/say,
  plus name-addressing (`Ethan, follow me`) and `/scriptevent blockpal:ai <cmd>` as a
  cheats-enabled slash alternative. One-time `!ai help` hint on first join.
- **Offline task planner instead of the LLM** — Bedrock's Script API has **no network
  access in single-player** (`@minecraft/server-net` is BDS-only), so cloud planning
  physically can't run; the planning layer is recreated **on-device**: quick intents +
  a natural-language parser (floors/platforms, walls, towers, bridges, a little house,
  mine areas, dig-down staircases, collect items, guard/kill, wait, jump, say) that
  emits step-by-step plans executed **one block per tick** with a **300 s watchdog**
  (the Java mod's hard-won execution rules). Floors/bridges pave terrain; other builds
  only fill air (never overwrite player builds); mining uses `setblock … destroy` for
  real drops. LLM-over-BDS is a possible future phase.
- **Six personalities** (friendly/cheerful/grumpy/stoic/heroic/shy) recreated as
  response pools driving every line; per-bot, persisted via entity dynamic properties.
  Four built-in **skins** (default/robot/ember/void) as variant component groups,
  switchable live.
- **Ownership** — summoner (or first claimer of a spawn-egg bot) is the owner; only
  the owner commands it (polite personality-voiced refusals otherwise).
- **Stable APIs only** (`@minecraft/server` 1.17.0, min engine 1.21.60) — no
  experimental toggles needed; version-difference shims (isValid method/property,
  `tame(player)`/`tame()`, before/after chat events) fail safe.
- **Tooling** — `bedrock/build.py` zips the packs into the `.mcaddon`;
  `bedrock/tools/gen_assets.py` regenerates skins/icons (pure-stdlib PNG writer). An
  offline Node smoke harness (stubbed `@minecraft/server`) exercised the full
  chat→planner→executor pipeline (20/20 scenarios) — but the packs have **not** been
  run inside a real Bedrock client; that's the standing verification caveat.
- Docs: `bedrock/README.md` + `wiki/Bedrock-Add-On.md` (Home/_Sidebar/Geyser page
  cross-linked).

### One-click self-hosting — "Host with Blockpal" (3.10.0+)
- A **Java-client-only** flow that stands up a Bedrock-capable dedicated server so friends
  (Java **and** Bedrock) can join, without hand-installing anything. Opened from the
  **pause menu** ("Host with Blockpal" button, singleplayer only, bottom-left corner as of
  3.16.1 so it can't overlap the centered vanilla button stack) or **`/aihost`** (client
  command); Bedrock players have no mod, so they can only *join*, never host — matching the
  Bedrock→Java-only cross-play direction.
- **Auto-downloads the latest components from their official sources** (so "latest Geyser"
  is always honoured): the Minecraft server jar (Mojang piston manifest, SHA-1 verified),
  the Fabric server launcher (FabricMC meta), Fabric API (Modrinth), and the latest
  **Geyser-Fabric + Floodgate-Fabric** builds (GeyserMC download API). It also copies the
  running Blockpal jar into the server so the hosted world has the companion too.
  **Downloads are cached (3.16.1)** — `Http.downloadCached` reuses a file whose SHA-1
  still matches outright, and un-checksummed "latest" components for 24 h, so only the
  first host pays the full download.
- **Lunar Client awareness (3.16.1)** — `client/host/LunarDetect.java` heuristically
  detects Lunar (mod ids / bootstrap classes / launch paths, failing safe to false); the
  Host screen then notes that Lunar's own built-in world hosting is simplest for
  Java-only friends. Lunar has **no API** a mod can call to trigger it, so Blockpal can
  only signpost it; Blockpal hosting stays the Bedrock cross-play path.
- **Launches a real dedicated server** as a child process (reusing the game's own JVM via
  `java.home`), captures its console, detects the "Done" ready line, and stops it cleanly
  (`stop` on stdin, force-kill fallback). Everything lives under `<gamedir>/blockpal-host/`.
- **Shows both connect addresses** — Java `ip:25565` and Bedrock `ip:19132` — for **LAN**
  (site-local IP) and **internet** (public IP), with copy buttons.
- **Safety gates baked in:** opt-in, a one-time **Minecraft EULA** accept toggle (no server
  starts until it's on), and a prominent warning that the shown IP is the host's own and
  that internet friends need **port-forwarding** — or the tunnel below.
- **Host your CURRENT world (3.15.0)** — the Host screen offers a **"Host current world"**
  toggle (default ON when opened from a singleplayer world): Start saves + leaves the world
  (`disconnectWithSavingScreen`), waits for the integrated server to close, **copies the
  save** into the server (`server/hosted-copy`, `level-name=hosted-copy`) — as of 3.16.1
  this copy happens **before** the component downloads, so the world is captured moments
  after the save closes; as of **3.20.0** the copy runs on a **parallel worker pool**
  (region files are many independent multi-MB files; sequential copying left the disk
  idle) with **live percent/MB progress** in the status line (`statusOnly`, no log flood)
  for both the copy-in and the sync-back — and hosts it —
  the host rejoins via Direct Connect → `localhost:25565`. When the server stops, the played
  world is **synced back over the singleplayer save** (the pre-host original is kept in
  `blockpal-host/backups/<world>-<timestamp>`) and the server's copy is **deleted**, so
  there's always exactly one true world. Sync-back is refused while that save is open in
  singleplayer (a **"Sync world back"** button runs it later), and a
  `blockpal-host/pending-sync.json` marker makes the offer survive a crash/restart. While a
  host is active (or a sync is pending) the **title screen** gains a "Blockpal Host…"
  re-entry button (after leaving the world there's no pause menu / `/aihost`). File plumbing
  in `client/host/WorldSync.java`; world capture in `AiAssistantClient.captureSourceWorld`.
- **No-port-forward tunnel (3.14.0)** — an optional **playit.gg** tunnel (the one relay that
  carries both Java TCP and Bedrock UDP) so friends can join without the host forwarding
  ports or sharing their IP. `TunnelManager` downloads the official playit agent for the OS
  (latest GitHub release, matched by OS/arch), runs it as a child process, and surfaces its
  one-time **setup link** (`https://playit.gg/…`) plus status/log in the Host screen (a
  "Start tunnel" toggle + "Copy link"). The host visits the link once (free account) to claim
  the tunnel and map the ports; the playit dashboard then shows the public address.
- **Code:** client-only, under `client/host/` — `HostManager` (state machine + threads),
  `ComponentResolver` (official URLs), `Http` (download + SHA-1), `HostConfig`,
  `ServerProcess`, `NetAddresses`, `TunnelManager` (playit agent); UI in
  `client/gui/HostScreen.java`; pause-menu button + `/aihost` wired in `AiAssistantClient`.
- **Caveat:** the download/launch path needs verification on a real machine with internet
  (it can't run inside CI). The reachability constraint is physics, not a bug — a home host
  is behind NAT until a port is forwarded or a tunnel is used.

### Party / invites (3.12.0+)
- A **server-side party system** — the social layer the hosted world and the upcoming
  minigames run on. Open to everyone and entirely command-driven, so **Java and Bedrock**
  players use it identically (no client mod needed). `/party invite <player>` (online),
  `/party accept` / `deny`, `/party leave`, `/party list`, `/party kick <player>` and
  `/party disband`. Inviting auto-creates your party; invites lapse after 2 minutes.
- **One leader, up to `PartyManager.MAX_PARTY` (100) members.** The leader invites/kicks/
  disbands; leaving or disconnecting drops you from the party and **hands off leadership**
  to another member (or evaporates an empty party). State is in-memory (not persisted
  across restart), like most party systems.
- **Code:** `party/Party.java` (group model) and `party/PartyManager.java` (registry +
  invites + leader transfer + disconnect cleanup), driven by `command/PartyCommands.java`
  (`/party`), registered in `AiAssistantMod` along with a `ServerPlayConnectionEvents.
  DISCONNECT` cleanup hook. The minigame modes will start a game on a party.

### Mini-games (3.13.0+)
- **Play game modes with the bot and your party.** `/game start <mode>` starts a game for
  the leader's party (or just you); participants are the online party members **and their
  owned bots**, so the companion really plays. `/game list` shows the modes, `/game stop`
  ends it (leader) or leaves it (member). Server-side, so Java and Bedrock play together.
- **Four modes** (`GameMode`): **Chained** (participants tethered to the leader — a tug back
  past ~12 blocks, a teleport past ~40), **Same Health** (everyone clamped to the group's
  lowest health each tick; any death ends it for all), **One Block** (a regenerating single
  block on a sky platform, skyblock-style), and **Fusion** (Chained + Same Health at once).
- **Code:** `minigame/GameMode.java`, `minigame/GameSession.java`, `minigame/MinigameManager.java`
  (tick / `AFTER_DEATH` / `PlayerBlockBreakEvents.AFTER` hooks, registered via
  `MinigameManager.registerEvents()`), `command/GameCommands.java` (`/game`). Disconnect
  cleanup shares the party hook in `AiAssistantMod`.
- **Honest limits:** games run **in the current world** (One Block builds a sky platform) —
  the "each game is its own resumeable world" vision (custom dimensions/persistence) is a
  future enhancement. The mechanics compile and follow standard server APIs but need
  in-world play-testing and tuning (leash feel, One Block placement, shared-death timing).
- **Growth (3.21.0)** is a fifth `GameMode`, but it's a solo AI-village sim rather than a
  party tether game, so `MinigameManager.start` hands `GROWTH` off to `VillageManager` (see
  below) instead of creating a `GameSession`. Reachable as `/game start growth` **or**
  `/village start`.

### Growth — an AI village that lives or dies on its own (3.21.0+)
- **The pitch (VoxelMind-style):** `/village start` (or `/game start growth`) grows an
  **AI-run village** around where you stand. Villagers are Blockpal bots
  (`AiAssistantEntity`), each spawned with a different **role** (builder, farmer, teacher,
  trader, guard, scholar — `VillageRole`), a different **personality**, and — on a local
  Ollama with a model pool — a different **small model**, so they genuinely "think
  differently". They **build huts, farm, teach and trade**, and **narrate** what they're
  doing (from their own model when one is reachable, from a role script otherwise) so their
  intelligence shows.
- **The sim runs at 2×.** An internal day clock (`simTicks += 2` per server tick,
  `DAY_SIM_TICKS`) turns the workforce into **food, houses, knowledge, morale and defence**
  each day: farmers feed the village, builders raise houses (a real 3×3 hut is placed), teachers/
  scholars raise knowledge (→ efficiency), traders lift morale, guards fend off night **raids**.
  A thriving, fed, housed, hopeful village **births** new settlers; a starving or broken one
  **loses** them. Mob deaths count too (villagers are pruned each tick).
- **You can "be one of them":** `/village join <role>` makes your work count toward that
  role's daily output; `/village leave` steps back; `/village status` shows the state.
- **Win / lose (exactly as designed):** if the village **dies out** (population 0) you
  **win** — you outlasted them. If it grows **as big as ever** (peak ≥ `villageTargetPopulation`,
  default 24) you're offered **`/village surrender`** to concede it its triumph. `/village stop`
  (founder) ends it and dismisses the villagers.
- **Lag-safe & server-authoritative** (works for Java + Bedrock): one villager narrates every
  few seconds (async, at most ~60% hit a model), the day sim runs a couple of times a minute,
  hut building is capped per day, and villagers are **hand-driven** (`setAutonomousMode(false)`)
  so they never run the per-bot survival loop and never storm the API. Each villager's model is
  set with the per-bot `AiAssistantEntity.setAiOverride(ApiAuth)`.
- **Code:** `minigame/village/VillageRole.java`, `minigame/village/VillageGame.java`,
  `minigame/village/VillageManager.java` (tick sim + spawn/build/narrate/win logic, registered
  via `VillageManager.registerEvents()`), `command/VillageCommands.java` (`/village`); wired in
  `AiAssistantMod` with a disconnect hook. **Config:** `villageTargetPopulation` (24),
  `villageStartPopulation` (5).
- **Honest limits:** it runs **in the current world**; the LLM narration and the growth/decline
  tuning (day length, food/morale curves, raid odds) want **in-world play-testing** — Minecraft
  and Ollama can't run in the build environment, so this is compile-verified (via CI) but not
  yet played in-game here.

---

## Changelog

### 3.21.0
- **Custom LOCAL models via Ollama.** New `ollamaEnabled` (+ `ollamaUrl` default
  `http://localhost:11434/v1/chat/completions`, `ollamaModel` default `llama3.2`) lets the
  bot talk to a **local Ollama** (or any keyless OpenAI-compatible local server, e.g. LM
  Studio) with **no key and no internet** — so you can run your own custom local models.
  Resolution priority in `HuggingFaceClient.ApiAuth.resolveFor`: a real API key → **Player2**
  (below) → **Ollama** → the free service. `ApiAuth` grew a `local` flag so a keyless *local*
  endpoint counts as usable (`usable() = hasToken() || free || local`); `ModConfig.aiAvailable()
  /aiAvailableFor()` now include both new providers. Text config:
  `/ai admin ollama on|off|url <url>|model <id>|models add|remove|list <id>`.
- **Player2 (player2.game) — the easiest AI, local OR online.** New `player2Enabled`. With
  **no key** it uses the free local **Player2 app** (keyless, `http://localhost:4315/v1/chat/
  completions`) — install-and-go. With a **`PLAYER2_KEY`** it uses Player2's **online cloud**
  (`https://api.player2.game/v1/chat/completions`, `Authorization: Bearer`) with model
  **`gpt-oss-120b`** by default. The key is read from the **`PLAYER2_KEY` env var /
  `-Dplayer2.key`** (used but **never written to disk** and **never baked into the jar**,
  like `BLOCKPAL_API_TOKEN`); a value set via config is obfuscated at rest. `build.yml` exposes
  the `PLAYER2_KEY` repo secret to the **build/CI environment** only (not the artifact).
  Requests to a Player2 endpoint also send the recommended `player2-game-key: blockpal` header
  (`HuggingFaceClient.addProviderHeaders`). Text config: `/ai admin player2 on|off|url <url>`.
  *(Note: player2.game's `/api/v1/mcp` route is an **MCP** server, a different protocol from the
  OpenAI-style HTTP chat-completions Blockpal speaks — so the online default is Player2's
  chat-completions endpoint, and `/ai admin player2 url <url>` repoints it if needed.)*
- **Survival-first behaviour — "use tables", commands only when needed.** The planner prompt
  was rewritten to **do things by hand** (MOVE_TO/PLACE_BLOCK/BREAK_BLOCK/MINE_AREA/COLLECT_ITEM)
  and to treat **RUN_COMMAND as a last resort** (only when a task truly can't be done by hand).
  It now explicitly uses **work stations** — not just crafting tables: furnace/blast_furnace/
  smoker, smithing_table, anvil, brewing_stand, loom, stonecutter, cartography_table, grindstone,
  enchanting_table — which are added to the planner context's "Interactables" scan
  (`AiTaskManager.isInteresting`). New `preferSurvivalActions` (default true; off appends a note
  re-enabling liberal command use).
- **Human-like delays.** New `humanizeActions` (default true) adds small randomised reaction
  pauses so the bot doesn't act with inhuman speed: a jittered inter-step delay in
  `ExecuteTaskGoal`, a short "reach out" dwell before COLLECT_ITEM completes, and a "notice"
  pause in `CollectItemsGoal` before it beelines to loot. Off = the old snappy/instant behaviour.
- **New mini-game: "Growth" (an AI village that lives or dies on its own).** `/village start`
  (or `/game start growth`) grows an **AI-run village** around you: villagers are Blockpal bots,
  each with a different **role** (builder/farmer/teacher/trader/guard/scholar), a different
  **personality**, and — on local Ollama — a different **small model**, so they "think
  differently". A **2× day clock** turns their roles into food, houses, knowledge, morale and
  defence; the village **builds huts, farms, teaches and trades**, **births** new settlers when
  it thrives and **loses** them when it starves or is raided. Villagers narrate what they're
  doing — from their own model when reachable, from a role script otherwise — so their
  intelligence shows. You can **`/village join <role>`** to be one of them. **Win/lose:** if the
  village **dies out** you win; if it grows **as big as ever** (peak ≥ `villageTargetPopulation`,
  default 24) you may **`/village surrender`**. Code: `minigame/village/` (`VillageRole`,
  `VillageGame`, `VillageManager`) + `command/VillageCommands`; each villager's model comes from
  a new transient per-bot `AiAssistantEntity` AI override (`setAiOverride`), and villagers are
  hand-driven (`setAutonomousMode(false)`) so they never storm the API.
- **Config schema → v11** (`ollama*`, `player2*`, `preferSurvivalActions`, `humanizeActions`,
  `villageTargetPopulation`, `villageStartPopulation`; migrate defaults the two survival-feel
  toggles on, leaves the providers opt-in). New Java files (`VillageRole`, `VillageGame`,
  `VillageManager`, `VillageCommands`) were `git add -f`'d per the `*.java` ignore rule.
- *(Toolchain caveat, same as recent releases: Gradle + the 26.2 deps aren't reachable in this
  environment, so no jar was built — `build.yml` compile-checks the branch push. Ollama isn't
  installed and Minecraft can't run here, so the live in-world play-test of the village game and
  the local-AI paths want a real machine; the code mirrors APIs already proven in this codebase.)*

### Bedrock Add-On 1.0.0 (side release — no Java `mod_version` bump)
- **Recreated Blockpal as a native Bedrock Edition Add-On** so Bedrock players can
  play with an AI companion in a **single-player world** — behavior pack + resource
  pack + Script API JavaScript, packaged as `builds/blockpal-bedrock-1.0.0.mcaddon`.
  Source lives in `bedrock/` (see the new Features section above for the full
  capability list). **Repo-only by request — not published to Modrinth**, which is
  also why `mod_version` (3.19.0) was deliberately left untouched: `release.yml`
  publishes on merge keyed by `mod_version`, and its `modrinth-published/3.19.0`
  marker keeps this merge a no-op for Modrinth.
- **Core recreation:** `blockpal:companion` entity (player model, classic 64×64 skin
  layout, spawn egg, 4 variant skins), follow/stay/guard modes, ownership + claiming,
  six personalities as response pools, 10-slot backpack + item collection, combat
  reflexes with call-for-help, `!ai …` chat commands + `Ethan, …` name addressing +
  `/scriptevent blockpal:ai`, right-click follow/stay toggle.
- **The LLM is replaced by an on-device planner** — Bedrock scripts have no HTTP in
  single-player (`@minecraft/server-net` is dedicated-server-only), so natural-language
  tasks (`build a 5x5 floor of stone`, `build a house`, `mine a 3x3 hole`, `dig down
  10`, `build a bridge 12`, `collect items`, …) are parsed locally into step plans run
  **one block per tick** under a **300 s watchdog** — porting the Java mod's 2.8.x
  performance lessons instead of relearning them. Paving plans replace terrain; walls/
  houses only fill air so player builds are never overwritten (a bug the offline smoke
  harness caught: ground-level floors previously placed 0 blocks on solid terrain).
- **Compatibility engineering:** stable `@minecraft/server` 1.17.0 only (min engine
  1.21.60, no experiments); runtime shims for known API drift (`isValid`
  method↔property, `tame(player)`↔`tame()` with a script-follow fallback,
  `beforeEvents.chatSend`→`afterEvents.chatSend` fallback); all dialogue/claim/skin
  state in entity dynamic properties so it persists with the world.
- **Tooling & verification:** `bedrock/build.py` (packs → `.mcaddon`),
  `bedrock/tools/gen_assets.py` (pure-stdlib PNG generator for the 4 skins + pack
  icons). All pack JSON schema-validated; all JS syntax-checked; a Node harness with a
  stubbed `@minecraft/server` ran the real scripts end-to-end (summon → chat → planner
  → executor; 20/20 checks). **Not yet run in a real Bedrock client** — Minecraft
  can't run in this environment; behavior-JSON/Molang and the live tame/pathfinding
  paths want on-device testing before calling it played-tested.
- Docs: new `wiki/Bedrock-Add-On.md`, `bedrock/README.md`; `wiki/Home.md`,
  `wiki/_Sidebar.md` and `wiki/Geyser-Bedrock.md` cross-linked. README/Modrinth
  description intentionally untouched (nothing Modrinth-facing changed).
### 3.20.0
- **Mini AI chat panel in the ESC menu & chat screen.** Requested as "press esc, a mini
  menu to chat… and when you press the chat menu, it also shows up": new
  `client/gui/MiniChatPanel` embeds the private assistant chat **directly into the pause
  menu** (right side, kept clear of the centered vanilla button column) and the **vanilla
  chat screen** — recent conversation lines, an input box (Enter or Send submits) and a
  "Full chat & history ⛶" button into the full `AssistantChatScreen`. Same
  `ClientAi`/`ChatMemory` backend as the chat box (private, advice-only, any server).
  Implementation stays on APIs proven in this codebase: injection via Fabric
  `ScreenEvents.AFTER_INIT` + `Screens.getWidgets` (the ✦-button path), in-place label
  updates via `setMessage` (the HostScreen pattern), flush-left lines via text-width-sized
  `StringWidget`s (the PossessionConsoleScreen pattern). Message lines are recreated in
  place on replies/tips while the input box is never rebuilt (focus + typing survive);
  drafts survive screen switches; `MiniChatPanel.tick()` (END_CLIENT_TICK) refreshes
  every second to pick up async tips. The Enter-to-send override deliberately carries no
  `@Override` so a mapping drift degrades to "use the Send button" instead of failing the
  build. Pause menus too narrow for the panel (and all containers) keep the ✦ button.
- **Model ids: scrubbed input + real error detail.** For "I have a valid model but it
  kept returning error 400": new `ai/ModelIds` — `clean()` strips wrapping quotes,
  whitespace and zero-width/BOM paste artifacts at **every** model-id entry point
  (`/ai admin model`, `/ai admin models add|remove`, `/ai model`, Settings GUI
  (`ConfigData.applyTo`), `PlayerPrefsPayload` handler, and `ModConfig.normalize()` so a
  broken saved id self-heals on load); `advice()` flags ids naming **quantized download
  bundles** (`…-GGUF`, `-GGML`, `-GPTQ`, `-AWQ`, `-EXL2`, `-MLX`) — hub *file* repos that
  hosted APIs don't serve (the exact trap in `Qwen/Qwen2.5-Coder-3B-Instruct-GGUF`) —
  suggesting the base id, shown at set time (commands + GUI save) as a warning.
  `HuggingFaceClient.friendlyHttpError` now parses the error body's real message
  (OpenAI-style `error.message` / flat `error` / `message` / `detail`, truncated) and
  includes the model id in use on 400/404/422, threading `ApiAuth` through
  `sendWithRetry`/`requestChat`. `ModelIds` unit-tested standalone (clean + advice paths).
- **Hosting: parallel world copy + live progress.** For "hosting takes forever to save
  and copy the world": `WorldSync.copyWorld` now plans the file list first (metadata
  walk), creates directories, then copies files on a **daemon worker pool** (2–6 threads
  by CPU count — a world is mostly independent multi-MB region files, and the old
  one-file-at-a-time loop left the disk mostly idle), returning `CopyStats(files, bytes)`
  and reporting whole-percent ticks via a `CopyProgress` callback; failures cancel the
  pool and surface the first `IOException`. `syncBack` gained the same progress
  plumbing. `HostManager` shows `Copying "world" into the server… N% (X/Y MB)` via new
  `statusOnly()` (status line only — no 200-line log flood), logs a one-line summary
  with duration, and gives sync-back the same treatment. The real `WorldSync` was
  compiled standalone (stubbed `HostPaths`/gson) and round-trip tested: parallel copy
  byte-identical, `session.lock` skipped, empty dirs kept, progress reaches 100%,
  sync-back brings served changes home with the backup intact and the copy deleted.
- Wiki: Client-Assistant (mini panel), Troubleshooting (new "rejected the request
  (400)" / GGUF entry), Per-Player-Keys-and-Models (id cleaning + bundle warning),
  Friend-Sharing (fast copy + progress). Root `CHANGELOG.md` gained a player-facing
  3.20.0 section. No config schema change (still v10).
- **Post-merge compile fix (same version).** The first push carried two 26.2 compile
  errors in `MiniChatPanel` (caught by CI; the PR was merged before the fix landed, so
  a follow-up PR repaired `main`): Fabric's accessor is **`Screens.getFont(screen)`**
  (not `getTextRenderer`), and this MC version's input events carry a
  **`net.minecraft.client.input.KeyEvent`** record (`key()`/`scancode()`/`modifiers()`)
  — `EditBox.keyPressed(int,int,int)` no longer exists, so the Enter-to-send override
  now overrides `keyPressed(KeyEvent)` (with `@Override`, since it's verified).
- **New verification recipe — the 26.2 APIs ARE checkable from this environment.**
  Gradle itself stays unusable (its distribution redirects to a GitHub release download
  the proxy 403s), but this session discovered that `piston-meta`/`piston-data.mojang.com`,
  `libraries.minecraft.net`, `maven.fabricmc.net`, `download.java.net` and
  `repo1.maven.org` are all fetchable. Since 26.x ships **unobfuscated with official
  names** (no mappings step), the whole compile check can be reproduced locally:
  fetch the 26.2 `client.jar` via the piston manifest + all `libraries[]` jars, unpack
  the fabric-api fat jar's `META-INF/jars/*` modules, grab Fabric Loader from the
  FabricMC maven and a JDK 25 tarball from `jdk.java.net` (find the real URL by
  scraping the page — the GA path hashes aren't guessable), then
  `javac -proc:none -cp <all of it> $(find src -name '*.java')`. Both source sets (98
  files) compile cleanly this way, and `javap` against those jars answers any
  signature question (e.g. it confirmed `StringWidget`'s 6-arg constructor +
  `setMessage`, and `ScreenKeyboardEvents` as a Fabric-stable input hook). **Do this
  before pushing any change that touches new MC/Fabric API surface** — it turns the
  old "wait for CI to find out" loop into a local check.
- *(Still wanting a real machine: running the game itself — the panel's rendering and
  click-to-focus on the vanilla chat screen, and big-world host copy timing. No jar was
  built here — javac compiles, but Loom packaging still needs Gradle. New Java files
  (`ModelIds.java`, `MiniChatPanel.java`) were `git add -f`'d per the `.gitignore` rule.)*
- **Post-merge fix (same version) — reliable JSON plans + first real in-game run.** The
  planner (`HuggingFaceClient.requestPlan`) now asks the provider for
  `response_format: {"type":"json_object"}` and parses steps leniently
  (`parseStep`): a missing/lower-case `action`, the alternate `{"RUN_COMMAND": {…}}`
  shape, or one malformed step no longer sinks the whole plan. Cause found by actually
  **running the mod for the first time in this environment** (Gradle 9.5.1 fetched from a
  mirror since the wrapper's GitHub download is egress-blocked; JDK 25 from
  download.java.net; `runClient` headless under Xvfb + llvmpipe): small/free models like
  `meta-llama/Llama-3.1-8B-Instruct` emit invalid JSON often enough that "give me a
  diamond pickaxe"-style tasks failed to parse ~half the time — exactly the models the
  keyless fallback and cheap keys use. With json_object mode + lenient parsing the same
  request succeeds 6/6. Also confirmed live: summon/greet, come/follow/stay, name-address
  chat, and natural-language build ("build a stone tower") all work end-to-end.
  Note: the shipped default `hfModel` (`mistralai/Mistral-7B-Instruct-v0.2`) returns
  *"not supported by any provider"* on a free HF token — a free-tier model such as
  `meta-llama/Llama-3.1-8B-Instruct` works.
- **README: real gameplay GIF.** Replaced the generated promo `media/chat.gif` reference
  with `media/gameplay.gif` — a real capture from the headless run (Ethan walks over on a
  chat command, builds a tower it planned, hands over a diamond pickaxe). `README.md` and
  the mirrored `modrinth/description.md` both point at it; the old `chat.gif` file is left
  in `media/` untouched.

### 3.19.0
- **Agent voice.** The companion can now be *talked to* and *talks back out loud*:
  - **Push-to-talk** — hold **V** (rebindable, `/aivoice key <code>`; raw GLFW polling
    via `GLFW.glfwGetKey`, deliberately not a registered KeyMapping) to record
    the mic (`client/voice/VoiceCapture`, javax.sound.sampled, 16 kHz mono WAV, 30 s
    cap); release to transcribe with **Whisper large-v3-turbo** by default
    (`client/voice/SpeechToText`: HF serverless raw-audio POST with the player's token;
    keyless falls back to the free voice-capable service via an `input_audio` chat
    part). Only the transcribed **text** is sent (`VoiceInputPayload` C→S) and routed to
    the sender's **own** bot via `ChatListener.handleAddressed` (extracted from the
    name-addressed chat path; ownership re-checked server-side with `findOwnedFor`).
    Voice never touches public chat.
  - **Private spoken replies** — `AiAssistantEntity.broadcastMessage` now also feeds
    `voice/VoiceCoordinator.speak`; `VoiceSpeakPayload` (S→C) goes only to the owner +
    players the owner shared with. The client synthesizes WAV via an OpenAI-style
    audio-out chat request on the free keyless endpoint (`client/voice/TextToSpeech`,
    WAV because the JDK plays it natively) and plays it on a strict one-at-a-time queue
    (`client/voice/VoicePlayback`, javax.sound — independent of OpenAL). Per-bot voice
    id (`VoiceId` NBT, `/ai voice set <id>`); client default `ttsVoice`
    (`/aivoice voice <id>`); `/aivoice on|off|stop|test`.
  - **Sharing & linking** — `voice/VoiceLinkManager` (in-memory, parties-style):
    `/ai voice share|unshare|clear|list`. A share in either direction links owners into
    one conversation group (connected component over share edges).
  - **Advanced talking** — `VoiceCoordinator` queues utterances per link group and
    releases one at a time (duration estimated ~15 chars/s, capped ~13 s), so linked
    agents take turns and never interrupt each other; the client queue is the second
    overlap guarantee.
  - **Server management** — new `allowVoice` gate (default true): `/ai admin voice
    on|off` text command + "Allow agent voice" toggle on Settings → Behavior (rides
    `ConfigData`). Push-to-talk is refused with a friendly message while off.
- **Config schema → v10:** `allowVoice`, `voiceResponses`, `voicePushToTalkKey` (86 = V),
  `sttApiUrl`, `sttModel` (`openai/whisper-large-v3-turbo`), `ttsVoice` (`alloy`);
  migrate() defaults them on upgrade.
- Wiki: new `wiki/Voice.md`; Commands/Settings/Home/_Sidebar updated. Root
  `CHANGELOG.md` gained a player-facing 3.19.0 section.
- **Post-release addendum (same version):** the **Modrinth project description** is now
  kept in lockstep with GitHub — new `modrinth/description.md` (mirrors `README.md`,
  no H1 headings since Modrinth rejects them) synced by the new
  `modrinth-description.yml` workflow on merge to `main`; new `media/` folder
  (banner.png, features.png, voice.png, chat.gif — generated holo-terminal-style
  promo graphics) embedded in both the README and the Modrinth page; README refreshed
  (stale 3.10.0 badge → 3.19.0, voice feature/commands/wiki link added).
- **Post-release addendum — live endpoint verification + voice resilience.** Every
  endpoint the AI/voice code calls was exercised live from a networked session:
  HF router chat completions (401 without a token = routed correctly), the Whisper
  STT route (`router.huggingface.co/hf-inference/models/openai/whisper-large-v3-turbo`
  — 401 auth challenge; raw-WAV request + `{"text"}` response confirmed against the
  official Inference Providers docs; **model live on hf-inference** per the hub),
  and the free fallback. Finding: the free service **dropped its audio model**
  (`openai-audio` → 404 "Model not found"; its anonymous catalog is now a single
  text-only model, `openai-fast` alias `openai` — the keyless *text* AI still
  works, verified live with a real completion). Fixes: `SpeechToText` chains
  token-Whisper → free STT (with one 503 warm-up retry) and exposes
  `lastFailure()` so the action bar states the *true* reason (service has no
  speech model / key rejected / network) instead of "couldn't hear you";
  `TextToSpeech` treats 404 as "endpoint has no audio model" — one clear log line
  plus a 10-minute mute instead of a 404 per chat line. Net: voice-in works with
  a (free) HF key today; voice-out resumes when `freeApiUrl` points at an
  audio-capable OpenAI-compatible endpoint (or the free service restores audio).
  Wiki Voice/Troubleshooting updated to match; the Modrinth description's banner
  URL was fixed for the `Banner.png` rename (raw URLs are case-sensitive). Note:
  `.gitignore` now ignores `*.java` (owner's change in PR #66) — **new Java files
  must be `git add -f`'d** or they silently miss commits.
- **First CI run caught three 26.2 mapping renames** (confirmed via the build logs, not
  guessed): `Minecraft.screen`, `Window.getWindow()` and
  `LocalPlayer.displayClientMessage(Component, boolean)` don't exist under this
  version's mappings, and no other file in the codebase uses them to borrow a proven
  name. Per the 3.17.2 lesson (no unverifiable mapping guesses), the fixes avoid the
  renamed accessors entirely: "a GUI is open" is now tracked with Fabric
  `ScreenEvents.AFTER_INIT` + `ScreenEvents.remove` (same stable Fabric class already
  compiled in `AiAssistantClient`); the key state is read via LWJGL `GLFW.glfwGetKey`
  with the window handle found by **one-time reflection** over `Window`'s no-arg `long`
  getters (fails safe: push-to-talk disabled + one log line); the action-bar status uses
  one-time reflection for the player's distinctive `(Component, boolean)` message method
  (fails safe: status skipped — the server-side chat echo still confirms every voice
  command through a proven path).
- *(Toolchain caveat, same as recent releases: Gradle + the 26.2 deps are unreachable in
  this environment, so no jar was built — `build.yml` compile-checks the branch push. The
  live audio path — mic capture, the Whisper/TTS endpoints (the free `openai-audio`
  voice model especially), playback, the GLFW key polling and the two reflective
  lookups — wants real-machine verification; javax.sound and the HTTP plumbing are
  JDK-only and the rest reuses APIs already proven in this codebase.)*

### 3.18.0
- **A client-side assistant that works on ANY server — even ones without Blockpal.**
  Everything before this needed Blockpal on the *server*; this release adds a
  **client-only** layer (`client/assist/`) that runs on the player's own machine:
  - **Private AI chat box.** `/aichat` — or a tiny **`✦`** button injected top-right into
    the pause menu, inventory and any container screen (anywhere the mouse is free) —
    opens `AssistantChatScreen`, a small, scrollable, word-wrapped chat panel. Replies are
    **private** (this box only, never the server chat). History persists to
    `config/blockpal/assistant-chats.json`, **capped at 100 messages/conversation and 100
    conversations**; `＋`/`◀`/`▶` switch threads, `▲`/`▼`/wheel scroll. Advice only — it
    never controls you. New `HuggingFaceClient.requestChat` (plain-prose reply) backs it.
  - **On-screen tips ("mini wiki").** `ScreenWatcher` notices notable situations (low
    health, starving, on fire, drowning, a new dimension) and drops one short survival tip
    into the private assistant chat box (≥90 s apart, local-only). `/aitips on|off`,
    `assistantTips` config (default on). Never controls you.
  - **Off-server possession.** On a non-Blockpal server, `/aidrive` opens
    `PossessionDriveScreen` (the "little textbox"); `/aidrive <instruction>` / `stop` steer
    it by text. `ClientPossession` plans with the local key/free AI and drives you by
    **simulating your own inputs** (`Options` key mappings + look), so no client movement
    mod is needed and the server sees ordinary player packets. Limited to **basic survival
    tasks** (walk/mine/place/use/collect/jump/sneak); other actions are stripped.
- **Anti-ban design.** Client automation can break some servers' rules even when it isn't a
  cheat, so: it **never attacks players/mobs** (attack key released the instant the
  crosshair is on an entity → **no PvP/combat advantage**) and **never chats or runs
  commands**; `ServerGuard` **hard-blocks** driving on known no-automation networks
  (Hypixel & co.) *regardless of settings*, always allows your own singleplayer/LAN world,
  routes Blockpal servers to server-side `/ai possess`, and only drives on other
  third-party servers with `allowClientPossession` on **and** an explicit warning. Driving
  also pauses below 30% health. The chat box + tips are safe everywhere.
- **Config schema → v9.** New client-local `allowClientPossession` + `assistantTips` (both
  default true; migrated on upgrade). They never sync to the server.
- *(Toolchain caveat, same as recent releases: Gradle + the 26.2 deps are unreachable in
  this environment, so no jar was built — `build.yml` compile-checks the branch push. The
  input-driving loop and the wheel-scroll path especially want real-machine play-testing;
  copy the jar into `builds/` when a networked machine builds it. The client-driving code
  leans on stable client APIs — `Options` key mappings, `KeyMapping.setDown`,
  `Minecraft.hitResult` — but they weren't compiled locally here.)*

### 3.17.2
- **API key fields mask like a password box.** Requested after 3.17.1: the token
  field (Settings → AI & API) and the personal key field (My Settings /
  `/ai mymenu`) now show dots (•) instead of plaintext by default, and are
  **read-only** until you press the new **Show key** toggle, which switches the
  box to editable plaintext for typing/pasting. Toggling Show key back off first
  captures what you typed (into `pToken` in `AiConfigScreen`, `typedKey` in
  `PlayerSettingsScreen`) then re-masks the box. `capture()`/`buildData()`/`save()`
  read that captured string, not the box's displayed dots, so nothing about
  what's actually sent to the server changed.
- **First attempt used `EditBox#setFormatter` and failed CI.** The original
  implementation tried a live per-keystroke formatter
  (`BiFunction<String,Integer,FormattedCharSequence>`) so the box could stay
  directly editable while showing dots — a real vanilla Minecraft API in many
  versions, but `./gradlew build` on the pushed branch came back with
  `cannot find symbol: method setFormatter` for this project's MC 26.2 `EditBox`
  (confirmed via the CI logs, not guessed) — this environment has no network
  access to compile against the real classes locally, so the wrong-API guess
  wasn't caught until the actual GitHub Actions run. Replaced with the
  read-only-until-Show design above, built entirely from `EditBox` methods
  already used and proven to compile elsewhere in this same codebase
  (`setValue`, `getValue`, `setEditable`, `setHint`, `setTooltip`), for a fix
  with no unverified API surface.
- **Deliberately scoped to typed text, not the saved key.** The user's ask ("load
  the key next time... with a show button") could be read as "let me reveal my
  already-saved key," which would require the server to send the real secret to
  the client on request — a genuine loosening of the "token never leaves the
  server" guarantee from the Security section above. Asked the user via
  `AskUserQuestion` before implementing; they chose the typed-text-only scope, so
  a saved key still shows blank (with the existing "✔ API key saved" status line)
  when the menu reopens, same as 3.17.1 — only what you actively type gets the
  mask/reveal treatment.

### 3.17.1
- **Fixed the "API key won't save" bug for real.** Root cause found in
  `AiConfigScreen`: the token `EditBox` was the only field rebuilt **empty** on
  every widget rebuild (the saved key is deliberately never echoed back), and
  `capture()` then overwrote the pending draft `pToken` with that empty box —
  so pasting a key and then (a) switching sub-tabs and back, (b) resizing the
  window / toggling fullscreen (`Screen.resize` rebuilds widgets with no
  capture), or (c) clicking a top `PanelNav` tab (screen replaced by the next
  sync, drafts discarded) silently dropped the key before it was ever sent —
  while Save still reported "Settings saved ✓" (blank token = "keep existing",
  and there was nothing to keep). Fixes: `init()` now starts with `capture()`
  (covers every rebuild path incl. resize), the token box is **seeded from the
  draft `pToken`** (only ever typed-but-unsent text, so privacy is unchanged),
  a "**➤ Key typed but not saved yet — press Apply or Save**" status line shows
  while a draft is pending, and `PanelNav.build` gained a `beforeSwitch`
  overload the Settings screen uses to **apply dirty edits before switching
  panels** (other screens unchanged via the old signature).
- **Disk layer verified innocent:** the real `ModConfig` was compiled standalone
  (stubbed `FabricLoader`) and round-trip tested — set → save → restart → load
  keeps the token (obfuscated in `hfTokenObf`; `hfToken` always empty on disk by
  design), token-less saves keep it, corrupt files regenerate with `.bak`. All
  pass; the loss was purely client-side draft handling.
- **Free-AI outage investigated (no code change):** the Pollinations endpoint
  answered planner-style requests correctly from this session (HTTP 200, valid
  JSON, also with the Java `HttpClient` user-agent), so "free AI not working"
  matches the transient 500/429 outage documented during 3.17.0 testing plus
  anonymous-tier rate limits; failed *analysis* calls are silent by design
  (`ChatIntent.none()`), which reads as "Ethan ignores chat" during an outage
  even though named/keyword commands still work with no API at all.
- Wiki: Troubleshooting's key-won't-save entry documents the draft-loss cause,
  the `hfTokenObf`-vs-`hfToken` confusion and the server-vs-client config path;
  "It doesn't react to chat" now separates the no-API quick paths from the
  API-dependent active analysis and its silent-failure behavior.
- *(Toolchain caveat: Gradle distributions and the 26.2 deps are unreachable
  under this environment's network policy, so no jar was built — `build.yml`
  compile-checks the branch push; copy the jar into `builds/` when a networked
  machine builds it.)*

### 3.17.0
- **Free keyless AI fallback.** With no API key resolvable (no shared key, no
  personal key), requests now fall back to a free keyless OpenAI-compatible
  service (default **Pollinations**, `https://text.pollinations.ai/openai`, model
  `openai`) so the companion works out of the box. HuggingFace remains the
  configured default and always takes over the moment a token is set. New config:
  `freeAiFallback` (default true, new "Free AI fallback" toggle on the AI & API
  tab, rides `ConfigData`), `freeApiUrl`, `freeModel`; config schema → v8.
  `ApiAuth` grew `url` + `free` and a central `ApiAuth.resolveFor(owner, name)`;
  gates moved from `hasApiToken()`/`hasToken()` to `ModConfig.aiAvailable()` /
  `aiAvailableFor()` / `ApiAuth.usable()` across the entity, task manager, chat
  listener and possession. Custom-personality moderation also runs over the free
  endpoint. Startup log, tutorial, in-game wiki, Quick-Start/Getting-Started/
  Settings wiki pages updated; stale `/ai settings`/`/ai token` references in
  error messages fixed to the current commands.
- **Crash-safe config saves.** `ModConfig.save()` now serializes the JSON in
  memory, writes a temp file and atomically moves it over `config.json`
  (`ATOMIC_MOVE` with graceful fallback), keeps the previous good file as
  `config.json.prev`, retries once after 150 ms on IO failure (antivirus locks),
  and is `synchronized`. Addresses the "settings sometimes don't save" reports on
  top of 3.16.1's admin-gate fix.
- **Futuristic dark-blue UI theme.** New `client/gui/TechTheme.java` draws a
  shared "holo-terminal" chrome from every screen's `extractBackground(...)`:
  navy gradient + faint grid backdrop, dark console plates with cyan edge light
  and bracketed corners, a bright center rule, and neon-cyan titles/headers
  (`TechTheme.title/header/accent/dim`). Applied to `AiConfigScreen`,
  `AdminScreen`, `PlayerSettingsScreen`, `BotManagerScreen`,
  `PossessionConsoleScreen` (plus a darker console log well), `TutorialScreen`,
  `AiManualScreen` and `HostScreen`; `PanelNav`/tab bars and section headers
  switched from yellow/gold to the cyan accent.
- Verified with a real `runClient` on MC 26.2 in a headless (Xvfb/llvmpipe)
  environment: theme rendering, v8 config migration + atomic save (with
  `config.json.prev`), and the free-AI request path all exercised in-game. A
  direct request to the free endpoint returned a valid completion; during the
  in-game session it was having a transient outage (HTTP 500 "ENOSPC" / 429),
  which is what motivated the 5xx retry and error-truncation polish above.

### 3.16.1
- **Fixed the singleplayer settings/API-key save bug.** `AdminAccess.isAdmin` now
  always passes for the **integrated-server host** (`MinecraftServer.
  isSingleplayerOwner`), so the owner of a singleplayer/LAN world is a Blockpal admin
  even with cheats off. Previously the owner's `ConfigUpdatePayload` was rejected,
  the reject-path re-sync reopened the settings screen, and everything typed
  (including the API key) was wiped — reading as "nothing saves / no config folder".
  `ConfigRequestPayload` (the PanelNav → Settings switch) is now admin-gated too, so
  a non-admin can never land in a Settings screen whose Apply would be refused
  (they're routed to My Settings instead).
- **Save feedback is never silent.** `ModConfig.save()` returns success and logs the
  absolute path on failure; new `ModConfig.configPath()`; the "Settings saved ✓"
  message includes the config file's real path (launchers like Lunar may not use
  vanilla's `.minecraft`) and write failures show a red chat error. The config path
  is also logged at mod init. The AI & API tab shows a "✔ API key saved — hidden
  here for privacy" status line under the (intentionally emptied) token box.
- **Hosting: cached downloads + world copied first.** New `Http.downloadCached`
  (SHA-1 match reused outright; un-checksummed "latest" components reused for 24 h),
  so only the first host pays the ~60 MB download. In copy mode `HostManager.run()`
  now waits for the world to close and copies it **before** the downloads, and the
  world-close poll is 4×/s. The status log notes reused components.
- **Pause-menu "Host with Blockpal" moved to the bottom-left corner** (was centered
  at `scaledHeight - 52`, which overlapped "Save and Quit to Title" at larger GUI
  scales, since the vanilla pause stack is vertically centered).
- **Lunar Client detection.** New `client/host/LunarDetect.java` (mod ids, bootstrap
  classes, launch-path heuristics; fails safe to false). When detected, the Host
  screen shows an aqua note pointing at Lunar's built-in world hosting for Java-only
  friends — Lunar exposes no API to trigger it programmatically, so Blockpal can
  only signpost it; Blockpal hosting remains the Bedrock cross-play path.
- Wiki: Troubleshooting (key-won't-save + where's-my-config entries), Settings
  (singleplayer owner is always admin), Friend-Sharing (button position, download
  cache, Lunar note). Root `CHANGELOG.md` gained a player-facing 3.16.1 section.
  *(Same toolchain caveat as 3.16.0: 26.2 dependencies can't be fetched in this
  environment, so no jar was built.)*

### 3.16.0
- **Possession mode.** New `/ai possess` lets a player hand control of their **own**
  character to their nearby owned companion, which then drives the player's body (move,
  mine, place, use, fight, collect, run commands, chat) from typed instructions. On a
  Java client it opens a **Possession Console** (`client/gui/PossessionConsoleScreen`) —
  a text box with a live status log; on Bedrock/vanilla it's fully text-driven
  (`/ai possess <instruction>` / `/ai possess stop`). You can only ever possess
  *yourself* with a bot you *own*, so there's no cross-player control.
- **Server-authoritative, no client mod required to be controlled.** All planning and
  driving is server-side, so it works on any server running Blockpal and in singleplayer.
  Locomotion pushes the player with server-side velocity + teleport catch-up (the same
  technique `MinigameManager` uses on players); the honest trade-off is feel — it can
  read as a "tug" and wants in-world tuning.
- **Code:** new `possession/PossessionManager.java` (server-tick driving loop, start/
  stop/queue, disconnect cleanup) and `possession/PossessionSession.java` (per-session
  planning + a per-tick action executor against the `ServerPlayer`); new
  `PossessionInputPayload` (C→S) and `PossessionSyncPayload` (S→C) with a
  `AiNetworking.sendPossession` chat fallback; a transient `possessing` flag on
  `AiAssistantEntity` that pauses the bot's own AI while it possesses. New
  `allowPossession` config (ops gate — Behavior tab + `/ai admin possession on|off`);
  config schema → v7. Wiki: new `Possession-Mode.md` (+ sidebar/Home/Commands/Settings).
- *(Compiles against MC 26.2 / Fabric; the toolchain + 26.2 dependencies can't be
  fetched in this environment, so no jar was built — the live driving loop wants
  real-machine play-testing, like the mini-games.)*

### 3.15.0
- **Host your actual world.** "Host with Blockpal" can now host the singleplayer world
  you're playing, not just a fresh one: a **"Host current world"** toggle (default ON when
  opened from a world) saves + leaves the world, **copies the save into the server**, and
  hosts it for Java + Bedrock friends — you rejoin via `localhost:25565`. Stopping the
  server **syncs the changes back** to your singleplayer save (pre-host original kept in
  `blockpal-host/backups/<world>-<timestamp>`) and **deletes the server's copy** — one true
  world, no divergence. If the save is open when the server stops, a **"Sync world back"**
  button defers it safely; a `pending-sync.json` marker survives crashes so the played
  world can't be silently lost. New title-screen "Blockpal Host…" button while hosting
  (re-entry after leaving the world).
- **Code:** new `client/host/WorldSync.java` (copy/backup/sync-back + marker), copy-mode
  pipeline + sync logic in `HostManager`, world toggle + sync button in `HostScreen`,
  world capture + title-screen button in `AiAssistantClient`. Verified against the 26.2
  mappings (`disconnectWithSavingScreen`, `getWorldPath(LevelResource.ROOT)`); the live
  copy/host/sync loop needs real-machine testing (can't run in CI).

### 3.14.0
- **No-port-forward tunnel for hosting.** The "Host with Blockpal" screen gains an optional
  **playit.gg** tunnel (the one relay that carries both Java TCP and Bedrock UDP), so friends
  can join a home host without port-forwarding or the host sharing their IP. New
  `client/host/TunnelManager.java` downloads the official playit agent for the OS, runs it as
  a child process, and surfaces its one-time setup link + status in the Host screen (a "Start
  tunnel" toggle + "Copy link"). Opt-in; the live download/run path needs a real machine to
  verify (can't run in CI). This closes out the multiplayer arc (trust → hosting → party →
  mini-games → tunnel).
- **Code:** `client/host/TunnelManager.java`, `HostPaths` (tunnel dir + binary), and the
  tunnel section in `client/gui/HostScreen.java`.

### 3.13.0
- **Mini-games.** New `/game start <mode>` (with `/game list` and `/game stop`) runs a game
  for your [party](#party--invites-3120), with the bot playing too. Four modes: **Chained**
  (tethered to the leader), **Same Health** (shared health pool; one death ends it for all),
  **One Block** (regenerating single block on a sky platform), and **Fusion** (Chained +
  Same Health). Server-side, so Java and Bedrock players share games.
- **Code:** `minigame/GameMode.java`, `minigame/GameSession.java`, `minigame/MinigameManager.java`
  (server-tick + `AFTER_DEATH` + block-break hooks), `command/GameCommands.java`; wired in
  `AiAssistantMod`.
- Games run in the current world for now (One Block makes a sky platform); the resumeable
  separate-world vision and the no-port-forward tunnel are the remaining follow-ups.
  Mechanics compile and use standard server APIs but want in-world play-testing/tuning.

### 3.12.0
- **Party / invite system.** New server-side `/party` commands so players can team up —
  the social layer for the hosted world and the upcoming minigames. `/party invite
  <player>`, `/party accept` / `deny`, `/party leave`, `/party list`, `/party kick
  <player>`, `/party disband`. One leader, up to 100 members; inviting auto-creates your
  party; invites lapse after 2 minutes; leaving/disconnecting hands off leadership.
  Entirely command-driven and server-side, so **Java and Bedrock** players use it the same.
- **Code:** `party/Party.java`, `party/PartyManager.java`, `command/PartyCommands.java`,
  registered in `AiAssistantMod` with a `ServerPlayConnectionEvents.DISCONNECT` cleanup hook.
- *(Next: the mini-game modes — Chained, Same Health, One Block, Fusion — which start a
  game on a party; then the no-port-forward tunnel.)*

### 3.11.0
- **Visual per-bot manager ("Bots" panel).** `/ai bots` on a Java client now opens a new
  **Bots** panel (a tab in the shared `PanelNav`, alongside Settings/Admin/My Settings)
  instead of only printing text. It lists **every bot on the server with its owner** in a
  scrollable picker; selecting one shows its details and gives buttons to command it
  (come/follow/stay/stop) and — for the owner/admin — manage it (rename, re-skin, change
  personality, dismiss). Built for busy servers with lots of bots, so you can act on a
  specific companion rather than "the nearest one".
- **Server-authoritative + safe.** New `BotListData` (`gather(server, viewer)` with
  per-viewer `canCommand`/`canManage` flags), `BotListRequestPayload` (C→S),
  `BotListSyncPayload` (S→C) and `BotActionPayload` (C→S). The action handler
  (`AiNetworking.applyBotAction`) finds the bot by network id across dimensions and
  **re-checks the sender's permission** before acting, so greyed buttons can't be forged.
  UI in `client/gui/BotManagerScreen.java`; `/ai bots` keeps a text fallback for
  Bedrock/vanilla clients (`AiNetworking.openBotsFor`).
- *(Multiplayer arc continues; the party/invite system, the mini-game modes and the
  no-port-forward tunnel are still the planned follow-ups.)*

### 3.10.0
- **"Host with Blockpal" — one-click self-hosting for cross-play.** A Java-client-only flow
  (pause-menu button in singleplayer, or `/aihost`) that downloads the **latest** Geyser +
  Floodgate (plus the Minecraft server, Fabric server launcher and Fabric API) from their
  official sources, configures and **launches a real dedicated server** as a child process,
  and shows the **Java + Bedrock connect addresses** (LAN and internet) so Bedrock friends
  can join a Java host with no mod on their device. Bedrock can't host (no mod) — only join.
- **Safety:** opt-in, one-time **Minecraft EULA** accept, SHA-1-verified Minecraft download,
  and a clear warning that the shown IP is the host's own and that internet play still needs
  port-forwarding (a no-port-forward **tunnel** option is a planned follow-up).
- **New code:** `client/host/` (`HostManager`, `ComponentResolver`, `Http`, `HostConfig`,
  `ServerProcess`, `NetAddresses`, `HostPaths`), `client/gui/HostScreen.java`, and the
  pause-menu hook + `/aihost` command in `AiAssistantClient`. Everything is written under
  `<gamedir>/blockpal-host/`. Compiles against MC 26.2 / Fabric; the live download+launch
  needs real-machine testing (can't run a server in CI).
- *(Next in the multiplayer arc: the custom party/invite system that seats friends into the
  hosted world, the mini-game modes, and the tunnel option.)*

### 3.9.0
- **Per-bot trust.** Owners can now let other players command a *specific* companion.
  New `/ai trust <player>` (online), `/ai untrust <player>`, `/ai trust list` and
  `/ai trust clear`. Trust is stored per bot in NBT (`Trusted` — a list of
  `TrustEntry(uuid, name)`, new `entity/TrustEntry.java`), so each companion keeps its
  own trusted circle. `AiAssistantEntity.canCommand(player)` = owner or trusted; admins
  always allowed. Trusted players/admins may give **orders** (come/follow/stay/stop,
  locate, inventory, tasks) in chat and via commands; identity edits (name/skin/
  personality), dismiss and trust-editing stay owner-or-admin only.
- **Per-bot visibility.** New `/ai bots` lists every companion you own across all
  dimensions (name, mode, dimension, position, health, personality, trusted count) so
  they're no longer an indistinguishable group; the existing per-bot commands act on
  the one you stand next to. (Foundation for a future per-bot GUI panel.)
- **Authorization hardening.** The `/ai` order/management commands are now gated
  server-side (`ensureCanCommand` / `ensureCanManage`) and the chat owner-gate honours
  trust + admin — closing the gap where any nearby player could `/ai follow` or even
  `/ai dismiss` someone else's bot. New entity helpers `ownedBy` / `findOwnedFor`.
- *(First slice of a larger multiplayer/mini-games effort; the mini-game modes, the
  invite/party system and the settings search box are planned follow-ups.)*

### 3.8.0
- **Geyser/Bedrock compatibility.** Bedrock Edition players can join via a Geyser proxy
  and use the full server-side feature set (summon, chat, tasks, personalities, commands)
  with no Bedrock-side mod. New `compat/BedrockSupport.java` does reflection-only,
  optional Floodgate detection (no compile dependency; gated on `isModLoaded`).
  `fabric.mod.json` declares `floodgate` under `suggests`.
- **Bedrock-aware fallbacks + text config.** `AiCommands.noGuiHint(player, …)` tailors the
  "no GUI" message for Bedrock vs Java. New ops-only text commands `/ai admin token`,
  `/ai admin apiurl`, `/ai admin model` let an admin configure the AI without the Java
  panel (also fixes a stale `/ai settings model` reference in `adminModelsRemove`).
- **Docs.** New `wiki/Geyser-Bedrock.md` (sidebar + `Home.md` updated); README gains a
  "Play from Bedrock" section and the version badge is corrected to the current build.
  Documents the known Geyser custom-entity rendering limitation.

### 3.7.0
- **Quick Start wiki page.** New `wiki/Quick-Start.md` gives new players the shortest path to a working companion — summon, talk, add a key, try a task. Added to the sidebar and linked from `Home.md` and `Getting-Started.md`.
- **AI Manual item.** New `blockpal:ai_manual` item (registered via `ModItems`, renders as a written book). Given once to each player on their first join (`AiAssistantMod.registerFirstRunTutorial`); not craftable and not in any creative tab. Right-clicking opens a paged in-game wiki (`AiManualScreen`, 5 pages: Quick Start, Commands, Personalities, Settings & API key, Custom Skins & More) via the `OpenManualPayload` server→client packet. Item registration lives in `ModItems.java`; item class in `item/AiManualItem.java`; screen in `client/gui/AiManualScreen.java`.
- **Tutorial expanded.** `TutorialScreen` gains two extra pages (Quick Start and a "right-click your AI Manual" closing page), so it now has 5 pages. Every page references the manual for deeper reading.
- **Plumbing.** `OpenManualPayload` added (mirrors `OpenTutorialPayload`); registered in `AiNetworking.registerPayloads()` and `AiNetworking.openManualFor(player)`; client receiver in `AiAssistantClient`. Item model at `assets/blockpal/items/ai_manual.json` (uses `minecraft:item/written_book` appearance). Lang key `item.blockpal.ai_manual`.

### 3.6.0
- **Custom personalities + AI moderation.** Beyond the six built-ins, players can write a
  free-text personality with `/ai personality custom <text>` or the **My Settings** GUI
  (`/ai mymenu`, now with a Personality cycler + custom box). The text is safety-checked
  by the language model (`HuggingFaceClient.moderatePersonality` →
  `Moderation(allowed, reason)`) before it's applied; rejections return a reason and a
  missing API key refuses (can't verify). Stored per-bot in NBT (`CustomPersonality`);
  `entity.getPlanStyle()` feeds the custom text (or the built-in `style()`) to the
  planner, while built-in reply pools still cover the quick no-API lines. Shared flow in
  `AiAssistantEntity.requestCustomPersonality(text, issuer)`.
- **In the settings panel.** Settings → **Identity** tab gained a **Default personality**
  picker (`defaultPersonality`); Settings → **Behavior** tab gained an **Allow custom
  personalities** toggle (`allowCustomPersonality`, the ops limit). Both ride `ConfigData`.
- **Plumbing.** `PlayerPrefsPayload`/`PlayerPrefsSyncPayload` carry the per-bot personality
  (built-in id or custom text + `allowCustom`); the `AiNetworking` handler applies it to
  the player's nearest owned bot (`applyPersonality`). `ModConfig` adds
  `allowCustomPersonality`; config schema → v6 (upgrades default it true).

### 3.5.0
- **Selectable personalities.** New `Personality` enum gives each bot a character that
  drives both its chat voice and the tone of its AI plans: **friendly** (the historical
  default), **cheerful**, **grumpy**, **stoic**, **heroic**, **shy**. Set a nearby bot's
  with `/ai personality <id>` (or list them with `/ai personality`); it's persisted
  per-bot in NBT (`Personality` tag). Each personality supplies the quick no-API
  response pools (come/follow/stay/stop, autonomous hand-off, name acknowledgement,
  gear pick-up / junk lines) and a `style()` fragment appended to the planner system
  prompt (`HuggingFaceClient.requestPlan(..., personaStyle)` → `systemPrompt()`), so
  `CHAT` actions stay in voice without altering the JSON schema or chosen actions.
- **New `defaultPersonality` setting** (default `friendly`) sets the personality of
  freshly summoned bots, used in the summon greeting. Resolution: a bot's stored
  personality wins, else the server default, else `friendly`. Config schema → v5
  (upgrading installs default to `friendly`, so existing worlds sound unchanged).
- Threaded through `ModConfig` (field + normalize/migrate), `AiAssistantEntity`
  (`personality` field, getter/setter, NBT save/load, all `broadcastMessage` response
  sites), `AiTaskManager` (passes `entity.getPersonality().style()`), `ChatListener`
  (name-acknowledgement pool) and `AiCommands` (`/ai personality`, summon greeting,
  `/ai help`). Wiki: new `Personalities.md`, updated `Commands.md`, `Settings.md`,
  `Talking-to-Your-Assistant.md`, `Home.md`, `_Sidebar.md`.

### 3.4.1
- **Consistent, merge-only CI.** Brought the `build.yml` and `wiki.yml` workflows in
  line with the merge-only `release.yml`: `build.yml` dropped its `pull_request`
  trigger (it now runs on pushes to `main` / `claude/**` — a PR's head commit still
  gets a build check via its branch push, with no duplicate PR-open run), and
  `wiki.yml` only republishes on pushes to `main` that touch `wiki/**` (plus the hourly
  backup). Net effect: nothing builds/publishes just because a PR was opened.
- **Docs/wiki brought up to date.** `wiki/Building-From-Source.md` now documents all
  three workflows (and the merge-only release trigger); `wiki/More-Info.md`'s changelog
  highlights were refreshed through 3.4.x; added a *CI / workflows* section here. No
  gameplay/jar changes — this is an infrastructure + documentation release.

### 3.4.0
- **One unified panel with tabs.** Every Blockpal screen now carries a shared top
  **panel switcher** (`PanelNav`) — **Settings** (admins), **Admin** (ops) and
  **My Settings** (everyone) — so the previously separate menus are reachable from
  one place. Switching a tab requests that panel's data from the server
  (`ConfigRequestPayload` / an admin refresh / a no-op `PlayerPrefsPayload`) and the
  reply opens the matching screen. New `/ai panel` entry point opens the right one.
- **Removed the confusing setting commands.** Deleted `/ai settings` (list + the
  generic `<key> <value>` setter), `/ai token`, `/ai listen`, `/ai active` and
  `/ai commands`. Configuration now lives entirely in the panel. Everyday gameplay
  commands and the personal `/ai mykey` / `/ai model` / `/ai mymenu` stay; ops keep a
  text fallback via the `/ai admin …` tree for vanilla clients.
- **More admin options editable in the GUI.** The Admin panel gained in-place
  controls (toggles / 0–4 level cyclers) for allow-commands, command level,
  **admin level**, max bots, require-own-key and player model-choice — so ops change
  them without commands or editing files. New `AdminActionPayload` actions
  (`adminlevel`, `commandlevel`, `allowcommands`, `requirekey`, `modelchoice`); setting
  toggles save silently (no re-sync) so the panel keeps its scroll position.
  `AdminStatsData` now also carries those values + the model/whitelist counts.
- **First-run tutorial.** On the first player join after a fresh install (detected
  via the new persisted `tutorialShown` flag — the `config/blockpal/` folder is
  created by the config loader), Blockpal greets the player and opens a paged
  `TutorialScreen` walkthrough. Reopen any time with `/ai tutorial`. Existing installs
  are marked seen by `migrate()`. Config schema → v4. New `OpenTutorialPayload`.

### 3.3.0
- **Per-player API keys (bring-your-own-key).** New `requireOwnApiKey` (off by
  default): when on, each bot uses *its owner's* personal key so one server owner
  isn't billed for everyone. Players set theirs with `/ai mykey <token>` (or
  privately in `/ai mymenu`), stored obfuscated per-UUID (`playerApiKeysObf`), never
  shown or logged. `ownKeyWhitelist` (`/ai admin keylist add|remove|list <player>`)
  exempts trusted players, who keep using the shared key. Key resolution is per-bot
  from the owner: personal key wins, else shared key unless BYOK is required and
  they aren't whitelisted (then a friendly "set your own key" prompt).
- **Player-selectable models.** `allowedModels` is an admin-curated list (a model
  whitelist; `/ai admin models add|remove|list`) that players pick from with
  `/ai model <id>`, `/ai models`, or the new personal `/ai mymenu` screen
  (`playerModels`, keyed by UUID; falls back to `hfModel`). `allowPlayerModelChoice`
  turns player choice off. The server default model is always kept selectable.
- **New personal menu.** `/ai mymenu` opens a per-player `PlayerSettingsScreen`
  (open to everyone, unlike the admin menu) for choosing a model and setting your
  own key privately. Backed by `PlayerPrefsSyncPayload` (S→C) and `PlayerPrefsPayload`
  (C→S); the prefs packet only ever edits the sending player's own settings.
- **Plumbing.** The API client now takes a resolved `HuggingFaceClient.ApiAuth`
  (token + model) per request instead of reading global config; `AiTaskManager`
  resolves it from the bot's owner. Bots now remember their owner's username
  (`OwnerName` NBT) so whitelist checks work while the owner is offline. New
  settings keys `require_own_key`, `allow_model_choice`. Config schema → v3
  (safe migration defaults: BYOK off, model choice on, a seeded model list).

### 3.2.0
- **Built-in admin menu (ops only).** New `/ai admin …` command tree and an
  `AdminScreen` GUI (`/ai admin menu`) for world owners / operators: **manage all
  bots globally** (`list`, `killall`), global **disable/enable**, `reload`, set the
  **bot cap** (`maxbots`), and **view stats** — total bots vs. cap, mod status,
  per-player bot counts and **live FPS**. Clients report FPS ~1×/s
  (`ClientStatsPayload` → `PlayerStatsTracker`). Text fallbacks (`/ai admin stats`,
  `list`) work on vanilla clients and the console. Data rides `AdminSyncPayload`
  (S→C) and `AdminActionPayload` (C→S); both re-checked server-side. New
  `AdminAccess` helper + static `AiAssistantEntity.all/countAll/countOwnedBy/killAll`.
- **Server-wide bot cap.** New `maxBotsPerServer` (default 8, 0 = unlimited);
  `/ai summon` refuses past it. Owner-controlled via `/ai admin maxbots <0-50>`, the
  menu's −/＋ buttons, or `/ai settings max_bots`.
- **Security — closed a privilege-escalation hole.** Previously *any* client with the
  mod could rewrite global server config (API token, API URL, model, command
  permission tier) by sending `ConfigUpdatePayload`, and toggle the mod-wide kill
  switch. Now every state-changing server-bound packet re-checks the sender's
  permission, and config-writing commands (`/ai menu`, `/ai token`,
  `/ai settings <key> <value>`, `/ai listen|active|commands`, sneak-click menu) are
  **admin-gated**. New `adminPermissionLevel` (default 2 = ops) decides who's an admin
  (`/ai settings admin_level <0-4>`). Everyday commands stay open to everyone.
- **API-key protection.** The HuggingFace token is now stored **obfuscated** at rest
  (`hfTokenObf`; reversible XOR — obfuscation, not encryption) instead of plaintext;
  legacy plaintext tokens migrate automatically. It can instead be supplied via the
  `BLOCKPAL_API_TOKEN` env var / `-Dblockpal.apiToken` property, in which case it is
  used but **never written to disk**. The token is still never sent to clients or logged.
- **`.gitignore` hardened** to keep the runtime config and stray token files out of
  git, and to ignore new Java source files by default unless deliberately reviewed
  and force-added.
- **Config schema → v2.** Added `adminPermissionLevel`, `maxBotsPerServer`,
  `hfTokenObf`; `migrate()` gives upgrading installs safe defaults (admin = ops, an
  8-bot cap) instead of Java's 0 (= everyone admin / unlimited).
- **Docs:** new `wiki/Admin-Menu.md`, `wiki/Security.md` and `wiki/Terms-and-Policy.md`;
  updated `wiki/Commands.md`, `wiki/Settings.md`, `wiki/Home.md`, `wiki/_Sidebar.md`.

### 3.1.0
- **Updated to Minecraft 26.2** (the "All En" update). `minecraft_version` →
  `26.2`, `fabric_api_version` → `0.152.2+26.2`, and `fabric.mod.json` now
  depends on `~26.2`. Loader (`0.19.3`) and Loom (`1.17.11`) unchanged.
- **API fix for 26.2** — `Minecraft.setScreen(...)` was renamed to
  `setScreenAndShow(...)`; updated the one call site in `AiAssistantClient`
  (opening the `/ai menu` settings screen). No other source changes were needed.
- **New `release.yml` workflow** — publishes to Modrinth on every pull request,
  a `v*` tag push, or a manual run. *(Later changed to publish only when a PR is
  **merged** — not on open or close-without-merge; see `release.yml` for the live
  triggers.)* The uploaded jar is renamed to
  `Blockpal-<mod_version>-<minecraft_version>.jar` (e.g. `Blockpal-3.1.0-26.2.jar`),
  published for the **Fabric and Quilt** loaders as a **`beta`** release, with the
  matching `CHANGELOG.md` section used as the Modrinth version description, and the
  project kept in the **`technology`** category (a post-publish API call; needs a
  project-write-scoped token, else it warns).
- **Idempotent publishing** — a version is uploaded at most once. Modrinth does
  *not* enforce unique version numbers, so the workflow guards itself: after a
  successful publish it pushes a `modrinth-published/<version>` git tag, and the
  gate skips if that tag already exists (it also does a best-effort Modrinth API
  check for hand-uploaded versions). Earlier the gate trusted a `curl -sf` query
  whose 404/error was silently read as "not found", so it re-published every run —
  the tag marker fixes that. Requires a `MODRINTH_TOKEN` secret and a
  `MODRINTH_PROJECT_ID` variable; the workflow needs `contents: write` to push the
  marker tag.

### 3.0.0
- **Renamed the whole mod to Blockpal.** This is a full, breaking rename (not just a
  display-name change like 2.14.0):
  - mod id `ai-assistant` → `blockpal` (`fabric.mod.json` `id`, `MOD_ID`, all
    `Identifier` namespaces for the entity, model layer, and network payloads).
  - Java package `com.milkdromeda.aiassistant` → `com.milkdromeda.blockpal` (entrypoint
    classes in `fabric.mod.json` updated to match). Internal class names like
    `AiAssistantEntity`/`AiAssistantMod` were left as-is (not user-facing).
  - Texture namespace `ai-assistant:` → `blockpal:`; asset folder
    `assets/ai-assistant/` → `assets/blockpal/`.
  - Config folder `config/ai-assistant/` → `config/blockpal/` (and legacy flat
    `ai-assistant.json` → `blockpal.json`). Old configs/skins are **not** migrated.
  - `archives_base_name` `ai-assistant` → `blockpal`, so new jars are
    `builds/blockpal-<version>.jar`. Display strings ("Nexus AI" → "Blockpal") updated
    across the GUI, `/ai help`/`/ai settings` headers, lang entries, init log, README
    and the wiki.
  - The GitHub repo (`MilkdromedaStudios/Nexus-Minecraft-AI`) was **not** renamed — only
    the mod. The default companion name stays **Ethan**.

### 2.14.0
- **Rebranded to Nexus AI** — the mod's display name is now **Nexus AI**
  (previously "AI Assistant"). Updated the `fabric.mod.json` name/description,
  the `/ai menu` screen title ("Nexus AI Settings"), the `/ai help` and
  `/ai settings` headers, the entity/item-group lang entries, the skins-folder
  `README.txt` header, the init log line, and the repo `README.md`. The default
  companion name stays **Ethan**. The internal mod id (`ai-assistant`), Java
  package, texture namespace and `config/ai-assistant/` folder are intentionally
  unchanged so existing configs, skins and textures keep working.

### 2.13.0
- **Tabbed settings menu** — the `/ai menu` screen is now split into
  **Identity / Behavior / AI & API / Combat / Developer** tabs, shown one at a
  time with the active tab highlighted in a pinned tab bar. Every setting has a
  hover tooltip. Values are kept in a pending draft and captured on each tab
  switch, so edits survive moving between tabs. (Developer Mode is now its own
  tab instead of a collapsible section.)
- **Sneak-click to open the menu is now toggleable** — new `sneakToOpenMenu`
  setting (default on). When off, sneak-right-clicking the assistant just
  toggles follow/stay; `/ai menu` always opens the menu regardless. Exposed on
  the Behavior tab and via `/ai settings sneak_menu on|off`.
- **One generic settings command** — replaced the per-setting `/ai settings`
  subcommands with a single `/ai settings <key> <value>` (with tab-completion of
  the key) that covers *every* config value, including ones that previously had
  no command (`name`, `skin`, `command_level`, `action_tick_delay`,
  `flee_health`, `chat_listening`, `active_mode`, `allow_commands`,
  `debug_logging`, `sneak_menu`, `preset`). Keeps the command surface small.
- **Versioned config** — `config.json` now carries a `configVersion`; missing or
  corrupt files regenerate from defaults, and files from older mod versions are
  migrated so newly-added settings get their intended default (not Java's
  false/0) while existing values like the API key are preserved.

### 2.12.1
- **"Open skins folder" button** in the `/ai menu` settings screen (under the
  skin field) — opens `config/ai-assistant/skins/` in the OS file browser via
  `Util.getPlatform().openPath(...)`, creating it first if needed, so players
  can drop in PNGs without hunting for the folder by hand.

### 2.12.0
- **Drop-in custom skins** — players can now add their own skins without
  rebuilding the mod: drop a 64×64 PNG into `config/ai-assistant/skins/` and
  apply it with `/ai skin <name>`. Files are loaded into dynamic textures on
  demand (new client-side `RuntimeSkins` loader, with caching and lazy GPU
  upload). The folder is created on first launch with a `README.txt`.
- **New client command `/aiskins`** — `list` shows the skins found in the
  folder, `reload` re-scans and releases cached textures so an edited PNG shows
  up without a full restart.
- **Four new built-in skins** — `slate`, `ember`, `forest`, and `amethyst`
  (themed colour palettes with a simple face), alongside the existing `robot`
  and `void`.
- Skin resolution order in the renderer is now: vanilla `default`/`steve` →
  explicit `namespace:path` → a PNG in the skins folder → a baked-in skin.
- `/ai skin` and `/ai help` now point at the new folder and built-ins.

### 2.11.0
- **Scrollable settings menu** — the `/ai menu` body is now a single scrollable
  column (`ScrollableLayout`, mouse wheel + scrollbar) so it fits on any screen
  size, even with Developer Mode expanded. Title and Save/Apply/Cancel bar stay
  pinned. (The 26.x render-state architecture replaced manual `render()`; the
  engine's `ScrollableLayout` is the supported way to clip/scroll content.)
- **Emergency FPS kill switch** — a client-side frame-rate watchdog auto-disables
  the whole mod when FPS collapses below a preset-dependent floor (Potato 3,
  Normal 4, Opus 5) for ~3 s. The assistant entity stays in the world but does
  nothing until `/ai resume`. New `EmergencyState` flag, `EmergencyDisablePayload`
  packet, `FpsGuardian` client watchdog, and `/ai resume` / `/ai enable` commands.

### 2.10.0
- **Performance presets** — new cycle button in `/ai menu` lets you pick
  **Normal** (default), **Opus** (high-end full AI: faster execution, more
  tokens, longer watchdog), or **Potato** (low-end: slow execution, fewer
  tokens, active analysis disabled). Selecting a preset auto-fills all
  relevant sliders and toggles including the hidden developer-mode fields.
- `ModConfig` and `ConfigData` carry a `performancePreset` field so the
  selected preset persists across sessions.

### 2.9.0
- **Developer Mode GUI** — collapsible section in `/ai menu` exposes three
  advanced settings that can cause lag or crashes: `actionTickDelay` (0–40 ticks),
  `maxTaskSeconds` (0–3600 s), and `fleeHealthPercent` (0–1.0). Each shows an
  inline red warning. Hidden by default; toggle with the **▶ Developer Mode** button.
- Added `developer.md` documenting each setting, its risks, safe ranges, and how
  interactions between them compound danger.
- `ConfigData` network record updated to carry the three developer fields in every
  sync packet so the GUI can read and write them server-side.

### 2.8.2
- **Crash fix** — `MINE_AREA` now breaks one block per tick (queued) instead of
  all 216 blocks at once. Breaking 216 blocks in a single server tick caused
  massive lighting/update cascades that froze the server long enough for
  Minecraft's own watchdog to kill the process.
- `mineQueue` is cleared on step change and goal stop so state never bleeds.

### 2.8.1
- **Performance fix** — rate-limit plan requests to a minimum of 30 s apart;
  hard backstop of 5 s inside `AiTaskManager.requestPlan()` prevents API floods
  even if multiple code paths fire at once.
- Loop tasks throttled from every ~2 s to every 10 s.
- Fixed tight loop: failed API responses (null plan) no longer trigger
  immediate re-requests every tick.

### 2.8.0
- **Always busy** — the assistant is in autonomous/survival mode by default from
  spawn. It immediately starts planning tasks: chop trees → mine → collect items
  → explore, looping forever with no idle gaps.
- **Silent task execution** — no "thinking..." or "on it" messages when starting
  a task; the bot just acts. Chat is reserved for meaningful moments (gear,
  combat, replies to commands).
- **Instant re-plan** — when a task finishes or the watchdog fires, the bot
  immediately picks the next survival task rather than stopping.

### 2.7.0
- **Owner-only obedience** — only the player who spawned the assistant can give
  it orders via chat; others are politely turned away with a varied dismissal.
- **Autonomous mode** — owner can say "do it yourself" (or similar) to hand off
  control; the bot self-directs, picks its own tasks via the LLM, and narrates
  its decisions. Cancelled by "stop", "follow me", or "stay".
- **Randomised responses** — all common replies (follow, come, stay, stop, gear,
  junk, task start/done, combat) now draw from a pool of natural alternatives
  so the bot never sounds like a broken record.

### 2.6.0
- **Expressive chat** — all assistant messages now use natural first-person
  dialogue in `Name: "message"` format instead of `[Name] message`.
- **Calls for help** — when health drops critically low during combat, the
  assistant broadcasts a call for help before retreating.
- Softened and personalised all response strings (follow, stay, come, tasks,
  equipment, junk disposal, errors) to feel more like a character.

### 2.5.0
- **Task watchdog** — tasks that exceed `maxTaskSeconds` (default 5 min) are
  automatically cancelled; assistant reverts to FOLLOWING and notifies players.
- **Folder-based config** — config moved to `config/ai-assistant/config.json`;
  auto-migrates from old location; survives mod updates without data loss.
- Further performance optimizations: cheaper idle threat scans; skip inventory
  upkeep when backpack is empty.

### 2.4.2
- Performance fix: rate-limit passive threat scan so idle entity is cheaper
  (no expensive scan every tick when nothing is nearby).
- Skip full inventory sort/equip pass when backpack is empty.

### 2.4.1
- Performance fix: path navigation recomputed at most every ~0.5 s instead of
  every tick, preventing lag spikes during long tasks.
- Rate-limit active chat analysis to ~once per 3 seconds per assistant.

### 2.4.0
- **Real inventory system** — assistant picks up dropped items, sorts them into
  categories (weapon, armor, tool, food, ore, block, other), auto-equips best
  gear, and stores overflow in a 10-slot backpack.
- **Item consumption** — eats food when hurt, drinks beneficial potions first,
  refuses and discards harmful consumables.

### 2.3.0
- Settings GUI redesigned: compact two-column layout, always-visible action
  bar (Save / Apply / Cancel), ESC auto-saves; settings now actually persist
  across sessions.

### 2.2.0
- Custom skin support (`robot`, `void`, user-supplied PNG files).
- Right-click follow/stay toggle now works in adventure and survival mode.
- Fixed build regression from MC 26.1.2 update.

### 2.1.0
- Initial public release.
- AI companion entity (Ethan) with `/ai summon`, `/ai dismiss`, `/ai <task>`.
- HuggingFace / OpenAI-compatible LLM task planner.
- Proactive chat analysis (active mode); direct name addressing.
- In-game settings GUI; persistent config; 16 action types.
- Six entity modes (IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING).
- Combat reflex goal (always fights back, retreats at low health).

---

## Build artifacts → `builds/` (keep history)

Whenever a jar is built and verified during testing, copy it into the repo's
**`builds/`** folder so it's available without compiling from source.

- **Keep a full history.** Never delete or overwrite older jars on a version
  bump — every released `mod_version` keeps its own jar. New jars are named
  `builds/blockpal-<version>.jar` (the `archives_base_name`); jars from before the
  3.0.0 rename keep their original `builds/ai-assistant-<version>.jar` names — leave
  them. Bump `mod_version` in `gradle.properties` when shipping a new build so the
  new jar lands alongside the old ones instead of replacing them.
- `builds/` is intentionally **not** gitignored (only `build/` is).

## Building

- Standard Fabric + Gradle (Loom) project; use the wrapper (`./gradlew build`).
- Requires **JDK 25**. Loom auto-provisions it via the Foojay resolver in
  `settings.gradle`; locally `~/.gradle/gradle.properties` can point
  `org.gradle.java.installations.paths` at a JDK 25.
- Key versions live in `gradle.properties` (Minecraft, Fabric Loader/API, Loom)
  and `gradle/wrapper/gradle-wrapper.properties` (Gradle itself).
- Verify with a real `./gradlew clean build` before committing a jar.
- **In a network-restricted Claude session** (Gradle's distribution download is
  blocked): the compile check can still be reproduced with plain `javac` — fetch the
  26.2 `client.jar` + `libraries[]` via the piston manifest, the fabric-api fat jar's
  nested modules + Fabric Loader from `maven.fabricmc.net`, and a JDK 25 from
  `jdk.java.net`; 26.x is unobfuscated so no mappings step is needed. See the 3.20.0
  changelog entry for the full recipe, and run it before pushing anything that
  touches new MC/Fabric API surface.

## CI / workflows (all act on *merge*, never on PR-open)

Four GitHub Actions workflows, deliberately consistent — real work happens on a
merge to `main`, not when a PR is opened (so a PR you later close has no side effects):

- **`build.yml`** — compile check. Runs on pushes to `main` and `claude/**` branches
  (a PR's head commit still gets a build check via its branch push) and on merge to
  `main`. No `pull_request` trigger, so there's no duplicate PR-open run.
- **`wiki.yml`** — publishes `wiki/` to the GitHub Wiki on pushes to `main` that touch
  `wiki/**` (i.e. after a merge), with an hourly cron backup sync.
- **`release.yml`** — publishes the jar to Modrinth only when a PR is **merged**
  (`pull_request: types:[closed]` gated by `merged == true`), or on a `v*` tag / manual
  dispatch. Idempotent via the `modrinth-published/<version>` marker tag.
- **`modrinth-description.yml`** — keeps the **Modrinth project page's description**
  in lockstep with the repo: on a push to `main` touching `modrinth/description.md`
  or `media/**` (or manual dispatch) it PATCHes the project `body` from
  `modrinth/description.md`. That file mirrors `README.md` adapted for Modrinth —
  **no H1 headings** (Modrinth rejects them; a lint step enforces this) and absolute
  `raw.githubusercontent.com` URLs for the `media/` images/GIF. Uses the same
  `MODRINTH_TOKEN` secret + `MODRINTH_PROJECT_ID` variable as `release.yml`; the
  token needs project-write scope. **When `README.md` changes, update
  `modrinth/description.md` in the same change** so the two stay in step.

## Layout

```
src/main/java        # common mod: entity, AI planner, commands, chat, networking
src/client/java      # client-only: rendering and the settings GUI
src/main/resources   # fabric.mod.json, lang files, skins, assets
builds/              # tested, ready-to-use jars (full version history, no deleting old builds.)
wiki/                # source for the GitHub Wiki (all user docs live here)
media/               # promo images + GIF used by README.md and the Modrinth description
modrinth/            # description.md — the Modrinth project page body (no H1s!)
```

## Documentation

- The repo `README.md` is intentionally **minimal** — a short overview plus links
  into the GitHub Wiki. All setup/usage/config docs live in `wiki/` and are
  published to the GitHub Wiki automatically by `.github/workflows/wiki.yml`
  **on merge to `main`** (see `wiki/README.md` for the one-time wiki-init step,
  and *CI / workflows* above).
- **When a feature changes, update the matching `wiki/*.md` page** (e.g. new
  command → `wiki/Commands.md`, new setting → `wiki/Settings.md`, dev-tab change
  → `wiki/Developer-Menu.md`) in the same change. Keep `wiki/Home.md` and
  `wiki/_Sidebar.md` in sync if you add or rename a page.

## Testing
- Every time you finish a mod, you will pull the request and test the mod. Private AI API keys can be found in ~/.config/git/ignore. 
