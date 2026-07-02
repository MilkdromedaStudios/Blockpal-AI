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
- Natural-language tasks (`/ai build a 5×5 floor`) are converted to a
  structured JSON action plan (5–15 steps) on a background thread.
- 16 available actions: `MOVE_TO`, `PLACE_BLOCK`, `BREAK_BLOCK`, `MINE_AREA`,
  `USE_BLOCK`, `RUN_COMMAND`, `JUMP`, `SET_SNEAK`, `ATTACK_NEAREST`,
  `FOLLOW_PLAYER`, `LOOK_AT`, `CHAT`, `WAIT`, `COLLECT_ITEM`, `STOP`.
- Looping tasks (patrol, guard, farm) re-plan continuously with fresh context.
- Planning is async — entity stays responsive and fights back during planning.

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
| `/ai resume` / `/ai enable` | Re-enable after the FPS kill switch tripped |
| `/ai locate` / `/ai where` | Find assistant |
| `/ai name <name>` | Rename |
| `/ai skin <name>` | Change skin (built-in or your own PNG) |
| `/ai personality [<id>]` | List / set how the bot talks & acts |
| `/ai personality custom <text>` | Give it your own (AI-moderated) personality |
| `/ai bots` | List every companion **you** own (mode, place, health, trust count) |
| `/ai trust <player>` / `/ai untrust <player>` | Let / stop another player command this bot |
| `/ai trust list` / `/ai trust clear` | Show / clear this bot's trusted players |
| `/aiskins list\|reload` | (client) list/reload skins in `config/blockpal/skins/` |
| `/ai inventory` / `/ai inv` | Show carried items |
| `/ai mykey <token>\|clear` | Set/clear **your own** API key (any player) |
| `/ai model [<id>]` / `/ai models` | Pick your bot's model / list the allowed models |
| `/ai mymenu` | Personal settings screen (model + your own key) |
| `/ai admin …` | **(ops only)** admin panel — see *Admin menu* below |
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
- Who counts as admin is `adminPermissionLevel` (vanilla tiers 0/2/4), changed with
  the Admin panel's admin-level control. Data flows over `AdminSyncPayload` (S→C) and
  `AdminActionPayload` (C→S), re-checked server-side in `AiNetworking`.

### Security & API-key protection
- **Authoritative permission checks** — every state-changing server-bound packet
  (`ConfigUpdatePayload`, `AdminActionPayload`) re-checks the sender's permission via
  `AdminAccess`, so a modified client can't rewrite the token / API URL / command
  tier or run admin actions even by forging a packet or hiding the UI. This closed a
  real privilege-escalation hole where any client could overwrite global config.
- **Token never leaves the server** — config sync to clients already omits the token
  (`ConfigData` sends only `tokenSet`); it's never logged.
- **Token at rest** — stored **obfuscated** in `config.json` (`hfTokenObf`, reversible
  XOR — *obfuscation, not encryption*; a mod jar is decompilable). Legacy plaintext
  tokens migrate to obfuscated on first save.
- **Env-var override** — set `BLOCKPAL_API_TOKEN` (or `-Dblockpal.apiToken`) and the
  token is used but **never written to disk** (`isTokenFromEnv()`) — the strong option.
- **`.gitignore`** hardened to keep secrets/config out of git. (It can't "hide" mod
  source — that lives in the distributed jar — see `wiki/Security.md`.)

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
- Full list of settings: `hfToken`/`hfTokenObf`, `hfModel`, `apiUrl`, `maxNewTokens`,
  `temperature`, `debugLogging`, `actionTickDelay`, `followDistance`,
  `guardRadius`, `fleeHealthPercent`, `allowCommands`,
  `commandPermissionLevel`, `adminPermissionLevel`, `maxBotsPerServer`,
  `requireOwnApiKey`, `ownKeyWhitelist`, `playerApiKeysObf`, `allowPlayerModelChoice`,
  `allowedModels`, `playerModels`,
  `chatListening`, `activeMode`, `defaultName`,
  `defaultSkin`, `defaultPersonality`, `allowCustomPersonality`, `maxTaskSeconds`,
  `performancePreset`, `sneakToOpenMenu`, `configVersion`.
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
- Values are held in a pending draft and `capture()`d on every tab switch, so
  edits survive moving between tabs; nothing is lost until you Cancel.
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

### One-click self-hosting — "Host with Blockpal" (3.10.0+)
- A **Java-client-only** flow that stands up a Bedrock-capable dedicated server so friends
  (Java **and** Bedrock) can join, without hand-installing anything. Opened from the
  **pause menu** ("Host with Blockpal" button, singleplayer only) or **`/aihost`** (client
  command); Bedrock players have no mod, so they can only *join*, never host — matching the
  Bedrock→Java-only cross-play direction.
- **Auto-downloads the latest components from their official sources** (so "latest Geyser"
  is always honoured): the Minecraft server jar (Mojang piston manifest, SHA-1 verified),
  the Fabric server launcher (FabricMC meta), Fabric API (Modrinth), and the latest
  **Geyser-Fabric + Floodgate-Fabric** builds (GeyserMC download API). It also copies the
  running Blockpal jar into the server so the hosted world has the companion too.
- **Launches a real dedicated server** as a child process (reusing the game's own JVM via
  `java.home`), captures its console, detects the "Done" ready line, and stops it cleanly
  (`stop` on stdin, force-kill fallback). Everything lives under `<gamedir>/blockpal-host/`.
- **Shows both connect addresses** — Java `ip:25565` and Bedrock `ip:19132` — for **LAN**
  (site-local IP) and **internet** (public IP), with copy buttons.
- **Safety gates baked in:** opt-in, a one-time **Minecraft EULA** accept toggle (no server
  starts until it's on), and a prominent warning that the shown IP is the host's own and
  that internet friends still need **port-forwarding** (a tunnel option is a planned
  follow-up so people needn't expose their IP / forward ports).
- **Code:** client-only, under `client/host/` — `HostManager` (state machine + threads),
  `ComponentResolver` (official URLs), `Http` (download + SHA-1), `HostConfig`,
  `ServerProcess`, `NetAddresses`; UI in `client/gui/HostScreen.java`; pause-menu button +
  `/aihost` wired in `AiAssistantClient`.
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

---

## Changelog

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
  git, with a note that a mod jar is decompilable so `.gitignore` can't hide source.
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

## CI / workflows (all act on *merge*, never on PR-open)

Three GitHub Actions workflows, deliberately consistent — real work happens on a
merge to `main`, not when a PR is opened (so a PR you later close has no side effects):

- **`build.yml`** — compile check. Runs on pushes to `main` and `claude/**` branches
  (a PR's head commit still gets a build check via its branch push) and on merge to
  `main`. No `pull_request` trigger, so there's no duplicate PR-open run.
- **`wiki.yml`** — publishes `wiki/` to the GitHub Wiki on pushes to `main` that touch
  `wiki/**` (i.e. after a merge), with an hourly cron backup sync.
- **`release.yml`** — publishes the jar to Modrinth only when a PR is **merged**
  (`pull_request: types:[closed]` gated by `merged == true`), or on a `v*` tag / manual
  dispatch. Idempotent via the `modrinth-published/<version>` marker tag.

## Layout

```
src/main/java        # common mod: entity, AI planner, commands, chat, networking
src/client/java      # client-only: rendering and the settings GUI
src/main/resources   # fabric.mod.json, lang files, skins, assets
builds/              # tested, ready-to-use jars (full version history, no deleting old builds.)
wiki/                # source for the GitHub Wiki (all user docs live here)
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
