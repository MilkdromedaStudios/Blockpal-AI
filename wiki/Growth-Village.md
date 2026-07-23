# Growth — an AI village that lives or dies on its own

**Growth** is a Blockpal mini-game where you watch (and can join) an **AI-run
village** that grows or collapses on its own. It's the "living world" mode: the
villagers are real Blockpal AIs that build, farm, teach and trade — and *talk*
about it — while a fast day-clock decides whether the settlement thrives or dies.

Start it with:

```
/village start          # grows a village around where you stand
/game start growth       # exactly the same thing
```

## How it works

- **The people are AIs.** Each villager is a Blockpal bot with:
  - a **role** — *builder, farmer, teacher, trader, guard, scholar*;
  - its own **personality**; and
  - (when you're running **local models** via Ollama) its own **small model**, so
    different villagers genuinely *think differently*.
- **Days run at 2× speed.** Every "day" the village's jobs turn into **food,
  houses, knowledge, morale and defence**:
  - farmers grow food, everyone eats;
  - builders raise **houses** (a real little hut is placed);
  - teachers & scholars raise **knowledge**, which makes everyone more efficient;
  - traders lift **morale**;
  - guards fend off **night raids**.
- **It grows and it dies.** A fed, housed, hopeful village **welcomes new
  settlers**. A starving or broken one **loses people** — and mobs can kill
  villagers too.
- **They show their intelligence.** Villagers narrate what they're doing. With a
  language model reachable (a key, Player2, or Ollama) the line is written by that
  villager's *own* model; otherwise it's drawn from a role script.

## Be one of them

```
/village join <role>     # builder | farmer | teacher | trader | guard | scholar
/village leave           # step back (the village plays on without you)
/village status          # day, population, food, houses, morale, knowledge, your role
```

Joining a role makes your own work count toward that role's daily output.

## Winning, losing, surrendering

- **If the village dies out (population 0), you win** — you outlasted them.
- **If it grows as big as ever** (its peak reaches the target population, default
  **24**), you're offered the chance to **concede**:

  ```
  /village surrender       # only once it has grown as big as ever
  ```
- **End it any time** (founder only): `/village stop` removes the villagers.

## Settings

Configured in `config/blockpal/config.json` (or by an admin):

| Setting | Default | What it does |
|---|---|---|
| `villageTargetPopulation` | 24 | Peak population that unlocks `/village surrender` |
| `villageStartPopulation` | 5 | How many villagers a fresh game starts with |

For the smartest, most varied villagers, run **local models** — see
[Local & Player2 AI](Local-AI-Ollama-and-Player2). With a pool of Ollama models,
each villager is handed a different one.

## Honest limits

Growth runs **in your current world**. The AI narration and the growth/decline
tuning (day length, food and morale curves, raid odds) are best experienced with a
language model set up, and want in-world play-testing. It's server-side, so **Java
and Bedrock** players both play it.
