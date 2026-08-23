# Speed & combat — how fast it acts and how well it fights

Two settings that change how a companion *feels* more than anything else in
Blockpal.

---

## Reaction speed

> "Its actions are too slow."

They were, and it was not one thing. Blockpal had accumulated a dozen separate
invented delays — a pause between plan steps, a randomised "reaction" jitter, a
head that could only turn 22° per tick, a five-second gap between thoughts, a
rate limit on looking, a cap on how much script could run per tick. Individually
each was defensible. Stacked up they made a companion that was *never actually
busy* still look idle.

The worst of them: the think cooldown was measured from when the **previous**
round started. A bot that finished its script in one second then stood still for
four before it was allowed to think again.

Now every one of them resolves from a single setting.

| Speed | Feels like | Step pause | Head turn | Re-thinks every | Mining |
|-------|------------|-----------:|----------:|----------------:|--------|
| `instant` | No waiting at all | 0 ticks | 180°/tick | 1 s | 2× |
| `fast` *(default)* | Snappy but believable | 2 ticks | 55°/tick | 2 s | normal |
| `human` | The old, deliberate feel | 8 ticks | 22°/tick | 5 s | normal |

```
/ai speed              # what it's set to
/ai speed instant      # (ops) change it
```

Also on **Settings → Behavior → How fast it acts**.

### What speed does *not* do

It never makes the bot cheat. Mining still costs real block-hardness time at
`fast` and `human`; reach limits, tool speeds and pathfinding are untouched.
`instant` doubles mining speed and says so — it is the one setting that trades
realism for pace, and it is not the default.

A companion that finishes a script now **re-plans immediately** rather than
waiting out a timer, which is the single biggest improvement of the lot.

---

## Combat skill

The old behaviour was: notice a monster, walk at it, swing. That is how something
that has never been in a fight behaves.

| Skill | What it does |
|-------|--------------|
| `basic` | Walks in and swings. The pre-3.26 behaviour, kept so nothing regresses. |
| `skilled` *(default)* | Holds a range instead of standing inside the enemy's swing, circles on an irregular beat, raises a shield between its own swings, backs off and eats when badly hurt. |
| `expert` | Adds crit timing (jump, then strike on the way down) and bow work at range. |

```
/ai combat             # what it's set to
/ai combat expert      # (ops) change it
```

Also on **Settings → Behavior → Fighting**.

All of it goes through the same keys and mouse buttons a player uses — the swing
still has to reach, the shield still has to be in the off hand, the bow still has
to be drawn and the arrows still have to be in the backpack.

---

## Fighting players (PvP)

**Off by default, and narrow when on.**

A companion that swings at people is a griefing tool, so every one of these must
hold before a swing can reach a person:

1. `allowPvp` is on — ops-only, and **an upgrade never turns it on**.
2. The target is **not** the bot's owner, and not anybody the owner trusts.
3. The target is actually playing — not creative, not spectating, not invulnerable.
4. The bot was **provoked**: that player hurt it or its owner in the last ten
   seconds, or the owner named them with `/ai attack <player>`.

There is no configuration in which a companion picks a fight. It defends, or it
does what its owner directly told it to do about one specific person. An ordered
fight lasts 60 seconds and is deliberately **not saved** — it never survives a
restart or turns up in a world file.

```
/ai admin pvp on          # (ops) allow it at all
/ai attack <player>       # (owner) point your companion at someone
```

> **Vanilla still has the final say.** A server with PvP disabled refuses
> player-to-player damage in its own hurt path regardless of any of this, so a
> companion there can swing and nothing will land.

The decision is made in exactly one place (`combat/PvpRules`), and every path
that can produce a swing — a script calling `attack()`, the combat brain, a
learned policy pressing the button — goes through it. There is no second check to
forget to update.
