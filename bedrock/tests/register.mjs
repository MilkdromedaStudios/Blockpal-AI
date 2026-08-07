// Installs the module-resolution hook, then runs the tests.
import { register } from "node:module";
register("./loader.mjs", import.meta.url);
