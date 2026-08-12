// Blockpal for Bedrock — tunables.
// There is no server config file on Bedrock; edit these and re-import the pack.
export const CONFIG = {
  defaultName: "Ethan",
  defaultPersonality: "friendly",
  maxCompanionsPerPlayer: 4,
  // Runaway-task watchdog, mirroring the Java mod's maxTaskSeconds default.
  taskTimeoutSeconds: 300,
  maxBuildBlocks: 400,
  maxMineBlocks: 216,
  // Script-driven walking (used when native pathfinding isn't available):
  moveStep: 0.35,        // blocks per tick
  collectRadius: 16,
  commandPrefix: "!ai"
};
