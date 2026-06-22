# Developer Menu

The **Developer** tab in `/ai menu` exposes three low-level settings that are hidden
from the normal tabs because misconfiguring them can cause **lag spikes, server
freezes, or crashes**. Each field shows an inline red warning in-game. Only change them
if you understand what each one does.

They're on the **Developer** tab of the panel (`/ai menu`); a
[performance preset](Performance-Presets) can also fill them in for you.

> **TL;DR — what the Developer menu does:** it lets you trade safety for speed/behavior.
> Lower delays = faster but riskier; disabling the watchdog = no runaway protection;
> lowering flee health = braver but more likely to die. The defaults are safe; the
> Developer tab is for power users who accept the trade-offs.

---

## Action tick delay (`actionTickDelay`)

**Default:** `2` &nbsp;|&nbsp; **Range:** 0 – 40 &nbsp;|&nbsp; **Type:** integer (ticks)

How many server ticks the assistant waits between executing consecutive action steps.

| Value | Effect |
|-------|--------|
| `0` | Steps execute every tick — maximum speed, but can freeze the server if a step is expensive (e.g. large `USE_BLOCK` chains). |
| `2` | Default. One step roughly every 2 ticks (~10 steps/second). |
| `20` | One step per second — visually slow but very safe for debugging. |
| `40` | One step every 2 seconds — useful when watching exactly what the AI is doing. |

**Risk at 0:** If the current plan contains many `PLACE_BLOCK` or `BREAK_BLOCK` steps,
running them every tick can saturate the server's block-update queue and trigger the
Minecraft watchdog (which kills the JVM).

---

## Task watchdog timeout (`maxTaskSeconds`)

**Default:** `300` &nbsp;|&nbsp; **Range:** 0 – 3600 &nbsp;|&nbsp; **Type:** integer (seconds)

Maximum wall-clock seconds a single task may run before the watchdog cancels it and
returns the assistant to FOLLOWING mode.

| Value | Effect |
|-------|--------|
| `0` | Watchdog **disabled** — tasks run forever. A stuck looping plan will never self-cancel. |
| `300` | Default (5 minutes). Catches most runaway tasks without interrupting legitimate long builds. |
| `600` | 10 minutes — reasonable for very large structures. |
| `3600` | 1 hour — effectively disabled for practical purposes. |

**Risk at 0:** A bug in the LLM plan (e.g. a `MOVE_TO` the assistant can never reach)
will loop indefinitely, consuming CPU and keeping the entity stuck forever. There is no
automatic recovery.

---

## Flee health threshold (`fleeHealthPercent`)

**Default:** `0.25` &nbsp;|&nbsp; **Range:** 0.0 – 1.0 &nbsp;|&nbsp; **Type:** decimal (fraction of max health)

The fraction of maximum health at which the assistant abandons combat and retreats.

| Value | Effect |
|-------|--------|
| `0.0` | **Never flees.** Fights to the death regardless of health. On hard difficulty it will die to any sufficiently strong mob. |
| `0.25` | Default. Retreats at 25% health (5 HP on a 20 HP pool). |
| `0.5` | Retreats at half health — very cautious, avoids most combat entirely. |
| `1.0` | Retreats the moment any damage is taken. Essentially pacifist mode. |

**Risk at 0:** The entity will never disengage from a fight. Against bosses or large
groups it will die, dropping all carried items. If respawn is disabled it will be gone
permanently.

---

## Interactions between settings

- A low `actionTickDelay` **combined with** a disabled watchdog (`maxTaskSeconds = 0`)
  is the most dangerous combination: plans execute at maximum speed with no timeout
  safety net.
- Setting `fleeHealthPercent = 0` while the assistant is in a dangerous biome (Nether,
  End) will almost certainly result in its death.

---

## Resetting to defaults

The safe defaults are **action tick delay 8**, **task watchdog 300 s**, and **flee
health 0.25**. Set them back on the **Developer** tab, or just pick the **Normal**
[performance preset](Performance-Presets) on the Behavior tab to restore all three at
once.

Or open `/ai menu`, switch to the **Developer** tab, and drag the sliders back to their
default positions before clicking **Save**.
</content>
