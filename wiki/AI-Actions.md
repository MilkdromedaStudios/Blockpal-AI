# AI Actions

When you give a natural-language task, the language model returns a structured JSON
plan of 5–15 steps. Each step is one of these **16 action types**:

| Action | What it does |
|--------|--------------|
| `MOVE_TO` | Walk/path to a position |
| `PLACE_BLOCK` | Place a single block |
| `BREAK_BLOCK` | Break a single block |
| `MINE_AREA` | Clear every block in a box (digging, tunnels) — one block per tick |
| `USE_BLOCK` | Flip a lever, press a button, open a door/trapdoor |
| `RUN_COMMAND` | Execute a Minecraft command (see [Running Commands](Running-Commands)) |
| `ATTACK_NEAREST` | Strike the nearest hostile |
| `FOLLOW_PLAYER` | Path to and follow a player |
| `LOOK_AT` | Face a position |
| `JUMP` | Hop (parkour) |
| `SET_SNEAK` | Crouch / un-crouch |
| `CHAT` | Say something |
| `WAIT` | Pause |
| `COLLECT_ITEM` | Pick up a nearby item |
| `STOP` | End the plan |

## Looping activities

Setting `"loop": true` on a plan makes it an ongoing activity — patrol, guard, keep
mining, explore. The assistant re-plans continuously with fresh context each round
(throttled so it doesn't spam the API).

## Puzzles & redstone

Because the assistant sees nearby levers, buttons, doors, pressure plates and redstone
in its planning context, it can solve simple puzzles and escape rooms via `USE_BLOCK`,
and build working contraptions when [command execution](Running-Commands) is enabled.

## Async planning

Planning happens on a background thread, so the entity stays responsive and keeps
fighting/retreating (the reflex layer) while it thinks.
</content>
