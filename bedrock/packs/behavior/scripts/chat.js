import { world, system } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { isValid, info, say } from "./util.js";
import {
  TYPE_ID, ownerIdOf, findOwned, summon, dismiss, come, locate, setMode,
  setPersonality, personalityOf, listBots, showInventory, speak, claim
} from "./companion.js";
import { PERSONALITY_IDS } from "./personality.js";
import { plan } from "./planner.js";
import { startTask, cancelTask, hasTask } from "./tasks.js";

const SKINS = {
  default: "blockpal:skin_default",
  robot: "blockpal:skin_robot",
  ember: "blockpal:skin_ember",
  void: "blockpal:skin_void"
};

function printHelp(player) {
  info(player, "§b— Blockpal Companion —");
  info(player, "!ai summon [name] — call a companion (default Ethan)");
  info(player, "!ai dismiss / come / follow / stay / guard / stop / where");
  info(player, "!ai name <name> · skin <default|robot|ember|void>");
  info(player, `!ai personality <${PERSONALITY_IDS.join("|")}>`);
  info(player, "!ai bots · inv · say <text>");
  info(player, "!ai <task> — e.g. 'build a 5x5 floor of stone', 'build a house',");
  info(player, "  'mine a 3x3 hole', 'dig down 10', 'collect items', 'build a bridge 12'");
  info(player, "Or just talk: 'Ethan, follow me' — right-click toggles follow/stay.");
}

// Natural-language orders: quick intents first (no planner needed), then the
// offline planner, mirroring the Java mod's quick-intent → LLM pipeline.
function handleInstruction(player, bot, text) {
  const t = text.toLowerCase().trim();
  if (t.length === 0) { speak(bot, "unknown"); return; }

  if (/\b(stop|cancel|halt|never ?mind)\b/.test(t)) {
    if (!cancelTask(bot)) speak(bot, "stop");
    setMode(bot, "follow", true);
    return;
  }
  if (/\b(?:wait|stand|stay)\s+(?:here|there|put|still)\b|\bstay\b/.test(t)) {
    cancelTask(bot, true);
    setMode(bot, "stay");
    return;
  }
  if (/\bfollow\b/.test(t)) {
    cancelTask(bot, true);
    setMode(bot, "follow");
    return;
  }
  if (/\bcome\b|\bover here\b|\bto me\b|\bget here\b/.test(t)) {
    cancelTask(bot, true);
    come(bot, player);
    return;
  }
  if (/\bwhere\b/.test(t)) { locate(bot); return; }
  if (/\b(guard|protect|defend|patrol|attack|kill|fight)\b/.test(t)) {
    cancelTask(bot, true);
    setMode(bot, "guard");
    return;
  }

  const p = plan(bot, player, text);
  if (p) {
    cancelTask(bot, true);
    startTask(bot, p);
  } else {
    speak(bot, "unknown");
  }
}

export function handleCommand(player, raw) {
  const text = raw.trim();
  const tokens = text.split(/\s+/).filter((s) => s.length > 0);
  const sub = (tokens[0] || "").toLowerCase();
  const rest = tokens.slice(1).join(" ");

  if (sub === "" || sub === "help") { printHelp(player); return; }
  if (sub === "summon") { summon(player, rest || undefined); return; }
  if (sub === "bots" || sub === "list") { listBots(player); return; }

  const bot = findOwned(player);
  if (!bot) {
    info(player, "You have no companion nearby. Use '!ai summon' first.");
    return;
  }

  switch (sub) {
    case "dismiss":
      cancelTask(bot, true);
      dismiss(bot);
      return;
    case "come":
      cancelTask(bot, true);
      come(bot, player);
      return;
    case "follow":
      cancelTask(bot, true);
      setMode(bot, "follow");
      return;
    case "stay":
      cancelTask(bot, true);
      setMode(bot, "stay");
      return;
    case "guard":
      cancelTask(bot, true);
      setMode(bot, "guard");
      return;
    case "stop":
      if (!cancelTask(bot)) speak(bot, "stop");
      return;
    case "where":
    case "locate":
      locate(bot);
      return;
    case "inv":
    case "inventory":
      showInventory(player, bot);
      return;
    case "name":
      if (!rest) { info(player, "Usage: !ai name <new name>"); return; }
      bot.nameTag = rest;
      speak(bot, "okay");
      return;
    case "skin": {
      const id = rest.toLowerCase();
      const event = SKINS[id];
      if (!event) { info(player, `Skins: ${Object.keys(SKINS).join(", ")}`); return; }
      try { bot.triggerEvent(event); } catch { }
      speak(bot, "okay");
      return;
    }
    case "personality": {
      const id = rest.toLowerCase();
      if (!id) {
        const current = personalityOf(bot);
        info(player, "Personalities: " + PERSONALITY_IDS.map((p) => (p === current ? `§b${p}§7` : p)).join(", "));
        return;
      }
      if (setPersonality(bot, id)) speak(bot, "okay");
      else info(player, `Unknown personality. Options: ${PERSONALITY_IDS.join(", ")}`);
      return;
    }
    case "say":
      if (rest) say(bot, rest);
      return;
    default:
      handleInstruction(player, bot, text);
  }
}

// "Ethan, follow me" / "Ethan: follow me" / "Ethan follow me"
function findAddressed(player, message) {
  const lower = message.toLowerCase();
  let best = null;
  try {
    for (const bot of player.dimension.getEntities({ type: TYPE_ID })) {
      if (!isValid(bot)) continue;
      const name = (bot.nameTag || CONFIG.defaultName).toLowerCase();
      if (!lower.startsWith(name)) continue;
      const after = message.slice(name.length);
      if (after.length > 0 && !/^[\s,:;!.]/.test(after)) continue;
      if (!best || name.length > best.name.length) {
        best = { bot, name, rest: after.replace(/^[\s,:;!.]+/, "") };
      }
    }
  } catch { }
  return best;
}

function handleChatMessage(player, message) {
  const trimmed = message.trim();
  const lower = trimmed.toLowerCase();
  const prefix = CONFIG.commandPrefix.toLowerCase();

  if (lower === prefix || lower.startsWith(prefix + " ")) {
    handleCommand(player, trimmed.slice(prefix.length));
    return true; // suppress from public chat when possible
  }

  const addressed = findAddressed(player, trimmed);
  if (addressed && addressed.rest.length > 0) {
    if (ownerIdOf(addressed.bot) !== player.id) speak(addressed.bot, "refuse");
    else handleInstruction(player, addressed.bot, addressed.rest);
  }
  return false; // normal chat stays visible
}

const lastInteract = new Map(); // entityId -> tick

export function setup() {
  // Cancelable chat needs beforeEvents.chatSend; fall back to afterEvents
  // (commands then stay visible in chat — harmless in single-player).
  const before = world.beforeEvents && world.beforeEvents.chatSend;
  if (before && typeof before.subscribe === "function") {
    before.subscribe((ev) => {
      const { sender, message } = ev;
      const lower = message.trim().toLowerCase();
      const prefix = CONFIG.commandPrefix.toLowerCase();
      if (lower === prefix || lower.startsWith(prefix + " ")) ev.cancel = true;
      system.run(() => handleChatMessage(sender, message));
    });
  } else {
    world.afterEvents.chatSend.subscribe((ev) => {
      const { sender, message } = ev;
      system.run(() => handleChatMessage(sender, message));
    });
  }

  // /scriptevent blockpal:ai <command> — alternative entry point.
  try {
    system.afterEvents.scriptEventReceive.subscribe((ev) => {
      if (ev.id !== "blockpal:ai") return;
      const player = ev.sourceEntity;
      if (!player || player.typeId !== "minecraft:player") return;
      handleCommand(player, ev.message || "");
    });
  } catch { }

  // Right-click: claim an unowned companion, else toggle follow/stay.
  try {
    world.afterEvents.playerInteractWithEntity.subscribe((ev) => {
      const bot = ev.target;
      if (!bot || bot.typeId !== TYPE_ID || !isValid(bot)) return;
      const last = lastInteract.get(bot.id) || -100;
      if (system.currentTick - last < 10) return;
      lastInteract.set(bot.id, system.currentTick);
      const player = ev.player;
      const owner = ownerIdOf(bot);
      if (!owner) {
        claim(bot, player);
        speak(bot, "greet", { owner: player.name });
        return;
      }
      if (owner !== player.id) { speak(bot, "refuse"); return; }
      if (hasTask(bot)) return; // don't yank it off a job with a misclick
      const next = (bot.getDynamicProperty("blockpal:mode") || "follow") === "follow" ? "stay" : "follow";
      setMode(bot, next);
    });
  } catch { }

  // One-time hint for new players.
  try {
    world.afterEvents.playerSpawn.subscribe((ev) => {
      if (!ev.initialSpawn) return;
      const player = ev.player;
      try {
        if (player.getDynamicProperty("blockpal:hinted")) return;
        player.setDynamicProperty("blockpal:hinted", true);
        info(player, "§bBlockpal§7 is active — type §b!ai help§7 to meet your AI companion.");
      } catch { }
    });
  } catch { }
}
