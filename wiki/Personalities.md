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

## Custom personalities

Want something that isn't on the list? Write your own in plain words:

```
/ai personality custom a wise old wizard who speaks in riddles
/ai personality custom a sarcastic robot butler
```

You can also do it in the **My Settings** screen (`/ai mymenu`): set the **Personality**
cycler to **Custom** and type a description in the box, then **Save** (stand near your
bot when you do).

**It's kept family-friendly automatically.** Your text is checked by the AI before it's
applied — anything with profanity, slurs, or adult/unsafe content is rejected with a
short reason, so just try a friendlier description. (Because the check uses the AI, a
custom personality needs a working [API key](Getting-Started); without one it can't be
verified and is refused.)

Switching back to any built-in personality clears the custom text.

> **Ops:** you can turn custom personalities off entirely with the **Allow custom
> personalities** toggle on the Settings → Behavior tab (`allowCustomPersonality`),
> leaving players with the six built-ins only.

## Server default

Newly summoned bots start with the server's **default personality**, which is `friendly`
out of the box. Set it on the **Settings → Identity** tab (the *Default personality*
picker) or via the `defaultPersonality` config value. Changing it makes every fresh
`/ai summon` start with that character; existing bots keep whatever they already have.

## Notes

- The in-character wording in mid-task chat comes from the language model, so it
  needs an API key (like any AI feature). The quick, no-API replies are always in
  character regardless.
- An unknown or missing personality id safely falls back to the server default.
