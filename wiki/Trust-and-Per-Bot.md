# Trust & Per-Bot Management

Companions are **owned** by the player who summons them, and they remember their
own settings (name, skin, personality, and now a **trust list**) individually. This
page covers managing several bots separately and letting friends help command one.

## Each bot is its own

The everyday management commands act on the companion **you are standing next to**, so
two companions can be set up completely differently:

- `/ai name <name>` — rename the nearby bot
- `/ai skin <name>` — re-skin it (see [Custom Skins](Custom-Skins))
- `/ai personality [<id>]` — change how it talks & acts (see [Personalities](Personalities))

To see everything you own at a glance:

```
/ai bots
```

`/ai bots` lists every companion **you** own across all dimensions, with each one's
mode, dimension, position, health, personality and how many players it trusts — so a
group of bots is no longer indistinguishable.

> A dedicated per-bot GUI panel is planned; for now management is by standing next to
> the bot you want to change (and `/ai bots` to find them).

## Trusting other players

By default only the **owner** can give a companion orders. To let a friend command a
specific bot, stand next to that bot and run:

```
/ai trust <player>     # the player must be online
/ai untrust <player>   # remove them (works for offline players by name too)
/ai trust list         # who's trusted on this bot
/ai trust clear        # remove everyone
```

Trust is **per bot**, so you can give different friends access to different
companions. The trusted player gets a heads-up message when you add them.

### What trusted players can and can't do

| Action | Owner | Trusted player | Server admin |
|--------|:-----:|:--------------:|:------------:|
| Orders: come / follow / stay / stop | ✅ | ✅ | ✅ |
| Locate, view inventory | ✅ | ✅ | ✅ |
| Give an AI task (`/ai <task>`, chat) | ✅ | ✅ | ✅ |
| Rename / re-skin / change personality | ✅ | ❌ | ✅ |
| Dismiss the bot | ✅ | ❌ | ✅ |
| Edit the trust list | ✅ | ❌ | ✅ |

Trusted players can command the bot **in chat** too (e.g. "Ethan, follow me"), exactly
like the owner — see [Talking to Your Assistant](Talking-to-Your-Assistant). Anyone who
isn't the owner, trusted, or an admin is politely turned away.

> **Security note:** these checks are enforced on the **server**, not just hidden in the
> UI, so a modified client can't command or dismiss a bot it isn't allowed to. See
> [Security](Security).
