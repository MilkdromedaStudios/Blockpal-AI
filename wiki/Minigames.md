# Minigames

Minigames turn Blockpal from "an AI companion in your world" into "an AI companion you
**play games with**" — solo with Ethan, or together with friends you invite (see
[Friend Sharing](Friend-Sharing)). They're built to get more fun and more chaotic the
more players join, and each one is a self-contained, **resumeable** game you can leave
and come back to later.

> **Available now (3.13.0).** Start a game with `/game start <mode>` (see *How to play*
> below). Games currently run **in your current world** — One Block builds a sky platform.
> The "each game is its own separate, resumeable world" vision (custom dimensions) is a
> planned enhancement, so for now a game lasts until it's stopped rather than being saved
> and reloaded later.

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
A **fusion of the two signature co-op modes**: everyone is **Chained together** *and*
shares **one health pool** at the same time. Stay close, stay alive — together or not at
all.

## How to play

1. (Optional) Gather friends into a party — see [Friend Sharing](Friend-Sharing):
   `/party invite <player>`, and they `/party accept`.
2. The party leader starts a round:

```
/game list            # show the modes
/game start <mode>    # chained | samehealth | oneblock | fusion
/game stop            # leader ends it; a member just leaves
```

Everyone in the party — and their bots — is pulled into the game, and Ethan plays
alongside you. Solo works too: run `/game start` with no party and it's just you and your
bot.

> Games run in your current world for now. One Block drops you on a fresh sky platform;
> the other modes play wherever you are. A separate, saved, resumeable world per game is a
> planned enhancement.

## Related pages

- [Friend Sharing](Friend-Sharing) — host a world and invite friends (Java and Bedrock).
- [Trust & Per-Bot Management](Trust-and-Per-Bot) — let friends command your bot.
- [Bedrock (Geyser)](Geyser-Bedrock) — how Bedrock friends join, and one-click hosting.
