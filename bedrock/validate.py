#!/usr/bin/env python3
"""Check the Blockpal Bedrock packs before they are allowed to build.

WHY THIS EXISTS
---------------
Minecraft Bedrock is silent about broken add-ons. A malformed JSON file, a
script that imports a name which doesn't exist, or an entity event a script
triggers but the entity never defines — none of these produce an error the
player can see. The pack just quietly does nothing, which is exactly how 1.0.0
shipped completely unresponsive and nobody found out until it was installed.

`build.py` refuses to package anything that fails these checks, so a broken
pack cannot be produced in the first place.

Run directly for a report:  python3 bedrock/validate.py
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BP = ROOT / "packs" / "behavior"
RP = ROOT / "packs" / "resource"

# The @minecraft/* modules the scripts may import (anything else must be a
# relative file inside the pack).
ALLOWED_MODULES = {"@minecraft/server", "@minecraft/server-ui"}

UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.I)

# @minecraft/server is a VERSIONED module: Minecraft only provides the versions it
# ships with. Ask for one the runtime doesn't have and the script module is dropped
# before a single line runs — no error in game, no content-log entry, nothing.
#
# That is what shipped in 1.0.x: it declared 1.17.0 (Minecraft 1.21.60, early 2025)
# and 2.0.0 was a breaking major, so on any current version the whole add-on was
# simply never loaded.
#
#   1.x  →  Minecraft 1.21.60-1.21.70   (superseded)
#   2.0.0 →  Minecraft 1.21.80 and up
MIN_SERVER_API_MAJOR = 2
# The engine version where each API major became available.
API_MAJOR_MIN_ENGINE = {2: (1, 21, 80)}


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.checks = 0

    def check(self, ok, message, warn_only=False):
        self.checks += 1
        if ok:
            return True
        (self.warnings if warn_only else self.errors).append(message)
        return False

    def error(self, message):
        self.errors.append(message)

    def summary(self):
        for w in self.warnings:
            print(f"  warning  {w}")
        for e in self.errors:
            print(f"  ERROR    {e}")
        print(f"\n{self.checks} checks, {len(self.errors)} error(s), {len(self.warnings)} warning(s)")
        return len(self.errors) == 0


def load_json(rep, path):
    """Parse a JSON file, reporting a readable error instead of exploding."""
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        rep.error(f"{path.relative_to(ROOT)} is not valid JSON: {exc}")
        return None


def check_all_json_parses(rep):
    for path in sorted(ROOT.rglob("*.json")):
        if "node_modules" in path.parts:
            continue
        rep.check(load_json(rep, path) is not None, f"{path.relative_to(ROOT)} failed to parse")


def check_manifests(rep):
    bp = load_json(rep, BP / "manifest.json")
    rp = load_json(rep, RP / "manifest.json")
    if not bp or not rp:
        return

    uuids = []
    for name, manifest in (("behavior", bp), ("resource", rp)):
        header = manifest.get("header", {})
        rep.check(bool(header.get("name")), f"{name} manifest has no header.name")
        rep.check(UUID_RE.match(str(header.get("uuid", ""))) is not None,
                  f"{name} manifest header.uuid is not a UUID")
        rep.check(isinstance(header.get("version"), list) and len(header["version"]) == 3,
                  f"{name} manifest header.version must be [x, y, z]")
        rep.check(isinstance(header.get("min_engine_version"), list),
                  f"{name} manifest needs a min_engine_version")
        uuids.append(str(header.get("uuid", "")).lower())
        for module in manifest.get("modules", []):
            rep.check(UUID_RE.match(str(module.get("uuid", ""))) is not None,
                      f"{name} manifest has a module with a bad uuid")
            uuids.append(str(module.get("uuid", "")).lower())

    rep.check(len(uuids) == len(set(uuids)), "two manifests/modules share a UUID — Minecraft will "
                                             "refuse or silently drop one of the packs")

    # The behaviour pack must point at a script entry that exists, and must
    # depend on the resource pack by its real UUID.
    script_modules = [m for m in bp.get("modules", []) if m.get("type") == "script"]
    if rep.check(len(script_modules) == 1, "behavior manifest must have exactly one script module"):
        entry = script_modules[0].get("entry", "")
        rep.check(bool(entry) and (BP / entry).is_file(),
                  f"script entry '{entry}' does not exist in the behavior pack")

    deps = bp.get("dependencies", [])
    module_deps = {d.get("module_name"): d for d in deps if d.get("module_name")}
    if rep.check("@minecraft/server" in module_deps,
                 "behavior manifest does not depend on @minecraft/server"):
        raw = str(module_deps["@minecraft/server"].get("version", ""))
        parts = raw.split("-")[0].split(".")
        try:
            major = int(parts[0])
        except (ValueError, IndexError):
            major = -1
            rep.error(f"@minecraft/server version '{raw}' is not a version number")
        if major >= 0:
            rep.check(major >= MIN_SERVER_API_MAJOR,
                      f"@minecraft/server {raw} is a dead API line. Minecraft only provides "
                      f"the versions it ships with, so asking for {major}.x means the script "
                      f"module is DROPPED BEFORE IT RUNS — no commands, no logs, no error. "
                      f"Use {MIN_SERVER_API_MAJOR}.0.0 or newer.")
            wanted = API_MAJOR_MIN_ENGINE.get(major)
            engine = tuple(bp.get("header", {}).get("min_engine_version", []) or [])
            if wanted and engine:
                rep.check(engine >= wanted,
                          f"min_engine_version {list(engine)} is older than Minecraft "
                          f"{'.'.join(map(str, wanted))}, which is where @minecraft/server "
                          f"{major}.x first appears — the pack would load on a version that "
                          f"cannot provide the API it asks for")

    rp_uuid = str(rp.get("header", {}).get("uuid", "")).lower()
    pack_deps = [str(d.get("uuid", "")).lower() for d in deps if d.get("uuid")]
    rep.check(rp_uuid in pack_deps,
              "behavior manifest must depend on the resource pack's UUID, or the textures "
              "won't load with the behaviour")


def script_files():
    return sorted((BP / "scripts").glob("*.js"))


def check_script_syntax(rep):
    """`node --check` every script — a syntax error means the pack never loads."""
    if not have_node():
        rep.warnings.append("node not found — skipped JavaScript syntax checking")
        return
    for path in script_files():
        result = subprocess.run(["node", "--check", str(path)],
                                capture_output=True, text=True)
        rep.check(result.returncode == 0,
                  f"{path.name} has a syntax error:\n{result.stderr.strip()}")


IMPORT_RE = re.compile(r'import\s+(?:(?P<names>\{[^}]*\}|\*\s+as\s+\w+|\w+)\s+from\s+)?["\'](?P<src>[^"\']+)["\']')
EXPORT_RE = re.compile(r'export\s+(?:async\s+)?(?:function|const|let|var|class)\s+(\w+)')
EXPORT_LIST_RE = re.compile(r'export\s*\{([^}]*)\}')


def exported_names(path):
    text = path.read_text(encoding="utf-8")
    names = set(EXPORT_RE.findall(text))
    for group in EXPORT_LIST_RE.findall(text):
        for part in group.split(","):
            part = part.strip()
            if not part:
                continue
            names.add(part.split(" as ")[-1].strip())
    return names


def check_imports(rep):
    """
    Every import must resolve, and every named import must actually be exported.

    This is the highest-value check in the file: in an ES module a missing named
    export is a hard load failure, so one typo takes the entire add-on offline —
    with no message in game.
    """
    for path in script_files():
        text = path.read_text(encoding="utf-8")
        for match in IMPORT_RE.finditer(text):
            src = match.group("src")
            names = match.group("names") or ""

            if src.startswith("@") or not src.startswith("."):
                rep.check(src in ALLOWED_MODULES,
                          f"{path.name} imports '{src}', which is not an allowed module "
                          f"({', '.join(sorted(ALLOWED_MODULES))})")
                continue

            target = (path.parent / src).resolve()
            if not rep.check(target.is_file(), f"{path.name} imports '{src}', which does not exist"):
                continue

            if names.startswith("{"):
                available = exported_names(target)
                for raw in names.strip("{}").split(","):
                    raw = raw.strip()
                    if not raw:
                        continue
                    wanted = raw.split(" as ")[0].strip()
                    rep.check(wanted in available,
                              f"{path.name} imports {{{wanted}}} from '{src}', but {target.name} "
                              f"does not export it — this stops the whole pack loading")


TRIGGER_RE = re.compile(r'triggerEvent\(\s*["\']([^"\']+)["\']')
SKIN_MAP_RE = re.compile(r'["\'](blockpal:skin_\w+)["\']')


def check_entity_events(rep):
    """
    Every entity event a script triggers must exist on the entity.

    `triggerEvent` with an unknown name throws at runtime, which — inside a
    command handler — reads to the player as "the command did nothing".
    """
    entity = load_json(rep, BP / "entities" / "companion.behavior.json")
    if not entity:
        return
    definition = entity.get("minecraft:entity", {})
    identifier = definition.get("description", {}).get("identifier")
    rep.check(identifier == "blockpal:companion",
              f"entity identifier is '{identifier}', but the scripts use 'blockpal:companion'")

    declared = set(definition.get("events", {}).keys())
    groups = set(definition.get("component_groups", {}).keys())

    used = set()
    for path in script_files():
        text = path.read_text(encoding="utf-8")
        used |= set(TRIGGER_RE.findall(text))
        used |= set(SKIN_MAP_RE.findall(text))

    for event in sorted(used):
        rep.check(event in declared,
                  f"scripts trigger entity event '{event}', which companion.behavior.json "
                  f"does not define (the command will silently do nothing)")

    # Each event should switch to a component group that exists.
    for name, body in definition.get("events", {}).items():
        add = body.get("add", {}).get("component_groups", []) if isinstance(body, dict) else []
        for group in add:
            rep.check(group in groups,
                      f"event '{name}' adds component group '{group}', which is not defined")


def check_resources(rep):
    """Textures and geometry referenced by the client entity must be real files."""
    client = load_json(rep, RP / "entity" / "companion.entity.json")
    if not client:
        return
    description = client.get("minecraft:client_entity", {}).get("description", {})

    rep.check(description.get("identifier") == "blockpal:companion",
              "the resource pack's client entity identifier does not match blockpal:companion")

    for key, ref in (description.get("textures") or {}).items():
        path = RP / f"{ref}.png"
        rep.check(path.is_file(), f"texture '{key}' -> {ref}.png is missing from the resource pack")

    geometry_ids = set()
    for geo_file in (RP / "models").rglob("*.json"):
        data = load_json(rep, geo_file)
        if not data:
            continue
        for entry in data.get("minecraft:geometry", []):
            ident = entry.get("description", {}).get("identifier")
            if ident:
                geometry_ids.add(ident)
    for key, ref in (description.get("geometry") or {}).items():
        rep.check(ref in geometry_ids,
                  f"geometry '{key}' -> '{ref}' is not defined by any model file")


def have_node():
    try:
        subprocess.run(["node", "--version"], capture_output=True, check=True)
        return True
    except Exception:
        return False


def run_behaviour_tests(rep):
    """
    Actually run the add-on against a stubbed Minecraft and check the commands
    work. Static checks can't tell you the pack responds to '!ai summon'.
    """
    runner = ROOT / "tests" / "run.sh"
    if not runner.is_file():
        rep.warnings.append("bedrock/tests/run.sh is missing — behaviour tests skipped")
        return
    if not have_node():
        rep.warnings.append("node not found — behaviour tests skipped")
        return
    result = subprocess.run(["bash", str(runner)], capture_output=True, text=True)
    rep.check(result.returncode == 0,
              "the behaviour tests failed — the pack does not work:\n"
              + "\n".join(result.stdout.strip().splitlines()[-25:]))


def validate(run_tests=True):
    rep = Report()
    print("Validating the Blockpal Bedrock packs…")
    check_all_json_parses(rep)
    check_manifests(rep)
    check_script_syntax(rep)
    check_imports(rep)
    check_entity_events(rep)
    check_resources(rep)
    if run_tests:
        run_behaviour_tests(rep)
    return rep


if __name__ == "__main__":
    report = validate(run_tests="--no-tests" not in sys.argv)
    sys.exit(0 if report.summary() else 1)
