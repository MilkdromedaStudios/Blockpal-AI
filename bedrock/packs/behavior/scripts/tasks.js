import { world, system, BlockPermutation } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { isValid, distance, runCmd, groundY, say } from "./util.js";
import {
  TYPE_ID, PROP_SCRIPT_FOLLOW, ownerIdOf, modeOf, personalityOf, speak, ownedCompanions
} from "./companion.js";
import { personaLine } from "./personality.js";

// entityId -> { bot, steps, i, label, quiet, startTick, state }
const tasks = new Map();

export function hasTask(bot) {
  return tasks.has(bot.id);
}

export function cancelTask(bot, silent) {
  const had = tasks.delete(bot.id);
  if (had && !silent) speak(bot, "stop");
  return had;
}

export function startTask(bot, plan) {
  tasks.set(bot.id, {
    bot,
    steps: plan.steps,
    i: 0,
    label: plan.label,
    quiet: !!plan.quiet,
    startTick: system.currentTick,
    state: {}
  });
  if (!plan.quiet) speak(bot, "task_start", { task: plan.label });
}

function finishTask(id, task) {
  tasks.delete(id);
  if (!task.quiet && isValid(task.bot)) speak(task.bot, "task_done", { task: task.label });
}

// Advance the bot one script-walk step toward (x, y, z).
// Returns true when it has arrived.
function stepToward(bot, target) {
  const loc = bot.location;
  const d = distance(loc, target);
  if (d < 1.6) return true;
  if (d > CONFIG.catchupDistance) {
    try { bot.teleport(target); } catch { }
    return true;
  }
  const step = Math.min(CONFIG.moveStep, d);
  const nx = loc.x + ((target.x - loc.x) / d) * step;
  const nz = loc.z + ((target.z - loc.z) / d) * step;
  let ny = loc.y;
  const gy = groundY(bot.dimension, Math.floor(nx), Math.floor(loc.y), Math.floor(nz));
  if (gy !== null && Math.abs(gy - loc.y) <= 4) ny = gy;
  try { bot.teleport({ x: nx, y: ny, z: nz }, { facingLocation: target }); }
  catch { try { bot.teleport({ x: nx, y: ny, z: nz }); } catch { } }
  return false;
}

function execPlace(bot, step) {
  try {
    const block = bot.dimension.getBlock({ x: step.x, y: step.y, z: step.z });
    if (block) {
      // Paving plans (floors, bridges) replace terrain; everything else
      // only fills air so it never overwrites existing builds.
      const canSet = block.isAir || block.isLiquid ||
        (step.replace && block.typeId !== "minecraft:bedrock");
      if (canSet && block.typeId !== step.block) {
        block.setPermutation(BlockPermutation.resolve(step.block));
      }
      return;
    }
  } catch { }
  runCmd(bot.dimension, `setblock ${step.x} ${step.y} ${step.z} ${step.block.replace("minecraft:", "")}`);
}

function execBreak(bot, step) {
  try {
    const block = bot.dimension.getBlock({ x: step.x, y: step.y, z: step.z });
    if (!block || block.isAir || block.isLiquid) return;
    if (block.typeId === "minecraft:bedrock") return;
    // 'destroy' drops the item like a real mining trip.
    if (runCmd(bot.dimension, `setblock ${step.x} ${step.y} ${step.z} air destroy`)) return;
    block.setPermutation(BlockPermutation.resolve("minecraft:air"));
  } catch { }
}

function execCollect(bot, task, step) {
  const st = task.state;
  if (st.collectEnd === undefined) st.collectEnd = system.currentTick + (step.seconds || 30) * 20;
  if (system.currentTick > st.collectEnd) return true;
  let nearest = null, nearestD = Infinity;
  try {
    for (const item of bot.dimension.getEntities({
      type: "minecraft:item",
      location: bot.location,
      maxDistance: CONFIG.collectRadius
    })) {
      const d = distance(item.location, bot.location);
      if (d < nearestD) { nearestD = d; nearest = item; }
    }
  } catch { }
  if (!nearest) return system.currentTick > st.collectEnd - 100 ? true : false;
  if (nearestD > 1.8) { stepToward(bot, nearest.location); return false; }
  try {
    const itemComp = nearest.getComponent("minecraft:item");
    const stack = itemComp && itemComp.itemStack;
    const inv = bot.getComponent("minecraft:inventory");
    const container = inv && inv.container;
    if (stack && container) {
      const leftover = container.addItem(stack);
      if (leftover === undefined) nearest.remove();
      else return true; // backpack full — stop collecting
    } else if (nearest) {
      nearest.remove(); // no inventory available; tidy up anyway
    }
  } catch { }
  return false;
}

function tickTask(id, task) {
  const bot = task.bot;
  if (!isValid(bot)) { tasks.delete(id); return; }

  // Runaway-task watchdog (mirrors the Java mod's maxTaskSeconds).
  if (system.currentTick - task.startTick > CONFIG.taskTimeoutSeconds * 20) {
    tasks.delete(id);
    speak(bot, "stop");
    return;
  }

  if (task.i >= task.steps.length) { finishTask(id, task); return; }
  const step = task.steps[task.i];

  switch (step.type) {
    case "place":
      execPlace(bot, step);
      task.i++;
      break;
    case "break":
      execBreak(bot, step);
      task.i++;
      break;
    case "move": {
      task.state.moveTicks = (task.state.moveTicks || 0) + 1;
      if (stepToward(bot, step) || task.state.moveTicks > 400) {
        task.state.moveTicks = 0;
        task.i++;
      }
      break;
    }
    case "collect":
      if (execCollect(bot, task, step)) { task.state.collectEnd = undefined; task.i++; }
      break;
    case "chat":
      if (step.raw !== undefined) say(bot, step.raw);
      else speak(bot, step.key, step.vars);
      task.i++;
      break;
    case "wait":
      if (step._left === undefined) step._left = step.ticks;
      if (--step._left <= 0) task.i++;
      break;
    case "jump":
      try { bot.applyImpulse({ x: 0, y: 0.42, z: 0 }); } catch { }
      task.i++;
      break;
    default:
      task.i++;
  }
}

// When native taming failed, the script walks the bot after its owner itself.
function tickScriptFollow() {
  for (const player of world.getPlayers()) {
    for (const bot of ownedCompanions(player)) {
      if (!bot.getDynamicProperty(PROP_SCRIPT_FOLLOW)) continue;
      if (modeOf(bot) !== "follow" || hasTask(bot)) continue;
      const d = distance(bot.location, player.location);
      if (d > CONFIG.catchupDistance) {
        try { bot.teleport(player.location); } catch { }
      } else if (d > 5) {
        // Called every 8 ticks, so take a bigger stride.
        for (let i = 0; i < 4; i++) {
          if (stepToward(bot, player.location)) break;
        }
      }
    }
  }
}

const lastHelpCall = new Map(); // entityId -> tick

export function setup() {
  system.runInterval(() => {
    for (const [id, task] of tasks) tickTask(id, task);
  }, 1);

  system.runInterval(tickScriptFollow, 8);

  // Call for help when badly hurt — a Blockpal signature move.
  try {
    world.afterEvents.entityHurt.subscribe((ev) => {
      const bot = ev.hurtEntity;
      if (!bot || bot.typeId !== TYPE_ID || !isValid(bot)) return;
      try {
        const h = bot.getComponent("minecraft:health");
        if (!h || h.currentValue > (h.effectiveMax ?? 40) * 0.3) return;
        const last = lastHelpCall.get(bot.id) || -10000;
        if (system.currentTick - last < 200) return;
        lastHelpCall.set(bot.id, system.currentTick);
        speak(bot, "help");
      } catch { }
    });
  } catch { }

  try {
    world.afterEvents.entityDie.subscribe((ev) => {
      const bot = ev.deadEntity;
      if (!bot || bot.typeId !== TYPE_ID) return;
      try {
        const name = bot.nameTag || CONFIG.defaultName;
        const line = personaLine(personalityOf(bot), "death").split("{name}").join(name);
        world.sendMessage(`§7${line}`);
        tasks.delete(bot.id);
      } catch { }
    });
  } catch { }
}
