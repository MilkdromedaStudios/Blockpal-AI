# Possession Mode

Possession mode flips the usual relationship around: instead of *you* telling the
companion what to do, you hand your **own** character over to it and let the AI drive
*you*. Open the console, type what you want done, and your companion moves you, mines,
places, fights and runs commands to make it happen — narrating what it's doing as it
goes.

> **Available now (3.16.0).** Start it with `/ai possess` (Java client) or steer it by
> text with `/ai possess <instruction>` on any client. It's fully server-side, so it
> works on **any server running Blockpal** and in **singleplayer** — there is nothing
> to install on the controlled client. Locomotion drives the player with server-side
> velocity (the same technique the mini-games use), so it can feel like being *pulled*
> along rather than walking; expect this to get smoother with tuning.

> **On a server that doesn't have Blockpal?** Use the **client-side** version instead:
> `/aidrive` drives *your own* character by simulating your inputs, for basic tasks only
> (mining/gathering — never combat), and is hard-blocked on anti-cheat networks like
> Hypixel. See [Client-Side Assistant](Client-Assistant).

## How to use it

1. **Stand near a companion you own** and make sure the AI has a key it can use
   (a shared server key, or your own via `/ai mykey <token>` — see
   [Per-Player Keys & Models](Per-Player-Keys-and-Models)).
2. Run **`/ai possess`**. On a Java client with Blockpal, the **Possession Console**
   opens — a little text box with a live log above it.
3. **Type an instruction** and press Enter (or **Send**): *"mine the iron ore below
   me"*, *"walk to 120 64 -30"*, *"fight the nearest zombie"*, *"place cobblestone to
   bridge that gap"*, *"chop this tree"*.
4. Watch the log — it shows what the AI is thinking, the plan it made, and when it's
   done. Type another instruction any time; the newest one takes over.
5. **Stop possession** with the **Stop possession** button, or `/ai possess stop`.
   You immediately get control back.

### No console? Use text

The console is only a convenience. On **Bedrock** (via Geyser) or a **vanilla Java**
client — anything that can't show the Blockpal GUI — possession still works entirely in
chat:

- `/ai possess <instruction>` — start (if needed) and queue an instruction.
- `/ai possess stop` — end possession.

Status updates come back as ordinary chat messages instead of a console feed.

## What it can do

Possession uses the same [action vocabulary](AI-Actions) the companion uses for its own
tasks, but applied to **your** body:

- **Move** — walks/climbs you toward a target (teleports to catch up over long gaps).
- **Mine / break / place** — digs, clears a small area, or places blocks within reach.
- **Use blocks** — flips levers, presses buttons, opens doors.
- **Fight** — attacks the nearest hostile in range.
- **Collect items** — walks you over nearby drops to pick them up.
- **Run commands** — the same gated set the bot may run (subject to *Allow commands*
  and the [denylist](Running-Commands)).
- **Chat** — says a line as you.

Your companion's [personality](Personalities) still flavours how it narrates.

## Who can do it

You can only ever possess **yourself**, and only with a companion **you own**. There is
no way to possess another player, so there is nothing to grief — it's your character,
your bot, your choice. Possession ends automatically if you disconnect, if the companion
is dismissed or dies, or if an admin turns the feature off.

## Turning it on/off (admins)

Possession is **on by default**. Operators can toggle it:

- In the panel: **Settings → Behavior → Allow possession mode**.
- By command: `/ai admin possession on|off`.

The setting is `allowPossession` in `config/blockpal/config.json`. When it's off,
`/ai possess` politely refuses.

## Honest limits

- **Server-authoritative movement feels like a tug.** Minecraft clients own their own
  position, so the server pushes you with velocity (and teleports to catch up). It
  moves you reliably, but it can feel like being dragged rather than walking naturally.
  This is the same server-side technique the [mini-games](Minigames) use, and it's the
  reason possession needs no client mod — the trade-off is feel, which will improve with
  tuning.
- **Facing is best-effort.** Because the client owns your camera, the AI's server-side
  aim mainly affects server-side logic (like attacks) rather than smoothly turning your
  view.
- **It needs an API key**, like every other AI feature — quick "come/follow" style
  orders don't, but planned actions do.
- **Sessions are in-memory.** A server restart ends any active possession (you simply
  have control again afterwards).
