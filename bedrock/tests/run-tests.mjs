// Runs the real Blockpal Bedrock scripts against a stubbed @minecraft/server.
//
// The point of this harness is to catch the class of bug that shipped in 1.0.0:
// the add-on called an experimental-only API at module scope, the module failed
// to load, and every single command silently did nothing. Minecraft told nobody.
// Here, that is a failing test.
//
//   node --import ./register.mjs run-tests.mjs          (stable world)
//   BLOCKPAL_TEST_BETA=1 node --import ./register.mjs run-tests.mjs
//
// Use ./run.sh to run every configuration.
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPTS = join(HERE, "..", "packs", "behavior", "scripts");

const stub = await import(join(HERE, "stub-server.mjs"));
const BETA = process.env.BLOCKPAL_TEST_BETA === "1";
const NO_CUSTOM = process.env.BLOCKPAL_TEST_NO_CUSTOM_COMMANDS === "1";
const MODE = `${BETA ? "beta APIs on" : "stable APIs only"}${NO_CUSTOM ? ", no custom commands" : ""}`;

let pass = 0, fail = 0;
function ok(what, cond, detail) {
  if (cond) { pass++; console.log(`  ok   ${what}`); }
  else { fail++; console.log(`  FAIL ${what}${detail ? " — " + detail : ""}`); }
}

console.log(`\n=== Blockpal Bedrock add-on — ${MODE} ===`);

// ── 1. the add-on must LOAD. This is the 1.0.0 regression. ──────────────────
let loadError = null;
try {
  await import(pathToFileURL(join(SCRIPTS, "main.js")).href);
} catch (err) {
  loadError = err;
}
ok("the pack loads without throwing", loadError === null,
   loadError && (loadError.stack || String(loadError)));
if (loadError) {
  console.log(`\n${pass} passed, ${fail} failed\n`);
  process.exit(1);
}

const entry = await import(pathToFileURL(join(SCRIPTS, "entry.js")).href);
stub.fireStartup();

// ── 2. at least one command entry point must exist, always ──────────────────
ok("/scriptevent registered (the guaranteed path)", entry.status.scriptEvent);
ok("chat only registers when the API exists", entry.status.chat === BETA);
ok("custom /blockpal:ai command registered when supported",
   entry.status.customCommand === !NO_CUSTOM);
if (!NO_CUSTOM) {
  ok("the custom command reached the registry", stub.customCommands.has("blockpal:ai"));
}
ok("right-click handler registered", entry.status.interact);
ok("spawn-egg claiming registered", entry.status.autoClaim);
ok("there is always a way in", entry.howToUse().includes("blockpal:ai") || entry.status.chat);

// A missing experimental API is expected, and must be reported, not silent.
if (!BETA) {
  ok("the missing chat API is explained, not swallowed",
     entry.status.failures.some((f) => f.toLowerCase().includes("chat")));
}

// ── 3. commands must actually do things ─────────────────────────────────────
const player = stub.addPlayer("Tester");
const dimension = player.dimension;

function scriptEvent(message) {
  stub.reset();
  stub.system.afterEvents.scriptEventReceive.emit({
    id: "blockpal:ai", sourceEntity: player, message
  });
}
const bots = () => dimension.getEntities({ type: "blockpal:companion" });

scriptEvent("help");
ok("'help' prints the command list", player.received.some((m) => m.includes("!ai") || m.includes("summon")));

scriptEvent("summon");
ok("'summon' spawns a companion", bots().length === 1, `got ${bots().length}`);
const bot = bots()[0];
ok("the companion is named", !!bot.nameTag);
ok("the summoner owns it", bot.getDynamicProperty("blockpal:owner") === player.id);
ok("it greets you", stub.log.messages.some((m) => m.includes(bot.nameTag)));

for (const [cmd, mode] of [["stay", "stay"], ["guard", "guard"], ["follow", "follow"]]) {
  scriptEvent(cmd);
  ok(`'${cmd}' switches it to ${mode}`, bot.getDynamicProperty("blockpal:mode") === mode,
     `mode is ${bot.getDynamicProperty("blockpal:mode")}`);
  ok(`'${cmd}' fires the matching entity event`,
     bot.events.includes(`blockpal:set_${mode}`));
}

scriptEvent("where");
ok("'where' reports a position", stub.log.messages.some((m) => /\d+, ?-?\d+, ?-?\d+/.test(m)));

scriptEvent("bots");
ok("'bots' lists your companions", player.received.some((m) => m.includes(bot.nameTag)));

scriptEvent("name Bob");
ok("'name' renames it", bot.nameTag === "Bob");

scriptEvent("skin robot");
ok("'skin' triggers the skin event", bot.events.includes("blockpal:skin_robot"));

scriptEvent("personality grumpy");
ok("'personality' is stored", bot.getDynamicProperty("blockpal:personality") === "grumpy");

scriptEvent("personality nonsense-value");
ok("a bad personality is refused with the options",
   player.received.some((m) => m.toLowerCase().includes("unknown personality")));

scriptEvent("inv");
ok("'inv' answers", player.received.length > 0);

scriptEvent("say hello there");
ok("'say' speaks", stub.log.messages.some((m) => m.includes("hello there")));

// ── 4. the offline planner must produce real work ───────────────────────────
const tasks = await import(pathToFileURL(join(SCRIPTS, "tasks.js")).href);

scriptEvent("build a 5x5 floor of stone");
ok("a build request starts a task", tasks.hasTask(bot));
stub.tick(60);
const changed = stub.log.placed.length + stub.log.commands.length;
ok("the task actually places blocks", changed > 0, `${changed} block changes`);
ok("it places the block it was asked for",
   stub.log.placed.every((b) => b.typeId.includes("stone")) &&
   (stub.log.placed.length > 0 || stub.log.commands.some((c) => c.includes("stone"))),
   stub.log.placed.slice(0, 3).map((b) => b.typeId).join(","));

scriptEvent("stop");
ok("'stop' cancels the task", !tasks.hasTask(bot));

scriptEvent("mine a 3x3 hole");
ok("a mine request starts a task", tasks.hasTask(bot));
scriptEvent("stop");

scriptEvent("do a backflip into the sun");
ok("nonsense gets a reply rather than silence", stub.log.messages.length > 0);

// ── 5. the alternative entry points must work too ───────────────────────────
if (!NO_CUSTOM) {
  stub.reset();
  const registered = stub.customCommands.get("blockpal:ai");
  registered.callback({ sourceEntity: player }, ["where"]);
  ok("/blockpal:ai runs the same commands", stub.log.messages.length > 0);
}

if (BETA) {
  stub.reset();
  const before = stub.world.beforeEvents.chatSend;
  const ev = { sender: player, message: "!ai where", cancel: false };
  before.emit(ev);
  ok("!ai in chat is handled", stub.log.messages.length > 0);
  ok("!ai is hidden from public chat", ev.cancel === true);

  stub.reset();
  before.emit({ sender: player, message: `${bot.nameTag}, follow me`, cancel: false });
  ok("addressing it by name works", bot.getDynamicProperty("blockpal:mode") === "follow");
}

// ── 6. right-click behaviour ────────────────────────────────────────────────
stub.reset();
bot.setDynamicProperty("blockpal:mode", "follow");
stub.system.currentTick += 50;
stub.world.afterEvents.playerInteractWithEntity.emit({ player, target: bot });
ok("right-click toggles follow -> stay", bot.getDynamicProperty("blockpal:mode") === "stay");

// A companion someone else owns must refuse politely, not obey.
stub.reset();
const stranger = stub.addPlayer("Stranger");
stub.system.currentTick += 50;
stub.world.afterEvents.playerInteractWithEntity.emit({ player: stranger, target: bot });
ok("it refuses a stranger", bot.getDynamicProperty("blockpal:mode") === "stay");

// ── 7. nothing should have thrown along the way ─────────────────────────────
const thrown = stub.log.warnings.filter((w) => w.includes("threw") || w.includes("command failed"));
ok("no handler threw during the run", thrown.length === 0, thrown.join(" | "));

console.log(`\n${pass} passed, ${fail} failed\n`);
process.exit(fail > 0 ? 1 : 0);
