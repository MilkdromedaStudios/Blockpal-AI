#!/bin/bash
# Runs every Blockpal JVM test. Needs the toolchain from ./setup-toolchain.sh first.
#
#   ./tests/setup-toolchain.sh     # once, ~700 MB
#   ./tests/run.sh
#
# These cover the parts where a mistake is invisible at compile time and expensive
# in-world: the PVT network and its data pipeline, the hand-written ConfigData codec,
# the script API's arity/dispatch tables, and the config schema's migrations.
set -u
cd "$(dirname "$0")/.." || exit 1
TC="${BLOCKPAL_TC:-$HOME/.blockpal-toolchain}"
OUT=$(mktemp -d)
JAVAC="$TC/jdk/bin/javac"
JAVA="$TC/jdk/bin/java"

if [ ! -x "$JAVAC" ]; then
  echo "No toolchain at $TC — run ./tests/setup-toolchain.sh first." >&2
  exit 1
fi
CP=$(find "$TC/libs" -name '*.jar' | tr '\n' ':')

echo "════ compiling the mod (both source sets) ════"
mkdir -p "$OUT/mod"
"$JAVAC" -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d "$OUT/mod" \
    $(find src/main/java src/client/java -name '*.java') || exit 1
echo "   ok"

fail=0
run() {
  echo
  echo "════ $1 ════"
  mkdir -p "$OUT/t"
  # The FabricLoader stub must precede the real one: ModConfig reaches for the game's
  # config directory at class-init, which no test has.
  "$JAVAC" -nowarn -cp "$OUT/mod:$CP" -d "$OUT/t" \
      tests/stub/net/fabricmc/loader/api/FabricLoader.java "tests/src/$2.java" || { fail=1; return; }
  "$JAVA" -cp "$OUT/t:$OUT/mod:$CP" "$2" || fail=1
}

run "PVT network and action space" NetTest
run "PVT data pipeline" PipelineTest
run "ConfigData codec round-trip" ConfigCodecTest
run "Script API consistency" ApiConsistency
run "Config schema" ConfigTest
run "Archive extraction + zip-slip" ArchiveTest
run "Local AI: 3 GB rule + consent" LocalAiTest

rm -rf "$OUT"
echo
[ $fail -eq 0 ] && echo "ALL SUITES PASSED" || echo "SOME SUITES FAILED"
exit $fail
