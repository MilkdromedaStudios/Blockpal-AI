# Settings

All of Blockpal's options are changed in the **in-game panel**. As of **3.4.0**
there are no per-setting commands (`/ai settings`, `/ai token`, `/ai listen`,
`/ai active`, `/ai commands` were removed) — everything lives in the panel, which
writes to `config/blockpal/config.json`.

> **Operator-only.** Server-wide settings are admin-gated (`adminPermissionLevel`,
> default 2 = ops). Players can always change *their own* model and key (see
> [Per-Player Keys & Models](Per-Player-Keys-and-Models)). See also [Security](Security).

## Opening the panel

`/ai panel` (or `/ai menu`), or — unless disabled — sneak-right-click your assistant.
Every Blockpal screen has a shared **tab bar** at the top to move between the panels:

| Tab | Who | What's there |
|-----|-----|--------------|
| **Settings** | admins | The five sub-tabs below (names, AI, behaviour, combat, developer) |
| **Admin** | ops | Server controls + stats — see [Admin Menu](Admin-Menu) |
| **My Settings** | everyone | Your own model and API key |

### Settings sub-tabs

| Sub-tab | What's here |
|-----|-------------|
| **Identity** | Name, skin, **Open skins folder** button, **Default personality** |
| **Behavior** | Chat listening, active analysis, sneak-to-open-menu, **allow custom personalities**, follow distance, guard radius, [performance preset](Performance-Presets) |
| **AI** | API URL, model, token, temperature, max tokens |
| **Combat** | Allow commands, permission level, flee health |
| **Developer** | Action tick delay, task watchdog timeout, flee health *(high-risk — see [Developer Menu](Developer-Menu))* |

- Each setting has a hover **tooltip** explaining it.
- Edits are held in a draft and captured on each tab switch, so moving between tabs
  doesn't lose changes.

> **Personality:** the **Identity** tab sets the *default* personality for newly
> summoned bots, and the **Behavior** tab has an *Allow custom personalities* toggle.
> Each player changes their *own* bot's personality with `/ai personality <id>` /
> `/ai personality custom <text>` or the **My Settings** screen (`/ai mymenu`). See
> [Personalities](Personalities).
- **Save / Apply / Cancel** bar is pinned at the bottom; **Esc** auto-saves.
- The token field stays blank when one is set — leave it blank to keep the current
  token, or type a new one to replace it.

## Admin options (in the Admin panel)

Ops change these right in the **Admin** tab — click a toggle or a level cycler, no
commands needed:

| Option | Meaning |
|--------|---------|
| Allow commands | Let bots run `/setblock`, `/fill`, `/give`, etc. |
| Command perm level | Permission tier (0–4) for those commands (2 = command-block) |
| Admin level | Op tier (0–4) needed to change settings / use the admin panel. Default **2** |
| Max bots | Most bots allowed on the server at once (0 = unlimited). Default **8** |
| Require own API key | Players must bring their own key (except the whitelist) |
| Players may pick model | Allow players to choose their bot's model |

The two lists — the **allowed models** and the **own-key whitelist** — are managed
with `/ai admin models …` and `/ai admin keylist …` (see
[Per-Player Keys & Models](Per-Player-Keys-and-Models)).

## API token security

The token is **never** shown back, never logged, and stored **obfuscated** in
`config.json` (`hfTokenObf`) rather than as plain text. For the strongest protection,
set it via the `BLOCKPAL_API_TOKEN` environment variable instead — then it's used but
never written to disk. On a vanilla-client server with no GUI, this env var (or
hand-editing `config.json`) is how you set the shared key. Full details in
**[Security](Security)**.

## Persistence & versioning

Settings live in `config/blockpal/config.json`. The file carries a `configVersion`
stamp:

- Missing or corrupt → regenerated from defaults (and a fresh install kicks off the
  first-run [tutorial](Getting-Started)).
- From an older mod version → newly-added fields are filled with their intended
  defaults (not Java's false/0), while existing values like your API key are preserved.

So your API key carries across mod updates, and a deleted file just comes back as
defaults.
