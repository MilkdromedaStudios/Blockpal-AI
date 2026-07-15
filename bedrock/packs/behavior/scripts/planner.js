import { CONFIG } from "./config.js";

// Offline natural-language planner.
//
// The Java mod sends tasks to an LLM over HTTP; Bedrock's Script API has no
// network access in single-player worlds (@minecraft/server-net is dedicated-
// server-only), so this recreates the planning layer on-device: instructions
// are parsed into the same kind of step-by-step action plan the LLM would
// have produced (place/break one block per tick, move, collect, chat, wait).

const BLOCKS = {
  "stone bricks": "minecraft:stone_bricks",
  "oak planks": "minecraft:oak_planks",
  "spruce planks": "minecraft:spruce_planks",
  "birch planks": "minecraft:birch_planks",
  "oak log": "minecraft:oak_log",
  "white wool": "minecraft:white_wool",
  "red wool": "minecraft:red_wool",
  "blue wool": "minecraft:blue_wool",
  "sea lantern": "minecraft:sea_lantern",
  "cobblestone": "minecraft:cobblestone",
  "cobble": "minecraft:cobblestone",
  "stone": "minecraft:stone",
  "dirt": "minecraft:dirt",
  "planks": "minecraft:oak_planks",
  "wood": "minecraft:oak_planks",
  "log": "minecraft:oak_log",
  "logs": "minecraft:oak_log",
  "glass": "minecraft:glass",
  "sand": "minecraft:sand",
  "gravel": "minecraft:gravel",
  "bricks": "minecraft:bricks",
  "brick": "minecraft:bricks",
  "sandstone": "minecraft:sandstone",
  "deepslate": "minecraft:deepslate",
  "andesite": "minecraft:andesite",
  "granite": "minecraft:granite",
  "diorite": "minecraft:diorite",
  "obsidian": "minecraft:obsidian",
  "netherrack": "minecraft:netherrack",
  "quartz": "minecraft:quartz_block",
  "wool": "minecraft:white_wool",
  "glowstone": "minecraft:glowstone"
};

function blockOf(text, fallback) {
  for (const key of Object.keys(BLOCKS)) {
    if (key.includes(" ") && text.includes(key)) return BLOCKS[key];
  }
  for (const key of Object.keys(BLOCKS)) {
    if (new RegExp(`\\b${key}\\b`).test(text)) return BLOCKS[key];
  }
  return fallback;
}

// "5x5", "5 by 5", "5 x 5 x 3", "6*3"
function parseDims(text) {
  const m = text.match(/(\d+)\s*(?:x|by|\*)\s*(\d+)(?:\s*(?:x|by|\*)\s*(\d+))?/i);
  if (m) return [parseInt(m[1], 10), parseInt(m[2], 10), m[3] ? parseInt(m[3], 10) : undefined];
  const s = text.match(/\b(\d+)\b/);
  if (s) return [parseInt(s[1], 10), undefined, undefined];
  return [undefined, undefined, undefined];
}

function clampDim(n, def, max) {
  if (!n || !Number.isFinite(n)) return def;
  return Math.max(1, Math.min(max, n));
}

function facing(bot) {
  let yaw = 0;
  try { yaw = bot.getRotation().y; } catch { }
  const a = ((yaw % 360) + 360) % 360;
  if (a >= 315 || a < 45) return { dx: 0, dz: 1 };   // south
  if (a < 135) return { dx: -1, dz: 0 };             // west
  if (a < 225) return { dx: 0, dz: -1 };             // north
  return { dx: 1, dz: 0 };                           // east
}

function basePos(bot) {
  const l = bot.location;
  return { x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) };
}

function capSteps(steps, max) {
  return steps.length > max ? steps.slice(0, max) : steps;
}

function planFloor(bot, text) {
  const [a, b] = parseDims(text);
  const w = clampDim(a, 5, 21), l = clampDim(b ?? a, 5, 21);
  const block = blockOf(text, "minecraft:cobblestone");
  const base = basePos(bot);
  const y = base.y - 1;
  const steps = [];
  const x0 = base.x - Math.floor(w / 2), z0 = base.z - Math.floor(l / 2);
  for (let i = 0; i < w; i++) {
    for (let j = 0; j < l; j++) {
      steps.push({ type: "place", x: x0 + i, y, z: z0 + j, block, replace: true });
    }
  }
  return { label: `a ${w}x${l} floor`, steps: capSteps(steps, CONFIG.maxBuildBlocks) };
}

function planWall(bot, text) {
  const [a, b] = parseDims(text);
  const len = clampDim(a, 6, 24), h = clampDim(b, 3, 12);
  const block = blockOf(text, "minecraft:cobblestone");
  const base = basePos(bot);
  const f = facing(bot);
  const p = { dx: -f.dz, dz: f.dx }; // perpendicular — wall runs across the view
  const cx = base.x + f.dx * 2, cz = base.z + f.dz * 2;
  const x0 = cx - p.dx * Math.floor(len / 2), z0 = cz - p.dz * Math.floor(len / 2);
  const steps = [];
  for (let j = 0; j < h; j++) {
    for (let i = 0; i < len; i++) {
      steps.push({ type: "place", x: x0 + p.dx * i, y: base.y + j, z: z0 + p.dz * i, block });
    }
  }
  return { label: `a wall ${len} long, ${h} high`, steps: capSteps(steps, CONFIG.maxBuildBlocks) };
}

function planTower(bot, text) {
  const [a] = parseDims(text);
  const n = clampDim(a, 6, 24);
  const block = blockOf(text, "minecraft:cobblestone");
  const base = basePos(bot);
  const f = facing(bot);
  const x = base.x + f.dx * 2, z = base.z + f.dz * 2;
  const steps = [];
  for (let j = 0; j < n; j++) steps.push({ type: "place", x, y: base.y + j, z, block });
  return { label: `a ${n}-block tower`, steps };
}

function planBridge(bot, text) {
  const [a] = parseDims(text);
  const n = clampDim(a, 12, 48);
  const block = blockOf(text, "minecraft:cobblestone");
  const base = basePos(bot);
  const f = facing(bot);
  const p = { dx: -f.dz, dz: f.dx };
  const steps = [];
  for (let i = 1; i <= n; i++) {
    for (let w = -1; w <= 1; w++) {
      steps.push({
        type: "place",
        x: base.x + f.dx * i + p.dx * w,
        y: base.y - 1,
        z: base.z + f.dz * i + p.dz * w,
        block,
        replace: true
      });
    }
  }
  return { label: `a ${n}-block bridge`, steps: capSteps(steps, CONFIG.maxBuildBlocks) };
}

function planHouse(bot, text) {
  const wallBlock = blockOf(text, "minecraft:cobblestone");
  const roofBlock = "minecraft:oak_planks";
  const base = basePos(bot);
  const f = facing(bot);
  // 5x5 hut, front wall two blocks ahead of the bot, doorway facing it.
  const cx = base.x + f.dx * 4, cz = base.z + f.dz * 4;
  const doorX = cx - f.dx * 2, doorZ = cz - f.dz * 2;
  const steps = [];
  for (let j = 0; j < 3; j++) {
    for (let i = -2; i <= 2; i++) {
      for (let k = -2; k <= 2; k++) {
        if (Math.abs(i) !== 2 && Math.abs(k) !== 2) continue; // walls only
        const x = cx + i, z = cz + k;
        if (x === doorX && z === doorZ && j < 2) continue;    // doorway
        steps.push({ type: "place", x, y: base.y + j, z, block: wallBlock });
      }
    }
  }
  for (let i = -2; i <= 2; i++) {
    for (let k = -2; k <= 2; k++) {
      steps.push({ type: "place", x: cx + i, y: base.y + 3, z: cz + k, block: roofBlock });
    }
  }
  return { label: "a little house", steps: capSteps(steps, CONFIG.maxBuildBlocks) };
}

function planMine(bot, text) {
  const down = text.match(/\b(?:down|deep)\b/);
  if (down) {
    const [a] = parseDims(text);
    const n = clampDim(a, 8, 16);
    const base = basePos(bot);
    const f = facing(bot);
    const steps = [];
    // Staircase forward-and-down: three blocks of headroom per step.
    for (let i = 1; i <= n; i++) {
      const x = base.x + f.dx * i, z = base.z + f.dz * i;
      for (let h = 0; h < 3; h++) steps.push({ type: "break", x, y: base.y - i + h, z });
    }
    return { label: `a staircase ${n} down`, steps: capSteps(steps, CONFIG.maxMineBlocks) };
  }
  const [a, b, c] = parseDims(text);
  const w = clampDim(a, 3, 6), d = clampDim(b ?? a, 3, 6), h = clampDim(c, 3, 6);
  const base = basePos(bot);
  const f = facing(bot);
  const p = { dx: -f.dz, dz: f.dx };
  const steps = [];
  // Top-down, near-to-far, so nothing collapses onto unmined rows.
  for (let j = h - 1; j >= 0; j--) {
    for (let i = 1; i <= d; i++) {
      for (let k = -Math.floor(w / 2); k <= Math.floor((w - 1) / 2); k++) {
        steps.push({
          type: "break",
          x: base.x + f.dx * i + p.dx * k,
          y: base.y + j - 1,
          z: base.z + f.dz * i + p.dz * k
        });
      }
    }
  }
  return { label: `mining a ${w}x${d}x${h} area`, steps: capSteps(steps, CONFIG.maxMineBlocks) };
}

export function plan(bot, player, rawText) {
  const text = rawText.toLowerCase().trim();

  const sayMatch = text.match(/^say\s+(.+)/);
  if (sayMatch) {
    return { label: "say", quiet: true, steps: [{ type: "chat", raw: rawText.trim().slice(4) }] };
  }
  if (/^(hi|hello|hey|yo|hiya|howdy)\b/.test(text)) {
    return { label: "greeting", quiet: true, steps: [{ type: "chat", key: "greet", vars: { owner: player.name } }] };
  }
  if (/\bjump\b/.test(text)) {
    return { label: "jump", quiet: true, steps: [{ type: "jump" }, { type: "wait", ticks: 10 }, { type: "jump" }] };
  }
  const waitMatch = text.match(/\bwait\b.*?(\d+)\s*(?:sec|second)/);
  if (waitMatch) {
    const s = Math.min(120, parseInt(waitMatch[1], 10) || 5);
    return { label: `waiting ${s}s`, steps: [{ type: "wait", ticks: s * 20 }] };
  }
  if (/\b(collect|pick\s*up|gather|grab)\b/.test(text)) {
    return { label: "collecting nearby items", steps: [{ type: "collect", seconds: 45 }] };
  }
  if (/\b(mine|dig|excavate|quarry)\b/.test(text)) return planMine(bot, text);
  if (/\b(build|make|place|construct|create)\b/.test(text) || /\b(floor|wall|tower|bridge|house|hut|platform|pillar)\b/.test(text)) {
    if (/\b(house|hut|shelter|cabin|home)\b/.test(text)) return planHouse(bot, text);
    if (/\b(wall|fence|barrier)\b/.test(text)) return planWall(bot, text);
    if (/\b(tower|pillar|column)\b/.test(text)) return planTower(bot, text);
    if (/\b(bridge|walkway|path)\b/.test(text)) return planBridge(bot, text);
    if (/\b(floor|platform|pad|foundation)\b/.test(text)) return planFloor(bot, text);
    return planFloor(bot, text); // generic "build ..." default
  }
  return null;
}
