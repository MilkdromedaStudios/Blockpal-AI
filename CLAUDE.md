# AI Assistant — project notes for Claude

A Minecraft **Fabric** mod that adds a friendly AI companion entity (default
name **Ethan**). Tasks are planned by an LLM over an OpenAI-compatible API.

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
- Custom skin support: built-in `default`, `steve`, `robot`, `void`, or any
  local PNG file via `assets/ai-assistant/textures/entity/skins/`.

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
| `/ai locate` / `/ai where` | Find assistant |
| `/ai name <name>` | Rename |
| `/ai skin <name>` | Change skin |
| `/ai token <token>` | Set API token |
| `/ai inventory` / `/ai inv` | Show carried items |
| `/ai listen on\|off` | Toggle chat listening |
| `/ai active on\|off` | Toggle active analysis |
| `/ai commands on\|off` | Allow/block command execution |
| `/ai settings [key] [value]` | View or change any setting |
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

### Combat & survival
- Six modes: IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING.
- `SurvivalReflexGoal` (top priority) — always scans for threats, retaliates,
  retreats when health < 25% regardless of current mode.
- Path recomputed at most every ~0.5 s (not every tick) to prevent lag.

### Settings & config
- All settings persist in **`config/ai-assistant/config.json`**
  (auto-migrated from the old flat `config/ai-assistant.json`).
- Config survives mod updates; new fields are added with defaults without
  wiping existing values.
- Full list of settings: `hfToken`, `hfModel`, `apiUrl`, `maxNewTokens`,
  `temperature`, `debugLogging`, `actionTickDelay`, `followDistance`,
  `guardRadius`, `fleeHealthPercent`, `allowCommands`,
  `commandPermissionLevel`, `chatListening`, `activeMode`, `defaultName`,
  `defaultSkin`, `maxTaskSeconds`.

### In-game settings GUI
- Opened via `/ai menu` or sneak-right-click on the assistant.
- Two-column layout: identity fields on the left, toggles and sliders on the
  right; everything fits on one screen.
- **Save / Apply / Cancel** action bar pinned at the bottom; ESC auto-saves.
- Changes sync to the server via `ConfigUpdatePayload`.

### Command execution
- Can run `/setblock`, `/fill`, `/give`, `/tp`, `/effect`, and similar
  commands (permission level 2 by default = command-block tier).
- Denylist blocks dangerous admin commands (`op`, `ban`, `whitelist`, etc.).
- Toggled per-session with `/ai commands on|off`.

---

## Changelog

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
  bump — every released `mod_version` keeps its own
  `builds/ai-assistant-<version>.jar`. Bump `mod_version` in
  `gradle.properties` when shipping a new build so the new jar lands alongside
  the old ones instead of replacing them.
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
```
