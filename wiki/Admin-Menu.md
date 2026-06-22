# Admin Menu

Blockpal has a built-in **admin panel** for world owners and server operators. It
lets you manage **every** bot on the server at once, control the mod globally, set a
bot limit, and see live stats (bots, players, FPS).

> **Who can use it?** Anyone at the **admin permission level** — `adminPermissionLevel`,
> default **2** (ops). On a normal server, operators qualify; in single-player, the
> world owner with cheats on qualifies. Change it right in the panel (the **Admin
> level** control), or it falls back to 2. Non-admins can't see or run any `/ai admin`
> command.

## Opening it

The panel is one of three tabs (**Settings · Admin · My Settings**) shared across
every Blockpal screen, so you can reach it from any of them.

| How | Notes |
|-----|-------|
| `/ai panel` | Opens the unified panel (admins land on the Admin tab) |
| `/ai admin menu` | Opens the visual panel directly (needs the mod on your client) |
| `/ai admin stats` | Text summary — works on any client and from the server console |
| `/ai admin list` | Text list of every bot and where it is |

## Change settings here (3.4.0)

The Admin tab now has **in-place controls** so you configure the server without any
commands or file-editing — click a toggle, or cycle a 0–4 level:

- **Allow commands**, **Command perm level**, **Admin level**, **Max bots**,
  **Require own API key**, **Players may pick model**.

These save instantly and silently (the panel keeps your scroll position). The two
lists — allowed models and the own-key whitelist — are still managed with
`/ai admin models …` and `/ai admin keylist …`.

## What it shows

- **Bots** — how many Blockpal entities exist vs. the cap.
- **Mod status** — active, or **DISABLED** (the emergency kill switch).
- **Allow commands** — whether bots may run game commands, and at what level.
- **Admin level** — the current `adminPermissionLevel`.
- **API token** — whether one is set (and whether it came from an environment
  variable). The token value itself is **never** shown.
- **Players online** — each player's bot count and **FPS** (clients report their own
  frame-rate about once a second; `?` means a player without the mod or not reported yet).
- **Bots** — one row per bot: name, owner, mode, dimension, health and position.

## What the buttons / commands do

| Button | Command | Effect |
|--------|---------|--------|
| **Kill all bots** | `/ai admin killall` | Removes every Blockpal entity on the server |
| **Disable bots** / **Enable bots** | `/ai admin disable` · `/ai admin enable` | Flips the mod-wide kill switch for everyone (same switch as the FPS guardian / `/ai resume`) |
| **Max bots** (cycler) | `/ai admin maxbots <0-50>` | Sets the server-wide bot cap (`0` = unlimited) |
| **Refresh** | — | Re-reads the current stats |
| — | `/ai admin reload` | Reloads `config.json` from disk |

The visual panel is just a front end: every button asks the server to do the work,
and the server **re-checks your permission** before acting and then sends back fresh
stats. A modified client can't bypass that check.

## Bot limit (anti-grief / anti-lag)

`maxBotsPerServer` (default **8**) caps how many companions can exist at once. When
the server is at the cap, `/ai summon` refuses with a message pointing players at an
admin. Raise/lower it with the panel's **Max bots** cycler or `/ai admin maxbots <n>`.
Set it to `0` for no limit.

## See also

- **[Security](Security)** — permission gating, API-key protection, and what
  `.gitignore` can and can't do.
- **[Settings](Settings)** — every config key, including `admin_level` and `max_bots`.
- **[Terms & Policy](Terms-and-Policy)** — the no-cheating / fair-use policy.
