# Local AI — a model that runs on your own graphics card

Blockpal can download a small language model and run it **on your machine**, on your
GPU. No API key, no account, no internet after the download, and nothing you or your
companion sees ever leaves the computer.

This is the **no-key option** now. It replaced the free keyless internet service, which
was small, shared with everyone, rate-limited and sometimes simply down.

---

## Setting it up

```
/ai local setup          # shows you exactly what it would download — downloads nothing
/ai local accept         # the yes. Only this starts a download.
```

That is the whole flow, and the split is deliberate: `setup` tells you the model, its
size, what it will run on and where it goes; `accept` is you agreeing to it. **Nothing is
ever downloaded without that explicit yes** — not when you switch connection, not when
the server starts, not when you open the settings panel.

Both are operator-only.

### What `setup` shows you

```
Blockpal would like to download an AI model and run it on this machine.

  Model    Qwen2.5 3B Instruct  (1.8 GB)
           The best all-rounder that still fits a 4 GB card. Recommended.
  Runtime  llama.cpp b7891  (142 MB)
           will run on: NVIDIA GPU (CUDA)
  Uses     ~2.1 GB of video memory
  Saved to .../config/blockpal/localai
  Graphics card: NVIDIA GeForce RTX 3060, 12288 MiB

Total download: 2.0 GB
```

---

## The models

Every one is 4-bit quantised and **under 3 GB** — a rule the build enforces, not a
guideline.

| id | Model | Size | Good for |
|----|-------|-----:|----------|
| `qwen3b` | Qwen2.5 3B Instruct | 1.8 GB | **Default.** Best all-rounder that fits a 4 GB card. |
| `coder3b` | Qwen2.5 Coder 3B | 1.8 GB | Better at the bot's script language; blunter in chat. |
| `llama3b` | Llama 3.2 3B Instruct | 1.9 GB | Chatty and friendly, a little weaker at JSON. |
| `qwen1.5b` | Qwen2.5 1.5B Instruct | 0.9 GB | Laptops and integrated graphics. Simpler answers. |

```
/ai local models              # list them
/ai local setup coder3b       # pick a different one (asks again — it's a new download)
```

---

## What actually runs

[llama.cpp](https://github.com/ggml-org/llama.cpp)'s `llama-server`, downloaded from its
official GitHub releases. It speaks the same OpenAI-compatible API Blockpal already uses
for every other provider, so once it is up nothing else in the mod needs to know it is
local.

It is bound to **127.0.0.1** and is never exposed to the network.

### Which build you get

llama.cpp publishes a separate binary per platform *and per GPU backend*, and the
difference between "runs on your graphics card" and "grinds along on the CPU" is entirely
which one you get. Blockpal picks at download time:

| Your machine | What it uses |
|---|---|
| Windows + NVIDIA | CUDA build (plus its GPU runtime libraries) |
| Windows, other GPU | Vulkan build |
| Linux | Vulkan build — **there is no Linux CUDA release**, and NVIDIA's Vulkan driver handles it |
| macOS, Apple Silicon | The standard build — Metal is compiled in |
| Anything else | CPU. It works, but expect ten seconds or more per reply. |

The exact filenames are resolved from the release listing at download time rather than
hard-coded, because a pinned filename becomes a broken download the moment the project
renames anything.

---

## Running it

```
/ai local            # status: model, runtime, hardware, state
/ai local start      # start it (no download — the files are already here)
/ai local stop       # stop it; files are kept
/ai local log        # what the model server printed, if something went wrong
```

Once downloaded it starts with the server automatically (turn that off with
**Start it with the server** in the panel).

---

## Settings

**Settings → AI & API → Local AI on this machine**.

| Setting | Default | What it does |
|---------|---------|--------------|
| Local model | `qwen3b` | Which model to run |
| Start it with the server | on | Bring it up automatically once downloaded |
| Context window | 4096 | How much it holds in mind; bigger uses more memory |
| GPU layers | -1 (auto) | How much to put on the card. -1 lets llama.cpp decide, which is usually right |
| Local model port | 8081 | Loopback port. Never exposed off this machine |

> **Consent is not a setting.** The panel can change every value above, but it cannot
> agree to a download on your behalf — a settings packet that claims consent is ignored,
> and there is a test that proves it. Agreeing happens at the `/ai local setup` prompt
> that tells you the size, or nowhere.

---

## Is it as good as ChatGPT?

No, and it doesn't pretend to be. A 3-billion-parameter model at 4-bit is roughly two
gigabytes; a frontier model is a thousand times larger. What it gives you instead:

- **Free forever** — no key, no bill, no rate limit.
- **Private** — prompts and the pictures from your companion's eyes never leave the machine.
- **Offline** — it works with the internet unplugged.
- **Fast** — about a second, against several for a cloud round trip.

For "follow me", "chop those trees", "build a wall here" and holding a conversation, it
is comfortably good enough. For elaborate multi-step engineering, a real key
(**Settings → AI & API → My own API key**) or the [MCP server](MCP-Server) will do better.

---

## Honest limits

- **The download is real.** About 2 GB, once. On a metered connection that matters, which
  is exactly why it asks first.
- **It needs the video memory.** ~2.1 GB for the 3B models on top of whatever Minecraft is
  using. On a 4 GB card that is fine; on a 2 GB card use `qwen1.5b`.
- **On a dedicated server it runs on the server's hardware**, not the player's — the model
  lives wherever the Minecraft server does. A headless box with no GPU will fall back to
  CPU and be slow.
- **Not verified in a real game.** The download, unpacking, GPU selection and process
  management are covered by tests as far as they can be without a GPU in the build
  environment; what nobody has yet done is watch it actually answer a companion's question
  on a real graphics card.
