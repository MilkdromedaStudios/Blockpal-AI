# PVT — teaching your companion by letting it watch you play

**PVT** stands for *pre-video training*. It is the part of Blockpal that learns
how to act **from watching people play**, instead of asking a language model what
to do next.

It is based on [OpenAI's VPT](https://openai.com/research/vpt) — the work that
learned to play Minecraft from unlabelled video — adapted to what a mod can
actually observe from inside the game.

---

## Why it exists

Every other brain in Blockpal asks a model. That is powerful and it is **slow**:
a round trip is seconds, so the companion looks at the world, thinks, writes a
script, and only then moves.

A learned policy has no round trip. An observation goes in, nine button decisions
come out, in tens of microseconds. The bot reacts within a tick of something
happening in front of it, forever, with no API key and no internet.

The trade is that it is **shallow**. Behaviour cloning reproduces what the
demonstrations contained and nothing else. A policy trained on somebody chopping
trees will chop trees; it will not build you a house because you asked. So the
two work together — see *How it fits with the other brains* below.

---

## Quick start

```
/ai pvt watch on      # let it learn from how you play (off by default)
   ... just play for a while ...
/ai pvt status        # see how much has been banked
/ai pvt train         # (ops) train a policy from it
/ai pvt use on        # (ops) let companions act on what they learned
```

That's the whole loop. Everything else on this page is detail.

---

## What gets recorded

Two things per sample, ten times a second:

- **What you could see** — a coarse 12×7 "retina" cast from your eyes over your
  field of view (how close each thing is, roughly what it is, how hard it would
  be to break), plus your health, footing, motion, what's in your hand, and where
  the nearest threat, dropped item and other player are *relative to where you
  are facing*.
- **What you did about it** — which movement keys were held, whether you jumped,
  sneaked, sprinted, attacked or used, and how far your view turned.

Everything is **egocentric**: distances and bearings from the viewer, never world
coordinates. That is the whole reason a recording made from *your* body can be
replayed by a *companion's* — "a tree two blocks ahead and slightly left" has to
produce the same numbers whoever is looking at it.

A frame is about 300 bytes, so an hour of play is roughly 21 MB. Recordings live
in `config/blockpal/pvt/demos/`.

### What is *not* recorded

Your chat, your name, your coordinates, your inventory contents, your IP — none
of it. The file is a list of which way somebody walked and what was in front of
them. Moments a walking companion could never reproduce are dropped rather than
learned from: creative flight, riding, spectating, and being dead.

### It is opt-in, per player, always

Nobody is recorded until they personally run `/ai pvt watch on`. The
`pvtAutoRecord` setting only decides whether someone who **has already said yes**
starts recording again when they next join — it never opts anybody in.

Consent is stored in `config/blockpal/pvt/recording-consent.txt`, one line per
player, and `/ai pvt watch off` removes it immediately.

---

## How the training works

### Behaviour cloning

The policy is a small network — about a hundred thousand weights — that reads an
observation and picks one class per **action head**: forward, strafe, jump,
sneak, sprint, attack, use, turn, and look up/down.

The heads are separate on purpose. One flat list of every button combination
would need about 44,000 classes, nearly all of which never happen; nine small
heads share their evidence instead, so every frame where somebody walked forward
teaches the forward head whatever their mouse was doing.

Training holds back a tenth of the frames to score against, stops early when that
held-out score stops improving, and keeps the **best** weights rather than the
last ones.

### The inverse dynamics model — the "video" part

Behaviour cloning needs *(what you saw, what you did)* pairs, and the second half
is the hard one. Footage of somebody playing shows you the world, not their
keyboard.

VPT's answer, which Blockpal follows, is a second and much easier model that
reads the action **backwards** off two consecutive observations: if the view slid
forward and rotated left, they were holding W and moving the mouse left. It is
easy because it is allowed to see the future — it never has to *decide* anything,
only describe what already happened.

A small amount of properly-labelled play trains it, and it can then label an
arbitrary amount of observation-only footage that nobody captured button presses
for. Labels it isn't confident about are **discarded**, because a wrong label is
worse than no label — the policy has no way to know it was wrong.

> **Measured, not assumed.** Movement is recovered from two views about 99% of
> the time. The view turn lands in exactly the right bin about 80% of the time,
> and within one bin 99.6% — the misses are a 2° flick read as a 5° one, which
> makes no visible difference to how the bot moves.

---

## How it fits with the other brains

A companion running on PVT has a stack, and each layer yields to the one above:

| Priority | Brain | What it handles |
|---------:|-------|-----------------|
| 1 | A running script | An explicit order, from you or the AI |
| 2 | Survival reflexes | Drowning, fire, starving, stuck |
| 3 | **Combat** | Anything currently swinging at it |
| 4 | **The learned policy** | Moment-to-moment movement and reaction |
| 5 | The thinking brain | Deciding what to actually go and do |

The learned policy hands the tick back whenever it isn't confident enough
(`pvtConfidence`, 30% by default), and the model takes over. So the policy is the
reflexes and the model is the intent — which is roughly how it feels to play.

---

## Commands

| Command | Who | Effect |
|---------|-----|--------|
| `/ai pvt` / `/ai pvt status` | anyone | Policy, recordings, training progress |
| `/ai pvt watch on` / `off` | anyone | Opt **your own** play in or out |
| `/ai pvt record start` / `stop` | anyone | Start/stop a recording session by hand |
| `/ai pvt train` | ops | Train a policy from everything banked |
| `/ai pvt use on` / `off` | ops | Let companions act on what they learned |
| `/ai pvt clear` | ops | Delete every recording (keeps the trained policy) |

An outside AI app connected over [MCP](MCP-Server) gets `pvt_status` and
`pvt_train` too.

---

## Settings

All on **Settings → Behavior → Learning by watching (PVT)**.

| Setting | Default | What it does |
|---------|---------|--------------|
| `pvtEnabled` | on | The whole layer — recording, training, driving |
| `pvtAutoRecord` | on | Resume recording for players who already opted in |
| `pvtConfidence` | 0.30 | How sure the policy must be before it drives |
| `pvtHiddenSize` | 192 | Neurons per hidden layer |
| `pvtEpochs` | 24 | Passes over the recordings (it stops early anyway) |
| `pvtLearningRate` | 0.002 | Adam step size |
| `pvtMaxFrames` | 200000 | Frames kept on disk; oldest sessions drop first |

Switch a server to the learned policy with the **Thinking style** cycler
("Act on what it learned by watching") or `/ai pvt use on`.

---

## Honest limits

- **It needs data.** A few minutes of play is not enough to learn from. Expect to
  record a while before the policy does anything you'd call sensible, and expect
  it to imitate *what you did*, including your habits.
- **It only knows what it saw.** No demonstration of mining means no mining.
- **Lopsided recordings teach lopsided behaviour.** If 97% of your frames are
  standing still, a policy that always stands still scores well. `/ai pvt status`
  reports how lopsided the recordings are for exactly this reason.
- **The maths is verified; the feel is not.** The network, the data pipeline and
  the inverse dynamics model are covered by tests that run on every build. How
  good the resulting bot actually *looks* after an hour of somebody's play is not
  something that can be measured without a real machine and a real player.
