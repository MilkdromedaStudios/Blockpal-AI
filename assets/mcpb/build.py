#!/usr/bin/env python3
"""Build assets/BlockPal.mcpb using only the Python standard library."""

from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parent
OUTPUT = ASSETS / "BlockPal.mcpb"
FILES = [
    ROOT / "manifest.json",
    ROOT / "server" / "index.js",
    ROOT / "README.md",
]


def main() -> None:
    if OUTPUT.exists():
        OUTPUT.unlink()

    with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as bundle:
        for path in FILES:
            if not path.is_file():
                raise FileNotFoundError(path)
            bundle.write(path, path.relative_to(ROOT).as_posix())

    print(OUTPUT)


if __name__ == "__main__":
    main()
