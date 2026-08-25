#!/usr/bin/env bash
set -euo pipefail

# These legacy variable names are intentionally kept so existing GitHub
# repository settings do not need to be renamed. Their VALUES are now:
#   MODRINTH_TOKEN      = CurseForge upload API token
#   MODRINTH_PROJECT_ID = numeric CurseForge project ID
# This script does not contact Modrinth.
PROJECT_ID="${MODRINTH_PROJECT_ID:-}"
TOKEN="${MODRINTH_TOKEN:-}"
CURSEFORGE_BASE_URL="https://minecraft.curseforge.com"
RELEASE_TYPE="${CURSEFORGE_RELEASE_TYPE:-beta}"

if [[ -z "$PROJECT_ID" ]]; then
  echo "::error::MODRINTH_PROJECT_ID is empty. Set it to the numeric CurseForge project ID."
  exit 1
fi

if [[ ! "$PROJECT_ID" =~ ^[0-9]+$ ]]; then
  echo "::error::MODRINTH_PROJECT_ID must be the numeric CurseForge project ID. Got: $PROJECT_ID"
  exit 1
fi

if [[ -z "$TOKEN" ]]; then
  echo "::error::MODRINTH_TOKEN is empty. Store the CurseForge upload API token in that GitHub secret."
  exit 1
fi

version_from_filename() {
  local base version
  base="$(basename "$1")"
  version="$(printf '%s\n' "$base" | sed -nE 's/^.*-([0-9]+(\.[0-9]+)+)\.jar$/\1/p')"
  if [[ -z "$version" ]]; then
    echo "::error::Could not determine a version from $base" >&2
    return 1
  fi
  printf '%s\n' "$version"
}

minecraft_version_from_jar() {
  local jar metadata spec mc
  jar="$1"

  if ! metadata="$(unzip -p "$jar" fabric.mod.json 2>/dev/null)" || [[ -z "$metadata" ]]; then
    echo "::error::$jar does not contain a readable fabric.mod.json" >&2
    return 1
  fi

  spec="$(printf '%s' "$metadata" | jq -r '.depends.minecraft // empty')"
  mc="$(printf '%s\n' "$spec" | grep -oE '[0-9]+(\.[0-9]+){1,2}' | head -n1 || true)"

  if [[ -z "$mc" ]]; then
    echo "::error::Could not determine the Minecraft version from $jar (depends.minecraft=$spec)" >&2
    return 1
  fi

  printf '%s\n' "$mc"
}

changelog_for_version() {
  local version="$1"
  if [[ ! -f CHANGELOG.md ]]; then
    printf 'Blockpal %s.\n' "$version"
    return
  fi

  local text
  text="$(awk -v target="## $version" '
    $0 == target { found=1; next }
    found && /^## / { exit }
    found { print }
  ' CHANGELOG.md)"

  if [[ -n "${text//[[:space:]]/}" ]]; then
    printf '%s\n' "$text"
  else
    printf 'Blockpal %s.\n' "$version"
  fi
}

marker_tag_for() {
  local version="$1" mc="$2"
  printf 'curseforge-published/%s_mc%s\n' "$version" "$mc"
}

already_marked() {
  local tag="$1"
  git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1
}

mark_published() {
  local tag="$1"
  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git tag -f "$tag" "${GITHUB_SHA:-HEAD}"
  git push --force origin "refs/tags/$tag"
}

upload_one() {
  local jar="$1"
  local version="${2:-}"
  local mc changelog display_name marker metadata response file_id

  if [[ ! -f "$jar" ]]; then
    echo "::error::File not found: $jar"
    return 1
  fi

  if [[ -z "$version" ]]; then
    version="$(version_from_filename "$jar")"
  fi

  mc="$(minecraft_version_from_jar "$jar")"
  marker="$(marker_tag_for "$version" "$mc")"

  if already_marked "$marker"; then
    echo "::notice::$version for Minecraft $mc already has marker $marker; skipping."
    return 0
  fi

  changelog="$(changelog_for_version "$version")"
  display_name="Blockpal $version (MC $mc)"

  metadata="$(jq -n \
    --arg changelog "$changelog" \
    --arg displayName "$display_name" \
    --arg mc "$mc" \
    --arg releaseType "$RELEASE_TYPE" \
    '{
      changelog: $changelog,
      changelogType: "markdown",
      displayName: $displayName,
      gameVersionNames: ["Fabric", $mc],
      releaseType: $releaseType,
      isMarkedForManualRelease: false,
      relations: {
        projects: [
          { slug: "fabric-api", type: "requiredDependency" }
        ]
      }
    }')"

  echo "Uploading $(basename "$jar") as $display_name to CurseForge project $PROJECT_ID ..."
  response="$(curl --silent --show-error --fail-with-body \
    -H "X-Api-Token: $TOKEN" \
    -F "metadata=$metadata" \
    -F "file=@$jar;type=application/java-archive" \
    "$CURSEFORGE_BASE_URL/api/projects/$PROJECT_ID/upload-file")"

  file_id="$(printf '%s' "$response" | jq -r '.id // empty')"
  if [[ -z "$file_id" ]]; then
    echo "::error::CurseForge returned success but no file id: $response"
    return 1
  fi

  echo "::notice::Uploaded Blockpal $version to CurseForge (file id $file_id)."
  mark_published "$marker"
}

publish_all_builds() {
  local list_file
  list_file="$(mktemp)"
  trap 'rm -f "$list_file"' RETURN

  while IFS= read -r -d '' jar; do
    local version
    version="$(version_from_filename "$jar")"
    printf '%s\t%s\n' "$version" "$jar" >> "$list_file"
  done < <(find builds -maxdepth 1 -type f -name '*.jar' -print0)

  if [[ ! -s "$list_file" ]]; then
    echo "::error::No .jar files found in builds/."
    return 1
  fi

  sort -V -k1,1 "$list_file" -o "$list_file"

  echo "CurseForge backfill order (oldest -> newest):"
  cat "$list_file"

  while IFS=$'\t' read -r version jar; do
    upload_one "$jar" "$version"
    sleep 2
  done < "$list_file"
}

case "${1:-}" in
  current)
    if [[ $# -lt 2 ]]; then
      echo "Usage: $0 current <jar> [version]" >&2
      exit 2
    fi
    upload_one "$2" "${3:-}"
    ;;
  all-builds)
    publish_all_builds
    ;;
  *)
    echo "Usage: $0 {current <jar> [version]|all-builds}" >&2
    exit 2
    ;;
esac
