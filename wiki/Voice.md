# Voice — talk to your agent, and hear it talk back

As of **3.19.0** your companion has a real voice. Hold the **push-to-talk key**
(default **V**) and speak — your words are transcribed with **Whisper
large-v3-turbo** and delivered to **your own companion only**. Everything the
agent says is also **spoken out loud** with a natural text-to-speech voice —
**privately**: by default only *you* hear your agent, and nobody else hears it
unless you choose to share.

## Talking to your agent (push-to-talk)

1. Stand in the world (no menu open) and **hold V**. The action bar shows
   `🎤 Listening…`.
2. Say what you want — *"follow me"*, *"stay here"*, *"go chop those trees and
   build a little hut"* — and **release the key**.
3. The recording is transcribed on **your own machine's connection** (audio never
   goes to the game server — only the resulting text does) and handled exactly
   like a chat message addressed to your bot by name: quick intents (come /
   follow / stay / stop / where are you) are instant and free, anything else
   becomes an AI task.

Voice input is **always private**: it never appears in public chat, and it can
only ever command **your own** companion — not a friend's, not a trusted bot.
You'll see a private `You → Ethan 🎤 "…"` line confirming what was heard.

- **Transcription engine:** with an API key set, Whisper large-v3-turbo
  (`sttModel`) on HuggingFace's inference API (`sttApiUrl`) — the same key you
  already use for the chat models. With **no key at all** it falls back to the
  free voice-capable service (like the text AI does), so voice works out of the box.
- **Rebind the key** with `/aivoice key <GLFW code>` (86 = V, 66 = B, 71 = G).
  The key is only read while no GUI is open, so typing "v" in chat is safe.

## Hearing your agent (private text-to-speech)

Every line your companion says in chat is also synthesized and played through
your speakers, in its own voice. This is **client-side and private** — the audio
plays only for players the server addressed, never for bystanders.

- Turn it off/on for yourself: `/aivoice off` / `/aivoice on`.
- Interrupt a long line: `/aivoice stop`.
- **Pick your default voice:** `/aivoice voice <id>` (`alloy`, `echo`, `fable`,
  `onyx`, `nova`, `shimmer`, `coral`, …).
- **Give a specific bot its own voice:** stand near it and `/ai voice set <id>`
  — per-bot and saved with the bot, so linked companions sound like different
  people.
- Hear a sample: `/aivoice test Hello! I'm your companion.`

Synthesis uses the free voice-capable OpenAI-compatible service (no key
needed), requesting WAV audio the game can play natively.

## Sharing & linking — who hears whose agent

By default **you only hear your own agent**. Sharing changes that:

| Command | Effect |
|---------|--------|
| `/ai voice` | Status: server voice on/off, your bot's voice, how it all works |
| `/ai voice share <player>` | Let an online player **hear your agent** too |
| `/ai voice unshare <player>` | Stop sharing with them |
| `/ai voice clear` | Stop sharing with everyone — private again |
| `/ai voice list` | Who hears your agent, and whose agents you can hear |
| `/ai voice set <id>` | Give your nearby bot its own TTS voice |

Sharing works in both directions independently: you hear a friend's agent only
if **they** share with **you**.

## Advanced talking — linked agents never interrupt each other

When agents are **shared and linked** (a share in either direction links their
owners into one conversation group), Blockpal coordinates their speech like
people in a real conversation:

- All linked agents' lines go through one **turn-taking queue** on the server —
  while one agent is speaking, the others **wait their turn** instead of
  talking over it.
- Speaking time is estimated from the line's length at a natural speaking rate,
  with a cap so one rambling agent can't hog the conversation.
- Your own client additionally plays incoming speech strictly one utterance at
  a time — a second guarantee that voices never overlap on your machine.

Unlinked agents (different groups) are independent — your private conversation
doesn't wait for someone else's.

## Server management (ops)

The whole voice layer is server-gated:

- **`/ai admin voice on|off`** — enable/disable voice for everyone (text
  command, works from Bedrock/vanilla clients and the console).
- The same toggle lives in the panel: **Settings → Behavior → "Allow agent
  voice"**.
- When off, the server never sends agent speech and refuses push-to-talk input
  with a friendly message.
- The FPS kill-switch and `/ai admin disable` also silence voice (no bots = no
  speech), and voice shares are in-memory, resetting on server restart (like
  parties).

## Settings reference (client-local unless noted)

| Setting | Default | Meaning |
|---------|---------|---------|
| `allowVoice` | `true` | **(server)** master gate for the whole voice layer |
| `voiceResponses` | `true` | Speak the agent's replies out loud on this client |
| `voicePushToTalkKey` | `86` (V) | Push-to-talk key as a GLFW key code |
| `sttApiUrl` | HF Whisper endpoint | Where push-to-talk audio is transcribed |
| `sttModel` | `openai/whisper-large-v3-turbo` | The transcription model |
| `ttsVoice` | `alloy` | Default TTS voice when a bot has none of its own |

## Privacy & honesty notes

- **Audio never reaches the game server.** The mic recording is transcribed
  from your own machine straight to the transcription API; the server receives
  only the final text (same as if you'd typed it).
- The mic is only open **while the key is held** (hard 30-second cap), and
  nothing is recorded in menus.
- Voice output/transcription are network services — on an outage the agent
  simply stays text-only; chat and commands are unaffected.
- Like other recent features, the live audio path (microphone capture, the
  Whisper/TTS endpoints, playback) needs real-machine testing — it can't run in
  CI. If your setup has no microphone, push-to-talk fails with a clear message
  and everything else keeps working.
