# Local & easy AI — Ollama and Player2

Blockpal works out of the box with a free built-in AI, and with a HuggingFace (or
any OpenAI-compatible) key. As of **3.21.0** there are two more ways to power your
companion that need **no HuggingFace key** — great for running your own models, or
for the absolute easiest setup.

**Priority order** when picking what to talk to:

1. a personal or shared **API key** (always wins);
2. **Player2**, if enabled;
3. **Ollama**, if enabled;
4. the free keyless service.

## Ollama — run your own custom local models

[Ollama](https://ollama.com) runs language models on your own machine and exposes
an OpenAI-compatible endpoint. Point Blockpal at it and you can run **any model you
have pulled**, with no key and no internet.

```
# once, on the machine running the server:
ollama pull llama3.2

# in-game (ops):
/ai admin ollama on
/ai admin ollama model llama3.2
/ai admin ollama            # show status
```

- Default endpoint: `http://localhost:11434/v1/chat/completions`
  (change with `/ai admin ollama url <url>` — e.g. LM Studio, or a remote box).
- **Model pool for the [Growth village game](Growth-Village):** add several models
  with `/ai admin ollama models add <id>` and each villager is handed a different
  one, so they "think differently".

Any keyless local OpenAI-compatible server (LM Studio, llama.cpp's server, …) works
the same way — just set the URL.

## Player2 — the easiest AI (local **or** online)

[Player2](https://player2.game) is an OpenAI-compatible AI made for games. It works
two ways:

- **Local (keyless):** install the free **Player2 app** and it serves AI on
  `localhost:4315` — no key, no model download.
- **Online (cloud):** set a **`PLAYER2_KEY`** and Blockpal uses Player2's hosted
  API with the strong **`gpt-oss-120b`** model by default.

```
/ai admin player2 on
/ai admin player2            # show status (local vs online, model, key)
/ai admin player2 url <url>  # repoint the online endpoint if needed
```

### The `PLAYER2_KEY` (online)

- Blockpal reads the key from the **`PLAYER2_KEY` environment variable** (or the
  `-Dplayer2.key` JVM property). It is **used but never written to disk**, and is
  **never baked into the mod jar** — the same secure pattern as `BLOCKPAL_API_TOKEN`.
- On a server you host, set `PLAYER2_KEY` in the environment before launching.
- A key stored via config is obfuscated at rest (still, prefer the env var).
- **Do not** hardcode a real key into a public build — the jar is decompilable, so
  anyone who downloads it would get your key.

> **Endpoint note.** player2.game also publishes an `/api/v1/mcp` route, which is an
> **MCP** server — a different protocol from the OpenAI-style chat-completions that
> Blockpal speaks. Blockpal's online default is Player2's **chat-completions**
> endpoint (`https://api.player2.game/v1/chat/completions`); use
> `/ai admin player2 url <url>` if Player2's chat endpoint ever changes.

## Related

- [Growth — the AI village game](Growth-Village)
- [Per-Player Keys & Models](Per-Player-Keys-and-Models)
- [Settings](Settings) · [Troubleshooting](Troubleshooting)
