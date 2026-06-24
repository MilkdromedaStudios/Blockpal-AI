# Personalities

Your Blockpal companion has a **personality** that shapes both *how it talks* and
*how it acts*. Quick replies (follow, come, stay, picking up gear, …) are written in
its voice, and its personality is also woven into the AI planner — so the things it
*says* while working on a task stay in character too.

## Choosing one

```
/ai personality            # list all personalities and show your bot's current one
/ai personality <id>       # give the nearby bot a new personality
```

For example, `/ai personality grumpy` makes your companion delightfully cranky.

Each bot remembers its own personality, so you can have a cheerful builder and a
stoic guard standing side by side. The choice is saved with the bot and survives
relogs and restarts.

## The personalities

| Id | Name | Vibe |
|----|------|------|
| `friendly` | Friendly | Warm, helpful and easygoing — the classic Ethan. *(default)* |
| `cheerful` | Cheerful | Bubbly and upbeat — endless enthusiasm for every little job. |
| `grumpy` | Grumpy | Grumbly and sarcastic — gets the job done, but won't pretend to like it. |
| `stoic` | Stoic | Terse and professional — a calm, no-nonsense operator. |
| `heroic` | Heroic | Brave and dramatic — a chivalrous, larger-than-life champion. |
| `shy` | Shy | Timid and soft-spoken — gentle and a little nervous, but always willing. |

A personality only changes *flavour* — every companion is equally capable. It never
changes what tasks it can do, only the wording it uses.

## Server default

Newly summoned bots start with the server's **`defaultPersonality`** (see
[Settings](Settings)), which is `friendly` out of the box. Change it to make every
fresh `/ai summon` start with a different character. Existing bots keep whatever
personality they already have.

## Notes

- The in-character wording in mid-task chat comes from the language model, so it
  needs an API key (like any AI feature). The quick, no-API replies are always in
  character regardless.
- An unknown or missing personality id safely falls back to the server default.
