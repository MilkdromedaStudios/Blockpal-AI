# Troubleshooting

### "Can't connect to the AI service"

Reset the URL to the supported HuggingFace router endpoint:

```
/ai settings api_url https://router.huggingface.co/v1/chat/completions
```

### "My API token is missing or invalid"

```
/ai token <your_huggingface_token>
```

Create a free token at <https://huggingface.co/settings/tokens>.

### "That model wasn't found"

```
/ai settings model <model-id>
```

### It doesn't react to chat

- Check listening is on: `/ai listen on`
- Free-form messages (not starting with a name/keyword) also need active analysis and a
  token: `/ai active on`, then `/ai token <token>`.
- Remember **owner-only obedience** — only the player who summoned it is obeyed.

### Using Ollama / LM Studio / another local model

```
/ai settings api_url http://localhost:11434/v1/chat/completions
/ai settings model <local-model-name>
```

### FPS tanked and the assistant went silent

The [emergency FPS kill-switch](Performance-Presets) tripped. Once framerate recovers,
run `/ai resume` (or `/ai enable`).

### Lag spikes or a server freeze during tasks

You may have lowered a [Developer-tab](Developer-Menu) setting too far. Reset them:

```
/ai settings action_tick_delay 2
/ai settings max_task_seconds 300
```

Or apply the **Potato** [preset](Performance-Presets): `/ai settings preset potato`.

### The settings menu opens when I don't want it to

Sneak-right-click opening the menu can trip accidentally. Turn it off:

```
/ai settings sneak_menu off
```

`/ai menu` always opens the menu regardless.

### My custom skin doesn't show up

- It must be a **64×64** PNG in `config/blockpal/skins/`.
- Apply it by filename without extension: `/ai skin my_skin`.
- After editing the file, run `/aiskins reload`.

Still stuck? [Open an issue](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/issues).
</content>
