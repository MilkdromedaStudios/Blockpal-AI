# Client-Side Assistant (works on any server)

Almost all of Blockpal runs on the **server**. This page covers the one part that runs
entirely on **your own client**, so it works **anywhere** — a vanilla server, someone
else's modded server, a realm, or singleplayer — even when the server doesn't have
Blockpal installed at all.

There are three pieces:

1. A **private AI chat box** (a mini in-game helper / wiki).
2. **On-screen tips** while you play.
3. **Off-server possession** — the AI driving your own character for basic tasks.

All three use your local AI settings (`config/blockpal/config.json`). With **no key**,
they fall back to the built-in **free AI**, so they work out of the box. On a Blockpal
server or in singleplayer you can also set a personal key with `/ai mykey <token>`.

---

## 1. The private chat box — `/aichat` (and the mini panel in the ESC menu)

**Press ESC and the assistant is right there:** a **mini chat panel** sits on the right
side of the pause menu — the recent conversation, a text box, and a Send button — so a
quick question never needs a separate menu. The same panel appears when you **open the
chat screen** (press `T`): click its box, type, and press Enter. The **"Full chat &
history ⛶"** button under the panel opens the full chat box below when you want
scrolling, threads, or the Tips toggle. (On a very small GUI scale with no room for the
panel, the tiny **`✦`** button appears instead.)

The full box opens with **`/aichat`**, the panel's ⛶ button, or the tiny **`✦`** button
that appears in the **top-right corner** of your inventory and any chest/container
screen — anywhere the mouse is free to move.

- Ask anything: recipes, "where do I find diamonds", "how do I beat the Warden", strategy,
  what a block does. Replies appear **only in this box** — nothing is ever sent to the
  server chat, so it's completely private.
- **Scroll** with the mouse wheel or the `▲`/`▼` buttons. Text wraps neatly to the panel.
- **Conversations:** `＋` starts a new one, `◀`/`▶` switch between them. Your history is
  saved between sessions (in `config/blockpal/assistant-chats.json`), up to **100 messages
  per conversation** and **100 conversations** (the oldest roll off).
- The chat only **advises** — it never controls your character and can't type in chat.

## 2. On-screen tips — `/aitips`

The assistant quietly keeps an eye on your situation and, when something notable happens
(low health, starving, on fire, drowning, stepping into a new dimension), drops **one
short survival tip** into the private chat box (open it with `/aichat` or the `✦` button).
Tips are private (local only, never server chat) and rate-limited so they never nag.

Toggle them with **`/aitips on`** / **`/aitips off`** (or the **Tips** button in the chat
box). Default: on.

## 3. Off-server possession — `/aidrive`

On a server that doesn't run Blockpal, you can still let the AI **drive your own
character** for basic tasks.

- **`/aidrive`** opens the drive console (a text box with a status log).
- **`/aidrive <instruction>`** — e.g. `/aidrive mine the ores in front of me` — starts it.
- **`/aidrive stop`** hands control back to you (so does the **Stop driving** button).

It works by **simulating your normal inputs** (walking, looking, mining the block under
your crosshair, etc.), so no client movement mod is needed and the server just sees a
normal player. It's limited to **basic survival tasks**: walking, mining, clearing a small
area, placing/using blocks, collecting drops, jumping and sneaking.

> On a server that **does** have Blockpal, use the server-side [`/ai possess`](Possession-Mode)
> instead — it's fully server-authoritative and doesn't automate your inputs. `/aidrive`
> will point you there automatically.

---

## Will this get me banned? (Please read)

Blockpal is built to **not** get you banned, but you have to understand the line:

- **The chat box and tips are safe on every server.** They only read what's already on
  your screen and give advice — they never touch the world, never automate anything, and
  never send anything to the server. That's no different from glancing at a wiki on your
  phone.

- **Driving (`/aidrive`) is automation**, and *some servers forbid automation in their
  rules even though it isn't a "hack".* Blockpal is deliberately strict about it:
  - It **never attacks players or mobs** — the attack input is released the instant your
    crosshair is on any entity — so it can give **no PvP or combat advantage**.
  - It **never types in chat and never runs commands.**
  - It is **hard-blocked on networks that ban automation** — including **Hypixel** and
    other big minigame servers — no matter your settings. On those servers `/aidrive`
    simply refuses (the chat box still works).
  - On **your own singleplayer/LAN world** it's always allowed.
  - On **any other third-party server** it only runs if you've left client-side
    possession enabled **and** you accept the on-screen warning that automation may break
    that server's rules — **use it at your own risk.**
  - It also **pauses at low health** so it won't walk you into danger.

If you're ever unsure whether a server allows automation, **don't drive on it** — use the
chat box for help instead. The safest choice is to use driving only in your own worlds and
on servers you know allow it.

### Settings

| Setting | Default | What it does |
|---------|---------|--------------|
| `allowClientPossession` | `true` | Master switch for `/aidrive`. The anti-cheat network block overrides it. Turn off to keep only the safe chat + tips. |
| `assistantTips` | `true` | Whether on-screen tips appear. |

Both are **client-local** (in your own `config/blockpal/config.json`) and never sync to
the server.
