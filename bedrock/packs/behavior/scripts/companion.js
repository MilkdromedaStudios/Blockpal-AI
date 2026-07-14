import { world, system } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { isValid, distance, say, info } from "./util.js";
import { personaLine, PERSONALITIES } from "./personality.js";

export const TYPE_ID = "blockpal:companion";
export const PROP_OWNER = "blockpal:owner";
export const PROP_OWNER_NAME = "blockpal:owner_name";
export const PROP_PERSONALITY = "blockpal:personality";
export const PROP_MODE = "blockpal:mode";
// Set when native taming failed and the script drives following itself.
export const PROP_SCRIPT_FOLLOW = "blockpal:script_follow";

export function ownerIdOf(bot) {
  try { return bot.getDynamicProperty(PROP_OWNER); } catch { return undefined; }
}

export function personalityOf(bot) {
  try { return bot.getDynamicProperty(PROP_PERSONALITY) || CONFIG.defaultPersonality; }
  catch { return CONFIG.defaultPersonality; }
}

export function modeOf(bot) {
  try { return bot.getDynamicProperty(PROP_MODE) || "follow"; } catch { return "follow"; }
}

// Personality-voiced line with {placeholder} substitution.
export function speak(bot, key, vars) {
  let line = personaLine(personalityOf(bot), key);
  if (vars) {
    for (const k of Object.keys(vars)) line = line.split(`{${k}}`).join(String(vars[k]));
  }
  say(bot, line);
}

export function ownedCompanions(player) {
  const out = [];
  try {
    for (const e of player.dimension.getEntities({ type: TYPE_ID })) {
      if (isValid(e) && ownerIdOf(e) === player.id) out.push(e);
    }
  } catch { }
  return out;
}

export function findOwned(player) {
  let best = null, bestD = Infinity;
  for (const e of ownedCompanions(player)) {
    const d = distance(e.location, player.location);
    if (d < bestD) { bestD = d; best = e; }
  }
  return best;
}

// Native taming gives us vanilla follow_owner pathfinding. API signatures
// differ across versions, so try both and fall back to script-side follow.
function tryTame(bot, player) {
  try {
    const t = bot.getComponent("minecraft:tameable");
    if (!t) return false;
    try { if (t.tame(player)) return true; } catch { }
    try { if (t.tame()) return true; } catch { }
    try { return !!t.isTamed; } catch { }
  } catch { }
  return false;
}

export function setMode(bot, mode, silent) {
  const event = mode === "follow" ? "blockpal:set_follow"
    : mode === "stay" ? "blockpal:set_stay"
      : "blockpal:set_guard";
  try { bot.triggerEvent(event); } catch { }
  try { bot.setDynamicProperty(PROP_MODE, mode); } catch { }
  if (!silent) speak(bot, mode);
}

export function claim(bot, player, name) {
  bot.setDynamicProperty(PROP_OWNER, player.id);
  bot.setDynamicProperty(PROP_OWNER_NAME, player.name);
  if (!bot.getDynamicProperty(PROP_PERSONALITY)) {
    bot.setDynamicProperty(PROP_PERSONALITY, CONFIG.defaultPersonality);
  }
  if (name) bot.nameTag = name;
  else if (!bot.nameTag) bot.nameTag = CONFIG.defaultName;
  if (!tryTame(bot, player)) bot.setDynamicProperty(PROP_SCRIPT_FOLLOW, true);
  setMode(bot, "follow", true);
}

export function summon(player, name) {
  const owned = ownedCompanions(player);
  if (owned.length >= CONFIG.maxCompanionsPerPlayer) {
    info(player, `You already have ${owned.length} companions in this dimension (max ${CONFIG.maxCompanionsPerPlayer}). Dismiss one first.`);
    return null;
  }
  let bot;
  try {
    bot = player.dimension.spawnEntity(TYPE_ID, player.location);
  } catch {
    info(player, "Couldn't spawn the companion here.");
    return null;
  }
  claim(bot, player, name || CONFIG.defaultName);
  speak(bot, "greet", { owner: player.name });
  return bot;
}

export function dismiss(bot) {
  speak(bot, "dismiss");
  system.run(() => { try { bot.remove(); } catch { } });
}

export function come(bot, player) {
  try { bot.teleport(player.location, { dimension: player.dimension }); }
  catch { try { bot.teleport(player.location); } catch { } }
  setMode(bot, "follow", true);
  speak(bot, "come");
}

export function locate(bot) {
  const l = bot.location;
  const dim = bot.dimension.id.replace("minecraft:", "").replace("_", " ");
  const pos = `${Math.floor(l.x)}, ${Math.floor(l.y)}, ${Math.floor(l.z)} (${dim})`;
  speak(bot, "where", { pos });
}

export function healthOf(bot) {
  try {
    const h = bot.getComponent("minecraft:health");
    if (h) return `${Math.round(h.currentValue)}/${Math.round(h.effectiveMax ?? h.defaultValue ?? 40)}`;
  } catch { }
  return "?";
}

export function setPersonality(bot, id) {
  if (!PERSONALITIES[id]) return false;
  bot.setDynamicProperty(PROP_PERSONALITY, id);
  return true;
}

export function listBots(player) {
  const owned = ownedCompanions(player);
  if (owned.length === 0) {
    info(player, "You have no companions in this dimension. Use '!ai summon' to call one.");
    return;
  }
  info(player, `Your companions (${owned.length}):`);
  for (const bot of owned) {
    const l = bot.location;
    info(player, ` §b${bot.nameTag || CONFIG.defaultName}§7 — ${modeOf(bot)}, ${personalityOf(bot)}, ` +
      `${Math.floor(l.x)} ${Math.floor(l.y)} ${Math.floor(l.z)}, ❤ ${healthOf(bot)}`);
  }
}

export function showInventory(player, bot) {
  try {
    const inv = bot.getComponent("minecraft:inventory");
    const c = inv && inv.container;
    if (!c) { info(player, "No inventory available."); return; }
    const items = [];
    for (let i = 0; i < c.size; i++) {
      const it = c.getItem(i);
      if (it) items.push(`${it.typeId.replace("minecraft:", "")} x${it.amount}`);
    }
    if (items.length === 0) info(player, `${bot.nameTag} is carrying nothing.`);
    else info(player, `${bot.nameTag} is carrying: ${items.join(", ")}`);
  } catch {
    info(player, "No inventory available.");
  }
}
