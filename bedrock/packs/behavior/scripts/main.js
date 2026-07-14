// Blockpal for Bedrock — entry point.
// An AI companion (Ethan) for single-player Bedrock worlds: chat commands,
// follow/stay/guard modes, personalities, and an offline task planner.
import { world, system } from "@minecraft/server";
import { TYPE_ID, ownerIdOf, claim, speak } from "./companion.js";
import { isValid, distance } from "./util.js";
import * as chat from "./chat.js";
import * as tasks from "./tasks.js";

chat.setup();
tasks.setup();

// A companion placed with its spawn egg has no owner yet — the nearest
// player claims it automatically (interacting also claims, see chat.js).
try {
  world.afterEvents.entitySpawn.subscribe((ev) => {
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
} catch { }

console.warn("[Blockpal] Bedrock companion loaded — type '!ai help' in chat.");
