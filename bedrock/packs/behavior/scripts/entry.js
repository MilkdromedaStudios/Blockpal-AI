// Blockpal for Bedrock — how players reach the companion.
//
// THE BUG THIS FILE EXISTS TO PREVENT
// -----------------------------------
// 1.0.0 registered chat handling like this:
//
//     const before = world.beforeEvents && world.beforeEvents.chatSend;
//     if (before) { before.subscribe(...); }
//     else { world.afterEvents.chatSend.subscribe(...); }   // <- unguarded
//
// Both `chatSend` events are EXPERIMENTAL-only in @minecraft/server (they sit
// behind the "Beta APIs" toggle — see the moniker blocks in Microsoft's
// WorldAfterEvents / WorldBeforeEvents reference). On a normal world both are
// `undefined`, so that else-branch threw a TypeError... at module scope. One
// throw there and the WHOLE script module fails to load, which is why not a
// single command worked — not chat, not /scriptevent, not right-click.
//
// So: every entry point is registered independently, inside its own guard, and
// the ones that always exist go first. A missing API can now cost us that one
// feature and nothing else. `report()` then tells the player what is actually
// live, so "it doesn't respond" is never a mystery again.
import { world, system } from "@minecraft/server";

/** What registered, and what didn't — surfaced in-game and to the content log. */
export const status = {
  scriptEvent: false,
  customCommand: false,
  chat: false,
  interact: false,
  autoClaim: false,
  joinHint: false,
  failures: []
};

/**
 * Runs one registration in isolation. A failure is recorded and logged, never
 * thrown — losing one entry point must never take the add-on down with it.
 */
export function safe(label, fn) {
  try {
    fn();
    return true;
  } catch (err) {
    const message = (err && (err.message || err.toString())) || "unknown error";
    status.failures.push(`${label}: ${message}`);
    try { console.warn(`[Blockpal] '${label}' unavailable — ${message}`); } catch { }
    return false;
  }
}

/**
 * `/scriptevent blockpal:ai <command>` — the path that always works.
 * `scriptEventReceive` is stable API, so this is the guaranteed way in and is
 * deliberately registered before anything that might not exist.
 */
export function registerScriptEvent(handleCommand) {
  status.scriptEvent = safe("scriptevent", () => {
    system.afterEvents.scriptEventReceive.subscribe((ev) => {
      if (ev.id !== "blockpal:ai") return;
      const player = ev.sourceEntity;
      if (!player || player.typeId !== "minecraft:player") return;
      try { handleCommand(player, ev.message || ""); } catch (err) {
        try { console.warn(`[Blockpal] command failed: ${err}`); } catch { }
      }
    });
  });
}

/**
 * A real `/blockpal:ai <args>` slash command with autocomplete, on versions
 * whose @minecraft/server exposes the (stable) custom-command registry. Older
 * runtimes simply don't have `system.beforeEvents.startup`, so this no-ops and
 * `/scriptevent` remains the way in.
 */
export function registerCustomCommand(handleCommand) {
  status.customCommand = safe("custom command", () => {
    const startup = system.beforeEvents && system.beforeEvents.startup;
    if (!startup || typeof startup.subscribe !== "function") {
      throw new Error("this version has no custom-command registry");
    }
    startup.subscribe((ev) => {
      const registry = ev.customCommandRegistry;
      if (!registry || typeof registry.registerCommand !== "function") return;
      registry.registerCommand(
        {
          name: "blockpal:ai",
          description: "Command your Blockpal companion",
          permissionLevel: 0,          // CommandPermissionLevel.Any
          optionalParameters: [{ name: "args", type: "String" }]
        },
        (origin, args) => {
          const player = origin && (origin.sourceEntity || origin.initiator);
          if (!player || player.typeId !== "minecraft:player") return undefined;
          const text = (args && args[0]) || "";
          // Command callbacks run in read-only mode, so do the work next tick.
          system.run(() => {
            try { handleCommand(player, text); } catch (err) {
              try { console.warn(`[Blockpal] command failed: ${err}`); } catch { }
            }
          });
          return undefined;
        }
      );
    });
  });
}

/**
 * `!ai …` typed straight into chat. Lovely when it works, but chatSend is an
 * experimental API — this only registers when the world has Beta APIs on.
 */
export function registerChat(handleMessage) {
  const before = safe("chat (before)", () => {
    const signal = world.beforeEvents && world.beforeEvents.chatSend;
    if (!signal || typeof signal.subscribe !== "function") {
      throw new Error("chatSend is an experimental API (enable Beta APIs to type !ai in chat)");
    }
    signal.subscribe((ev) => {
      const sender = ev.sender;
      const message = ev.message;
      if (handleMessage.claims(message)) ev.cancel = true;
      system.run(() => handleMessage.run(sender, message));
    });
  });
  if (before) { status.chat = true; return; }

  // Some builds expose only the after-event. Worth one guarded attempt.
  status.chat = safe("chat (after)", () => {
    const signal = world.afterEvents && world.afterEvents.chatSend;
    if (!signal || typeof signal.subscribe !== "function") {
      throw new Error("chatSend is an experimental API (enable Beta APIs to type !ai in chat)");
    }
    signal.subscribe((ev) => {
      const sender = ev.sender;
      const message = ev.message;
      system.run(() => handleMessage.run(sender, message));
    });
  });
}

export function registerInteract(onInteract) {
  status.interact = safe("right-click", () => {
    world.afterEvents.playerInteractWithEntity.subscribe(onInteract);
  });
}

export function registerAutoClaim(onSpawn) {
  status.autoClaim = safe("spawn-egg claiming", () => {
    world.afterEvents.entitySpawn.subscribe(onSpawn);
  });
}

export function registerJoinHint(onJoin) {
  status.joinHint = safe("join hint", () => {
    world.afterEvents.playerSpawn.subscribe(onJoin);
  });
}

/** The one line every player should see: how do I actually talk to this thing? */
export function howToUse() {
  if (status.customCommand) return "Type §b/blockpal:ai help§7 to begin.";
  if (status.chat) return "Type §b!ai help§7 in chat to begin.";
  if (status.scriptEvent) return "Run §b/scriptevent blockpal:ai help§7 to begin (cheats must be on).";
  return "§cNo command entry point registered — see the content log.";
}

/**
 * Logs what came up, and tells players once they're in the world. Without this,
 * a missing API is invisible and the add-on just looks broken.
 */
export function report() {
  const live = [];
  if (status.customCommand) live.push("/blockpal:ai");
  if (status.scriptEvent) live.push("/scriptevent blockpal:ai");
  if (status.chat) live.push("!ai chat");
  const summary = live.length > 0 ? live.join(", ") : "NONE";
  try {
    console.warn(`[Blockpal] Bedrock companion loaded. Commands: ${summary}.`);
    for (const failure of status.failures) console.warn(`[Blockpal]   skipped ${failure}`);
  } catch { }
  return summary;
}
