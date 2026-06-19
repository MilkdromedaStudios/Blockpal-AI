# Settings

Every option can be changed two ways: the in-game **settings screen** or the
**`/ai settings`** command. Both write to the same `config/blockpal/config.json`.

## Settings screen — `/ai menu`

Open with `/ai menu` (always works) or — unless disabled — sneak-right-click on the
assistant. It's a five-tab screen:

| Tab | What's here |
|-----|-------------|
| **Identity** | Name, skin, **Open skins folder** button |
| **Behavior** | Chat listening, active analysis, sneak-to-open-menu, follow distance, guard radius, [performance preset](Performance-Presets) |
| **AI** | API URL, model, token, temperature, max tokens |
| **Combat** | Allow commands, permission level, flee health |
| **Developer** | Action tick delay, task watchdog timeout, flee health *(high-risk — see [Developer Menu](Developer-Menu))* |

- Each setting has a hover **tooltip** explaining it.
- Edits are held in a draft and captured on each tab switch, so moving between tabs
  doesn't lose changes.
- **Save / Apply / Cancel** bar is pinned at the bottom; **Esc** auto-saves.
- The body scrolls (mouse wheel + scrollbar) to fit any screen size.
- The token field stays blank when one is set — leave it blank to keep the current
  token, or type a new one to replace it.

## Command-line settings

```
/ai settings                            # show all current values
/ai settings model mistralai/Mistral-7B-Instruct-v0.2
/ai settings api_url http://localhost:11434/v1/chat/completions
/ai settings temperature 0.7
/ai settings max_tokens 512
/ai settings follow_distance 4
/ai settings guard_radius 16
/ai settings sneak_menu false           # disable sneak-right-click to open menu
/ai settings preset potato              # apply a performance preset
```

## Full list of keys

`name` · `skin` · `model` · `api_url` · `token` · `temperature` · `max_tokens` ·
`follow_distance` · `guard_radius` · `command_level` · `max_task_seconds` ·
`action_tick_delay` · `flee_health` · `chat_listening` · `active_mode` ·
`allow_commands` · `debug_logging` · `sneak_menu` · `preset`

> `max_task_seconds`, `action_tick_delay` and `flee_health` are the high-risk
> Developer-tab settings — read **[Developer Menu](Developer-Menu)** before changing them.

## Persistence & versioning

Settings live in `config/blockpal/config.json` (auto-migrated from the old flat
`config/blockpal.json`). The file carries a `configVersion` stamp:

- Missing or corrupt → regenerated from defaults.
- From an older mod version → newly-added fields are filled with their intended
  defaults (not Java's false/0), while existing values like your API key are preserved.

So your API key carries across mod updates, and a deleted file just comes back as
defaults.
</content>
