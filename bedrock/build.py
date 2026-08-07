#!/usr/bin/env python3
"""Package the Blockpal Bedrock packs into builds/blockpal-bedrock-<version>.mcaddon.

A .mcaddon is a zip of pack folders; importing it into Minecraft Bedrock
installs the behavior pack and resource pack together. The version is read
from the behavior pack manifest header.

THE BUILD REFUSES TO PACKAGE A BROKEN PACK. Minecraft says nothing when an
add-on is malformed — it just doesn't work — so everything in validate.py runs
first (JSON, manifests, script syntax, imports, entity events, textures) along
with the behaviour tests that drive the real scripts against a stubbed
Minecraft. Any error and nothing is written. Use --skip-checks only if you know
exactly why you want a broken pack.
"""
import json
import sys
import zipfile
from pathlib import Path

import validate

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
PACKS = {
    "Blockpal BP": ROOT / "packs" / "behavior",
    "Blockpal RP": ROOT / "packs" / "resource",
}


def main():
    if "--skip-checks" in sys.argv:
        print("!! --skip-checks: packaging WITHOUT validating. The result may silently "
              "do nothing in game.")
    else:
        report = validate.validate(run_tests="--no-tests" not in sys.argv)
        if not report.summary():
            print("\nRefusing to build: fix the errors above first "
                  "(or pass --skip-checks to build anyway).")
            return 1
        print()

    manifest = json.loads((PACKS["Blockpal BP"] / "manifest.json").read_text())
    version = ".".join(str(v) for v in manifest["header"]["version"])
    out = REPO / "builds" / f"blockpal-bedrock-{version}.mcaddon"
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        for folder_name, src in PACKS.items():
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    zf.write(path, f"{folder_name}/{path.relative_to(src)}")
    print(f"wrote {out} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
