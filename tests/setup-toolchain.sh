#!/bin/bash
# Fetches everything needed to compile and test Blockpal with plain javac, no Gradle.
#
# 26.x ships unobfuscated with official names, so there is no mappings step: the
# Minecraft client jar and its libraries, the fabric-api fat jar's nested modules and
# Fabric Loader are enough to typecheck the whole mod. Gradle's own distribution
# download is blocked in some sandboxes, which is why this path exists at all.
#
# Everything lands in $BLOCKPAL_TC (default ~/.blockpal-toolchain), about 700 MB.
set -u
TC="${BLOCKPAL_TC:-$HOME/.blockpal-toolchain}"
mkdir -p "$TC/libs" "$TC/jdk"
cd "$TC" || exit 1
log(){ echo "[tc] $*"; }

# ── JDK 25 ────────────────────────────────────────────────────────────────────
if [ ! -x "$TC/jdk/bin/javac" ]; then
  log "scraping jdk.java.net/archive for a linux-x64 JDK 25 tarball"
  URL=$(curl -s --max-time 60 https://jdk.java.net/archive/ \
        | grep -oE 'https://download\.java\.net/[^"]*jdk-25[^"]*linux-x64_bin\.tar\.gz' \
        | head -1)
  if [ -z "$URL" ]; then log "FAILED to find JDK 25 url"; else
    log "downloading $URL"
    curl -s --max-time 900 -o jdk.tar.gz "$URL" && \
    tar xzf jdk.tar.gz && \
    D=$(find . -maxdepth 1 -type d -name 'jdk-25*' | head -1) && \
    rm -rf "$TC/jdk" && mv "$D" "$TC/jdk" && rm -f jdk.tar.gz
  fi
fi
"$TC/jdk/bin/javac" -version 2>&1 | sed 's/^/[tc] javac: /'

# ── Minecraft 26.2 client.jar + libraries ─────────────────────────────────────
if [ ! -f "$TC/libs/client.jar" ]; then
  log "resolving Minecraft 26.2 from piston-meta"
  VJ=$(curl -s --max-time 60 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
       | python3 -c "import sys,json;m=json.load(sys.stdin);print(next(v['url'] for v in m['versions'] if v['id']=='26.2'))")
  log "version json: $VJ"
  curl -s --max-time 120 -o version.json "$VJ"
  python3 - <<'PY' > urls.txt
import json
d=json.load(open('version.json'))
print(d['downloads']['client']['url'])
for l in d['libraries']:
    a=l.get('downloads',{}).get('artifact')
    if a: print(a['url'])
PY
  log "$(wc -l < urls.txt) artifacts to fetch"
  head -1 urls.txt | xargs -I{} curl -s --max-time 900 -o "$TC/libs/client.jar" {}
  tail -n +2 urls.txt | xargs -P 8 -I{} sh -c 'curl -s --max-time 300 -O --output-dir '"$TC"'/libs {}'
fi

# ── Fabric loader + API ───────────────────────────────────────────────────────
FL=0.19.3
FA=0.152.2+26.2
[ -f "$TC/libs/fabric-loader.jar" ] || curl -s --max-time 300 -o "$TC/libs/fabric-loader.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-loader/$FL/fabric-loader-$FL.jar"
[ -f "$TC/fabric-api.jar" ] || curl -s --max-time 600 -o "$TC/fabric-api.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FA/fabric-api-$FA.jar"
if [ -f "$TC/fabric-api.jar" ] && [ ! -f "$TC/libs/.api-unpacked" ]; then
  log "unpacking fabric-api nested modules"
  rm -rf "$TC/apix" && mkdir -p "$TC/apix" && (cd "$TC/apix" && "$TC/jdk/bin/jar" xf "$TC/fabric-api.jar" META-INF/jars 2>/dev/null || unzip -qo "$TC/fabric-api.jar" 'META-INF/jars/*' )
  find "$TC/apix/META-INF/jars" -name '*.jar' -exec cp {} "$TC/libs/" \; 2>/dev/null
  touch "$TC/libs/.api-unpacked"
fi
# annotations + mixin bits loader needs
for GAV in "org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar" \
           "com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"; do
  F="$TC/libs/$(basename $GAV)"
  [ -f "$F" ] || curl -s --max-time 300 -o "$F" "https://repo1.maven.org/maven2/$GAV"
done
curl -s --max-time 300 -o "$TC/libs/sponge-mixin.jar" \
  "https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.15.4+mixin.0.8.7/sponge-mixin-0.15.4+mixin.0.8.7.jar" 2>/dev/null

log "libs: $(ls "$TC/libs" | wc -l) jars, $(du -sh "$TC/libs" | cut -f1)"
log "DONE"
