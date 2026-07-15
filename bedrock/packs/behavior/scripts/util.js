import { world } from "@minecraft/server";

// Entity.isValid is a method on 1.x API surfaces and a property on 2.x —
// this add-on targets 1.17.0 but tolerates both.
export function isValid(entity) {
  try {
    if (!entity) return false;
    return typeof entity.isValid === "function" ? entity.isValid() : !!entity.isValid;
  } catch {
    return false;
  }
}

export function distance(a, b) {
  const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// All companion dialogue goes through here — Name: "line", like the Java mod.
export function say(entity, line) {
  const name = (entity && entity.nameTag) || "Ethan";
  world.sendMessage(`§b${name}§r: "${line}"`);
}

export function info(player, text) {
  try { player.sendMessage(`§7${text}`); } catch { }
}

export function runCmd(dimension, cmd) {
  try { dimension.runCommand(cmd); return true; } catch { }
  try { dimension.runCommandAsync(cmd); return true; } catch { }
  return false;
}

// Find a standable Y near (x, z) around baseY: solid below, two air blocks.
// Returns null when nothing suitable is loaded/found.
export function groundY(dimension, x, baseY, z) {
  try {
    for (let dy = 0; dy <= 6; dy++) {
      const candidates = dy === 0 ? [baseY] : [baseY - dy, baseY + dy];
      for (const y of candidates) {
        const below = dimension.getBlock({ x, y: y - 1, z });
        const at = dimension.getBlock({ x, y, z });
        const above = dimension.getBlock({ x, y: y + 1, z });
        if (below && at && above && !below.isAir && at.isAir && above.isAir) return y;
      }
    }
  } catch { /* unloaded chunks — caller keeps current Y */ }
  return null;
}
