# Talking to Your Assistant

You don't need a slash command to give orders — Blockpal listens to chat.

| You type in chat | What happens |
|------------------|--------------|
| `follow me` | Follows you *(instant, no token)* |
| `come here` | Comes to you, teleports if far *(instant)* |
| `stay` / `wait` | Holds position *(instant)* |
| `stop` | Cancels current task *(instant)* |
| `where are you?` | Reports location *(instant)* |
| `Ethan, build a wall` | Sends "build a wall" to the AI planner |
| `can you clear out these trees?` | Active analysis → mines the trees |
| `ugh, I really need a shelter` | Active analysis → builds a shelter |

## How it decides

- **Chat listening** — monitors all server chat; trigger words activate the assistant
  without using its name. Toggle with `/ai listen on|off`.
- **Direct addressing** — `"Ethan, follow me"` / `"Ethan: follow me"`. The name prefix
  is stripped and the rest is run. This always works.
- **Quick intents** — the common phrases above are handled instantly with no API call.
- **Active analysis** — with this on (default), the LLM classifies *every* 5+ character
  message within 48 blocks to decide whether you need the assistant and what you want.
  Rate-limited to about once every 3 seconds to avoid API spam. Toggle with
  `/ai active on|off`. (Needs a [token](Getting-Started).)

## Owner-only obedience

Only the player who spawned the assistant can give it orders via chat. Other players
are politely turned away.

## Autonomous mode

The owner can say **"do it yourself"** (or similar) to hand off control. The bot then
self-directs — picks its own survival tasks, narrates its decisions roughly every 30 s.
Cancel it with **"stop"**, **"follow me"**, or **"stay"**.

## Expressive responses

All messages use natural first-person dialogue in `Name: "message"` format, drawn from
randomised pools so replies vary. When health drops critically low in combat, the
assistant calls out to nearby players for help before retreating.
</content>
