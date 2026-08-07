// Resolves the bare specifier "@minecraft/server" to the test stub, so the
// add-on's real, unmodified scripts can be loaded and run under Node.
import { pathToFileURL } from "node:url";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const STUB = pathToFileURL(join(HERE, "stub-server.mjs")).href;

export function resolve(specifier, context, next) {
  if (specifier === "@minecraft/server") {
    return { url: STUB, shortCircuit: true };
  }
  return next(specifier, context);
}
