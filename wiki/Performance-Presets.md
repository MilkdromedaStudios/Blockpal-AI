# Performance Presets

A preset auto-fills temperature, max tokens, the active-analysis toggle, and **all
Developer-tab fields** at once — a quick way to match the mod's load to your hardware.

Cycle it from the **Behavior** tab in `/ai menu`, or set it from chat:

```
/ai settings preset normal
/ai settings preset opus
/ai settings preset potato
```

| Preset | For | Effect |
|--------|-----|--------|
| **Normal** *(default)* | Most setups | Balanced execution speed, tokens and watchdog. |
| **Opus** | High-end machines | Faster step execution, more tokens, longer task watchdog, full AI activity. |
| **Potato** | Low-end machines | Slower step execution, fewer tokens, **active analysis disabled** to cut API/CPU load. |

The selected preset persists across sessions (`performancePreset` in the config).

## Related: Emergency FPS kill-switch

Independently of the preset, a client-side frame-rate watchdog samples FPS every tick.
If FPS stays below a preset-dependent floor (**Potato 3, Normal 4, Opus 5**) for about
3 seconds straight, it trips a mod-wide kill switch:

- The assistant entity stays in the world but does **nothing** — no planning, task
  execution, gear management or chat analysis.
- All players are notified.
- Re-enable with `/ai resume` (or `/ai enable`) once the framerate recovers. The
  watchdog re-arms automatically after recovery.
</content>
