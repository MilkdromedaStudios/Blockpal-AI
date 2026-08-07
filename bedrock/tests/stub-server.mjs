// A stand-in for @minecraft/server, faithful enough to run the real add-on.
//
// The important thing about this stub is what it LEAVES OUT. By default it
// models the *stable* API surface, which has no `chatSend` on either
// world.beforeEvents or world.afterEvents — those are experimental-only. That
// is the exact shape of a normal player's world, and the shape that made
// 1.0.0 fail to load at all.
//
// Set BLOCKPAL_TEST_BETA=1 to model a world with Beta APIs switched on.

const BETA = process.env.BLOCKPAL_TEST_BETA === "1";
/** Set BLOCKPAL_TEST_NO_CUSTOM_COMMANDS=1 to model an older runtime. */
const CUSTOM_COMMANDS = process.env.BLOCKPAL_TEST_NO_CUSTOM_COMMANDS !== "1";

export const log = { messages: [], warnings: [], commands: [], placed: [] };

class Signal {
  constructor(name) { this.name = name; this.handlers = []; }
  subscribe(fn) { this.handlers.push(fn); return fn; }
  unsubscribe(fn) { this.handlers = this.handlers.filter((h) => h !== fn); }
  emit(ev) { for (const h of [...this.handlers]) h(ev); }
  get count() { return this.handlers.length; }
}

let nextId = 1;

export class Entity {
  constructor(typeId, dimension, location) {
    this.typeId = typeId;
    this.id = `e${nextId++}`;
    this.dimension = dimension;
    this.location = { ...location };
    this.nameTag = "";
    this.props = new Map();
    this.events = [];
    this.removed = false;
    this.health = { currentValue: 40, effectiveMax: 40, defaultValue: 40 };
  }
  isValid() { return !this.removed; }
  getDynamicProperty(k) { return this.props.get(k); }
  setDynamicProperty(k, v) { this.props.set(k, v); }
  triggerEvent(e) { this.events.push(e); }
  remove() { this.removed = true; this.dimension.entities = this.dimension.entities.filter((x) => x !== this); }
  teleport(loc) { this.location = { ...loc }; }
  getComponent(id) {
    if (id === "minecraft:health") return this.health;
    if (id === "minecraft:tameable") return { tame: () => true, isTamed: true };
    if (id === "minecraft:inventory") return { container: { size: 0, getItem: () => undefined } };
    return undefined;
  }
  applyImpulse() { }
  setRotation() { }
}

export class Player extends Entity {
  constructor(name, dimension, location) {
    super("minecraft:player", dimension, location);
    this.name = name;
    this.received = [];
  }
  sendMessage(text) { this.received.push(text); log.messages.push(text); }
}

// A real (if tiny) mutable world, so block placement is observable. The add-on
// places blocks via block.setPermutation() and only falls back to /setblock, so
// an inert Block would make "did it build anything?" untestable.
class Block {
  constructor(dimension, loc) {
    this.dimension = dimension;
    this.location = { ...loc };
    this.x = loc.x; this.y = loc.y; this.z = loc.z;
  }
  get key() { return `${this.x},${this.y},${this.z}`; }
  get typeId() {
    const set = this.dimension.blocks.get(this.key);
    if (set !== undefined) return set;
    return this.y < 64 ? "minecraft:dirt" : "minecraft:air";   // dirt ground at y<64
  }
  get isAir() { return this.typeId === "minecraft:air"; }
  get isLiquid() { return this.typeId === "minecraft:water"; }
  setPermutation(permutation) {
    const typeId = permutation && permutation.typeId ? permutation.typeId : String(permutation);
    this.dimension.blocks.set(this.key, typeId);
    log.placed.push({ x: this.x, y: this.y, z: this.z, typeId });
  }
}

class Dimension {
  constructor(id) { this.id = id; this.entities = []; this.blocks = new Map(); }
  getEntities(opts) {
    let list = this.entities.filter((e) => !e.removed);
    if (opts && opts.type) list = list.filter((e) => e.typeId === opts.type);
    return list;
  }
  spawnEntity(typeId, location) {
    const e = new Entity(typeId, this, location);
    this.entities.push(e);
    world.afterEvents.entitySpawn.emit({ entity: e });
    return e;
  }
  getBlock(loc) { return new Block(this, loc); }
  runCommand(cmd) { log.commands.push(cmd); return { successCount: 1 }; }
  runCommandAsync(cmd) { log.commands.push(cmd); return Promise.resolve({ successCount: 1 }); }
}

const overworld = new Dimension("minecraft:overworld");

export const world = {
  dimensions: { "minecraft:overworld": overworld },
  players: [],
  getPlayers() { return this.players.filter((p) => !p.removed); },
  getDimension(id) { return id === "minecraft:overworld" ? overworld : new Dimension(id); },
  sendMessage(text) { log.messages.push(text); },
  afterEvents: {
    entitySpawn: new Signal("entitySpawn"),
    entityHurt: new Signal("entityHurt"),
    entityDie: new Signal("entityDie"),
    playerInteractWithEntity: new Signal("playerInteractWithEntity"),
    playerSpawn: new Signal("playerSpawn")
    // NOTE: no chatSend — it is experimental-only. Added below when BETA.
  },
  beforeEvents: {}
};

if (BETA) {
  world.afterEvents.chatSend = new Signal("chatSend(after)");
  world.beforeEvents.chatSend = new Signal("chatSend(before)");
}

const intervals = [];

export const system = {
  currentTick: 0,
  run(fn) { fn(); },                                  // immediate, for determinism
  runTimeout(fn) { fn(); },
  runInterval(fn, period) { intervals.push({ fn, period }); return intervals.length; },
  clearRun() { },
  afterEvents: { scriptEventReceive: new Signal("scriptEventReceive") },
  beforeEvents: {}
};

if (CUSTOM_COMMANDS) {
  system.beforeEvents.startup = new Signal("startup");
}

/** Registered custom commands, so tests can invoke them like a player would. */
export const customCommands = new Map();

export function fireStartup() {
  if (!system.beforeEvents.startup) return;
  system.beforeEvents.startup.emit({
    customCommandRegistry: {
      registerCommand(command, callback) { customCommands.set(command.name, { command, callback }); },
      registerEnum() { }
    }
  });
}

/** Advance the world by n ticks, running the add-on's interval callbacks. */
export function tick(n = 1) {
  for (let i = 0; i < n; i++) {
    system.currentTick++;
    for (const iv of intervals) {
      if (system.currentTick % iv.period === 0) {
        try { iv.fn(); } catch (err) { log.warnings.push(`interval threw: ${err}`); }
      }
    }
  }
}

export function addPlayer(name = "Tester") {
  const p = new Player(name, overworld, { x: 0, y: 64, z: 0 });
  overworld.entities.push(p);
  world.players.push(p);
  return p;
}

export function reset() {
  log.messages.length = 0;
  log.warnings.length = 0;
  log.commands.length = 0;
  log.placed.length = 0;
}

// Minimal stand-ins for the other named exports the add-on imports. An ESM
// import of a missing name is a hard load failure, so anything the scripts
// import must exist here too.
export const BlockPermutation = {
  resolve(typeId, states) { return { typeId, states }; }
};

export const EntityComponentTypes = {};
export const GameMode = { survival: "survival", creative: "creative" };
export const ItemStack = class ItemStack { constructor(typeId, amount) { this.typeId = typeId; this.amount = amount; } };

globalThis.console.warn = (...args) => { log.warnings.push(args.join(" ")); };
