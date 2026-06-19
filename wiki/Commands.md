# Commands

All commands are under `/ai`. Type `/ai help` in-game for the live list.

| Command | Description |
|---------|-------------|
| `/ai help` | Show the in-game command list |
| `/ai menu` · `/ai config` | Open the settings screen |
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
| `/ai token <token>` | Set API token |
| `/ai listen on\|off` | Toggle chat listening |
| `/ai active on\|off` | Toggle proactive AI analysis |
| `/ai commands on\|off` | Allow/block command execution — see [Running Commands](Running-Commands) |
| `/ai inventory` · `/ai inv` | Show carried items |
| `/ai settings` | List all current settings |
| `/ai settings <key> <value>` | Change any one setting (tab-complete the key) — see [Settings](Settings) |
| `/aiskins list\|reload` | *(client)* List or hot-reload the skins folder |
| `/ai <task>` | Give a natural-language task |

## Quick intents (no API token)

These common phrases — whether as a command or just typed in chat — are handled
instantly with no LLM call: **come**, **follow**, **stop**, **stay**, **where are you**.

See **[Talking to Your Assistant](Talking-to-Your-Assistant)** for how chat addressing
and active analysis decide what counts as a command.
</content>
