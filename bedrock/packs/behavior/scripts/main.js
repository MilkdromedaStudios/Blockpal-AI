// Blockpal for Bedrock — entry point.
// An AI companion (Ethan) for single-player Bedrock worlds: chat commands,
// follow/stay/guard modes, personalities, and an offline task planner.
//
// Everything here is defensive on purpose. This module runs at pack load, and
// a single uncaught throw at this level takes the whole add-on offline — which
// is exactly what happened in 1.0.0 (an experimental-only chat API was called
// unguarded, so no command of any kind worked). Each step is isolated now.
import { world, system } from "@minecraft/server";
import { TYPE_ID, ownerIdOf, claim, speak } from "./companion.js";
import { isValid, distance } from "./util.js";
import * as entry from "./entry.js";
import * as chat from "./chat.js";
import * as tasks from "./tasks.js";

entry.safe("command handlers", () => chat.setup());
entry.safe("task loop", () => tasks.setup());

// A companion placed with its spawn egg has no owner yet — the nearest
// player claims it automatically (interacting also claims, see chat.js).
entry.registerAutoClaim((ev) => {
  const bot = ev.entity;
  if (!bot || bot.typeId !== TYPE_ID) return;
  system.run(() => {
    if (!isValid(bot) || ownerIdOf(bot)) return;
    let nearest = null, nearestD = Infinity;
    for (const player of world.getPlayers()) {
      if (player.dimension.id !== bot.dimension.id) continue;
      const d = distance(player.location, bot.location);
      if (d < nearestD && d <= 16) { nearestD = d; nearest = player; }
    }
    if (nearest) {
      claim(bot, nearest);
      speak(bot, "greet", { owner: nearest.name });
    }
  });
});

// Say what actually came up. If something didn't register, this is where a
// player (or a bug report) finds out, instead of silence.
entry.report();
