# Getting Started

Once the mod is [installed](Installation), getting going takes about a minute.

## 1. Summon your assistant

In-game, run:

```
/ai summon
```

A companion named **Ethan** spawns next to you with a nametag. Rename it any time
with `/ai name <name>`.

## 2. Add an AI token (one-time)

Quick commands (come, follow, stay…) work out of the box, but **AI-planned tasks need
an API token.** Blockpal uses HuggingFace's router (OpenAI-compatible) by default.

1. Create a free token at <https://huggingface.co/settings/tokens>.
2. In-game, run:
   ```
   /ai token <your_token>
   ```

That's it — your token persists in `config/blockpal/config.json` and survives
mod updates.

## 3. Try it out

| Try typing in chat | What happens |
|--------------------|--------------|
| `follow me` | Follows you *(instant, no token)* |
| `Ethan, build a 5×5 floor` | Sends the task to the AI planner |
| `can you clear these trees?` | Active analysis figures out you want them mined |

See **[Talking to Your Assistant](Talking-to-Your-Assistant)** for the full chat system,
or **[Commands](Commands)** for the slash-command reference.

## Using a different model or provider

Point it at any OpenAI-compatible endpoint (Ollama, LM Studio, OpenAI…):

```
/ai settings api_url http://localhost:11434/v1/chat/completions
/ai settings model <model-id>
```

More in **[Settings](Settings)**.
</content>
