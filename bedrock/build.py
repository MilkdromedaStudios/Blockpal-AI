#!/usr/bin/env python3
"""Package the Blockpal Bedrock packs into builds/blockpal-bedrock-<version>.mcaddon.

A .mcaddon is a zip of pack folders; importing it into Minecraft Bedrock
installs the behavior pack and resource pack together. The version is read
from the behavior pack manifest header.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
PACKS = {
    "Blockpal BP": ROOT / "packs" / "behavior",
    "Blockpal RP": ROOT / "packs" / "resource",
}


def main():
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


if __name__ == "__main__":
    main()
