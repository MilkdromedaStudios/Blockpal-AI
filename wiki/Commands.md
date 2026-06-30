# Commands

All commands are under `/ai`. Type `/ai help` in-game for the live list.

| Command | Description |
|---------|-------------|
| `/ai help` | Show the in-game command list |
| `/ai tutorial` | Open the how-to walkthrough |
| `/ai panel` | Open the unified panel (admins → Admin, players → My Settings) |
| `/ai menu` · `/ai config` | Open the settings screen (admins) |
| `/ai summon [name]` | Spawn an assistant (default: **Ethan**) |
| `/ai dismiss` | Remove your assistant |
| `/ai come` | Call it to you |
| `/ai follow` | Have it follow you |
| `/ai stay` | Hold position and keep watch |
| `/ai stop` | Cancel the current task |
| `/ai resume` · `/ai enable` | Re-enable after the FPS kill-switch tripped |
| `/ai locate` · `/ai where` | Distance, direction and coords |
| `/ai name <name>` | Rename it |
| `/ai skin <name>` | Change skin (built-in or custom PNG) — see [Custom Skins](Custom-Skins) |
| `/ai personality [<id>]` | List or set how it talks & acts — see [Personalities](Personalities) |
| `/ai personality custom <text>` | Give it your own AI-moderated personality |
| `/ai bots` | List every companion **you** own — see [Trust & Per-Bot Management](Trust-and-Per-Bot) |
| `/ai trust <player>` · `/ai untrust <player>` | Let / stop another player command this bot |
| `/ai trust list` · `/ai trust clear` | Show / clear this bot's trusted players |
| `/ai inventory` · `/ai inv` | Show carried items |
| `/ai mykey <token>\|clear` | Set/clear **your own** API key — see [Per-Player Keys & Models](Per-Player-Keys-and-Models) |
| `/ai model [<id>]` · `/ai models` | Pick your bot's model / list allowed models |
| `/ai mymenu` | Personal settings screen (your model + your key) |
| `/aiskins list\|reload` | *(client)* List or hot-reload the skins folder |
| `/aihost` · `/aihost status\|stop` | *(client, Java only)* Host a Bedrock-capable server — see [Bedrock (Geyser)](Geyser-Bedrock) |
| `/ai admin …` | *(ops only)* Admin panel — see below |
| `/ai <task>` | Give a natural-language task |

> **No setting commands (3.4.0).** The old `/ai settings`, `/ai token`, `/ai listen`,
> `/ai active` and `/ai commands` were removed — they were too fiddly. **Configure
> everything in the panel** (`/ai panel` / `/ai menu`), which is operator-only
> (`adminPermissionLevel`, default 2 = ops). Everyday commands above stay open to all
> players. Ops on a **vanilla** client can still configure via the text `/ai admin …`
> tree (and the `BLOCKPAL_API_TOKEN` environment variable for the shared key).

## Who can command a bot

By default only the **owner** (the player who summoned it) can give a companion
orders. The owner can **trust** other players with `/ai trust <player>` so they may
command it too, and server admins can always command or moderate any bot. Trusted
players can give orders (come/follow/stay/stop, locate, inventory, tasks) but cannot
rename, re-skin, change the personality of, dismiss, or re-trust someone else's bot —
those stay owner-only. See **[Trust & Per-Bot Management](Trust-and-Per-Bot)**.

## Admin commands (ops only)

The whole `/ai admin` tree is hidden from, and refused to, anyone below the admin
permission level. See **[Admin Menu](Admin-Menu)** for the full guide.

| Command | Description |
|---------|-------------|
| `/ai admin menu` | Open the visual admin panel |
| `/ai admin stats` | Bots, players, FPS, mod status (text) |
| `/ai admin list` | Every bot and where it is (text) |
| `/ai admin killall` | Remove **all** bots on the server |
| `/ai admin maxbots <0-50>` | Cap bots per server (0 = unlimited) |
| `/ai admin disable` · `/ai admin enable` | Turn all bots off / on for everyone |
| `/ai admin reload` | Reload `config.json` from disk |
| `/ai admin requirekey on\|off` | Make players use their own API key — see [Per-Player Keys & Models](Per-Player-Keys-and-Models) |
| `/ai admin keylist add\|remove\|list <player>` | Who may use the shared key when BYOK is on |
| `/ai admin models add\|remove\|list <id>` | Curate the models players may pick |

## Quick intents (no API token)

These common phrases — whether as a command or just typed in chat — are handled
instantly with no LLM call: **come**, **follow**, **stop**, **stay**, **where are you**.

See **[Talking to Your Assistant](Talking-to-Your-Assistant)** for how chat addressing
and active analysis decide what counts as a command.
</content>
