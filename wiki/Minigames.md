# Minigames

Minigames turn Blockpal from "an AI companion in your world" into "an AI companion you
**play games with**" — solo with Ethan, or together with friends you invite (see
[Friend Sharing](Friend-Sharing)). They're built to get more fun and more chaotic the
more players join, and each one is a self-contained, **resumeable** game you can leave
and come back to later.

> **Status: in development.** Minigames are the next big feature in Blockpal's
> multiplayer arc. This page describes what they are and how they'll work so you know
> what's coming; the modes below aren't playable in the current release yet. The
> foundations that ship today — [per-bot trust](Trust-and-Per-Bot) and one-click
> [hosting + Bedrock cross-play](Friend-Sharing) — are what they build on.

## What a minigame is

- **You play with the bot.** Ethan isn't a spectator — it's a participant in the game,
  using the same AI it uses everywhere else.
- **Solo or with friends.** Play one-on-one with the bot, or invite friends (up to a
  large group) so a round becomes a party. **Java and Bedrock players can play
  together** — Bedrock friends join through Geyser (see [Friend Sharing](Friend-Sharing)).
- **Dynamic with player count.** The more players in a round, the more intense and
  chaotic it's meant to get — the games scale with the size of the group rather than
  feeling the same at any size.
- **Each game is its own world.** A minigame runs as a separate, **resumeable** game
  space — think of it as "a new world" you can step out of and return to later, with its
  state intact, without disturbing your main world.

## The game modes

### Chained
Every player **and bot is tethered together** — you all move as one connected group.
Wander too far and you yank the others (or get yanked); success depends on moving and
reacting as a team. The more players on the chain, the harder it is to keep everyone in
sync.

### Same Health
Everyone in the round — players and bot — shares **one health pool**. If anyone takes
damage, everyone feels it, and **if one of you dies, you all die.** It turns careless
moments into group stakes and rewards looking out for each other.

### One Block
Everyone starts on **a single block**, skyblock-style, and grows a world out from almost
nothing. Resources are scarce at the start, so early cooperation (and the bot's help)
matters a lot.

### Fusion
An experimental mode still being designed — the idea is to "fuse" things together for a
twist on the others (for example, combining players or bots into a single stronger unit).
The exact rules aren't locked yet; this section will be filled in as it takes shape.

## Resuming a game

Because each minigame is its own saved game space, you can stop partway through and pick
it back up later — the round you set up (and its progress) is still there when you come
back, rather than being lost the moment you leave.

## How you'll start one

The intended flow (subject to change as the feature lands):

1. Open the minigame menu and pick a mode (Chained, Same Health, One Block, …).
2. Invite the friends you want to play with — see [Friend Sharing](Friend-Sharing).
3. Start the round; Ethan joins in and plays alongside everyone.
4. Leave any time and resume the same game later.

## Related pages

- [Friend Sharing](Friend-Sharing) — host a world and invite friends (Java and Bedrock).
- [Trust & Per-Bot Management](Trust-and-Per-Bot) — let friends command your bot.
- [Bedrock (Geyser)](Geyser-Bedrock) — how Bedrock friends join, and one-click hosting.
