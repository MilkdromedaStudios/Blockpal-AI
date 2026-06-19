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
- **Owner-only** — only the player who spawned the assistant can give it orders;
  other players are politely turned away.
- **Autonomous mode** — owner can say "do it yourself" to hand off control; the
  bot self-directs, picks its own tasks, and narrates its decisions every ~30 s.
  Cancelled by "stop", "follow me", or "stay".

### Commands (`/ai …`)
| Command | Effect |
|---------|--------|
| `/ai` / `/ai help` | Show help |
| `/ai summon [name]` | Spawn assistant |
| `/ai dismiss` | Remove assistant |
| `/ai menu` / `/ai config` | Open settings GUI |
| `/ai come` | Call to player |
| `/ai follow` | Follow player |
| `/ai stay` | Guard position |
| `/ai stop` | Cancel current task |
| `/ai resume` / `/ai enable` | Re-enable after the FPS kill switch tripped |
| `/ai locate` / `/ai where` | Find assistant |
| `/ai name <name>` | Rename |
| `/ai skin <name>` | Change skin (built-in or your own PNG) |
| `/aiskins list\|reload` | (client) list/reload skins in `config/blockpal/skins/` |
| `/ai token <token>` | Set API token |
| `/ai inventory` / `/ai inv` | Show carried items |
| `/ai listen on\|off` | Toggle chat listening |
| `/ai active on\|off` | Toggle active analysis |
| `/ai commands on\|off` | Allow/block command execution |
| `/ai settings` | List all current settings |
| `/ai settings <key> <value>` | Change any one setting (tab-complete the key) |
| `/ai <task>` | Give a natural-language task |

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
- Configurable via `/ai settings max_task_seconds <seconds>`.

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
- Full list of settings: `hfToken`, `hfModel`, `apiUrl`, `maxNewTokens`,
  `temperature`, `debugLogging`, `actionTickDelay`, `followDistance`,
  `guardRadius`, `fleeHealthPercent`, `allowCommands`,
  `commandPermissionLevel`, `chatListening`, `activeMode`, `defaultName`,
  `defaultSkin`, `maxTaskSeconds`, `performancePreset`, `sneakToOpenMenu`,
  `configVersion`.
- **Every setting is also command-configurable**: `/ai settings <key> <value>`
  is a single generic setter (tab-complete the key) covering all of the above,
  so the command surface stays small.

### In-game settings GUI
- Opened via `/ai menu` or — unless disabled — sneak-right-click on the
  assistant. The sneak shortcut can trip accidentally, so it's toggleable
  (`sneakToOpenMenu`, on the Behavior tab or via `/ai settings sneak_menu off`);
  `/ai menu` always works regardless.
- **Tabbed categories** — settings are split into **Identity**, **Behavior**,
  **AI & API**, **Combat** and **Developer** tabs, shown one at a time. The
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
  active analysis toggle, and all developer-tab fields at once (the same logic
  is available from `/ai settings preset <name>`).

### Command execution
- Can run `/setblock`, `/fill`, `/give`, `/tp`, `/effect`, and similar
  commands (permission level 2 by default = command-block tier).
- Denylist blocks dangerous admin commands (`op`, `ban`, `whitelist`, etc.).
- Toggled per-session with `/ai commands on|off`.

---

## Changelog

### 3.1.0
- **Updated to Minecraft 26.2** (the "All En" update). `minecraft_version` →
  `26.2`, `fabric_api_version` → `0.152.2+26.2`, and `fabric.mod.json` now
  depends on `~26.2`. Loader (`0.19.3`) and Loom (`1.17.11`) unchanged.
- **API fix for 26.2** — `Minecraft.setScreen(...)` was renamed to
  `setScreenAndShow(...)`; updated the one call site in `AiAssistantClient`
  (opening the `/ai menu` settings screen). No other source changes were needed.
- **New `release.yml` workflow** — publishes to Modrinth on a `v*` tag push (or
  manual run). The uploaded jar is renamed to
  `Blockpal-<mod_version>-<minecraft_version>.jar` (e.g. `Blockpal-3.1.0-26.2.jar`).
  Requires a `MODRINTH_TOKEN` secret and a `MODRINTH_PROJECT_ID` variable.

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
  (see `wiki/README.md` for the one-time wiki-init step).
- **When a feature changes, update the matching `wiki/*.md` page** (e.g. new
  command → `wiki/Commands.md`, new setting → `wiki/Settings.md`, dev-tab change
  → `wiki/Developer-Menu.md`) in the same change. Keep `wiki/Home.md` and
  `wiki/_Sidebar.md` in sync if you add or rename a page.
