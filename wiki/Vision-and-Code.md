# Vision & Code — how a companion thinks

**3.25.0 replaced how the AI decides what to do.** It used to ask a model for a list of
actions with coordinates attached, then carry them out. Now it does what a player does:

> **Look at the world → decide → write a little program → press the keys.**

The bot is honestly a bit **dumber** this way. That's the trade. Everything it manages,
it managed with a player's controls, a player's reach and a player's view — no teleport,
no set-block, no seeing through walls.

---

## The loop

**1. It looks.** Blockpal renders a small picture from the companion's own eye position —
a ray per pixel, about a 90° field of view, with a white cross marking the centre of its
view. Blocks are painted in their map colours, shaded by which face is lit and how far
away they are; creatures and dropped items are drawn as coloured markers. Along with it
goes the same scene in words: what the crosshair is on, what's in view, what it's standing
on, what it can see moving.

Nothing behind it. Nothing through walls. Nothing past the view distance. That limitation
is the feature.

**2. It thinks.** The picture, the written scene, its goal, what it's carrying and what
happened last round go to the model. The model answers with one sentence of reasoning,
optionally something to say out loud, and a short script.

**3. It acts.** The script runs on the bot's virtual keyboard and mouse. Then it looks
again — the world has changed, and so has the plan.

---

## The scripting language

Small on purpose. Variables, `if`, `repeat`, `while`, `break`/`continue`, comments, and a
fixed list of actions. Here's a real one:

```
# chop the tree in front of me
lookAt(120, 71, -43)
goTo(120, 70, -41)
repeat 5 {
  mine()
  if blockAt(120, y() + 1, -43) == "air" { break }
}
collect(6)
say("Got the wood!")
```

And a chest run:

```
let n = count("cobblestone")
if n > 32 {
  let spot = find("chest", 10)
  if spot != "" {
    goTo(posX(spot), posY(spot), posZ(spot))
    openContainer(posX(spot), posY(spot), posZ(spot))
    put("cobblestone", 64)
    closeContainer()
  }
}
```

### Senses

`x() y() z()` · `yaw() pitch()` · `health() maxHealth()` · `time()` · `light()` ·
`onGround()` · `blockAt(x,y,z)` · `lookingAt()` · `distanceTo(x,y,z)` ·
`find("chest", radius)` · `findEntity("zombie", radius)` · `nearestItem(radius)` ·
`posX(s) posY(s) posZ(s)` · `ownerDistance() ownerX() ownerY() ownerZ()` ·
`count("coal")` · `has("axe")` · `holding()`

### Controls (instant)

`say(text)` · `log(text)` · `select("pickaxe")` · `sneak(bool)` · `sprint(bool)` ·
`drop("dirt", 10)` · `equipBest()` · `eat()`

### Actions (they take time)

`wait(ticks)` · `walk/walkBack/strafeLeft/strafeRight(ticks)` · `jump()` ·
`look(yaw,pitch)` · `lookAt(x,y,z)` · `turn(dYaw,dPitch)` · `goTo(x,y,z)` · `mine()` ·
`mineAt(x,y,z)` · `attack(ticks)` · `use()` · `useAt(x,y,z)` · `place(x,y,z)` ·
`collect(radius)` · `followOwner(ticks)`

### Containers — chests and everything else

`openContainer(x,y,z)` · `containerList()` · `take("coal", 8)` · `takeAll()` ·
`put("raw_iron", 8)` · `putAll()` · `closeContainer()`

Works on chests (double chests too), barrels, shulker boxes, hoppers, droppers,
dispensers and **furnaces** — putting ore and coal into a furnace lands them in the input
and fuel slots automatically, and the furnace smelts them the ordinary way. The bot has to
be **within reach**, and the lid opens and clicks like it would for you.

---

## What the buttons actually do

This is where "non-cheating" is decided:

- **Mining** accumulates real break progress from the block's hardness and the tool it's
  actually holding, with the cracks showing. Bedrock doesn't budge. The wrong tool takes
  the same painful time it takes you.
- **Placing** puts down the block it's *holding*, against the face it's aimed at, within
  reach, consuming it. Logs take the axis you'd expect; facing blocks turn toward the
  placer.
- **Attacking** swings on a weapon cooldown at whatever the crosshair is on — and
  **never at a player**. A companion is not a weapon.
- **Walking** is the mob's own movement. It's blocked by walls, it falls off cliffs, it
  swims.
- **Looking** turns at a human rate. No snap-aim.

---

## Try it yourself

```
/ai look                              # read what your companion can see right now
/ai code lookAt(100,64,20) goTo(100,64,22) mine()
/ai code stop
```

Anyone who may command the bot may hand it a script — it's the same limited vocabulary
the AI has.

---

## Settings

On **Settings → Behavior**:

| Setting | Default | What it does |
|---------|---------|--------------|
| Thinking style | Look and write code | Switch back to the classic JSON action planner |
| Send pictures to the AI | on | Off = the written scene only (cheaper; needed for models with no vision) |
| Live on its own | on | Keep-alive reflexes with no AI at all (see below) |
| Warn me in creative mode | on | Because companions never teleport |

Picture size and view distance are `visionWidth` / `visionHeight` / `visionRange` in
`config/blockpal/config.json` (80×45 at 48 blocks by default). Each look costs one ray
per pixel on the server thread, so bigger is not free.

`scriptMaxTicks` (default 1200 = 60 s) caps how long one script may run.

---

## Living on its own

Between thoughts — and when there's no AI connected at all — a set of reflexes keeps the
companion alive with no API of any kind:

- eats when hurt, drinks a healing potion if it has one
- swims up when it's drowning
- runs out of fire
- hops when it's wedged on terrain
- **walks** back toward its owner when left behind

That last one matters: **companions never teleport.** Not to you, not when they fall
behind, not ever. A companion that blinks to your side isn't living in the world with
you — it's a marker following your camera. The cost lands on flying players, which is
why you get a warning the first time you go creative with one out.

---

## Which models suit this

Anything that can see images does best — the picture is the point. With **MCP**
(see [MCP server](MCP-Server)) that's whatever app you already use: Claude, ChatGPT, Grok
or Gemini, all of which see images.

With an in-game connection, a vision-capable model (`gpt-4o-mini`, `gemini-2.0-flash`,
`claude-3-5-sonnet`, a local `llama3.2-vision`) will play noticeably better than a
text-only one. If a model rejects the image, Blockpal automatically retries with the
written scene alone — a blind model still plays, just worse.

See also: [MCP server](MCP-Server) · [AI Actions](AI-Actions) · [Settings](Settings)
