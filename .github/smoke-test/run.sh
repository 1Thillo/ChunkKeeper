#!/usr/bin/env bash
# Starts a real Paper or Folia server with ChunkKeeper, runs the console commands from
# commands.txt and checks the server log against expected.txt.
#
# usage: run.sh <paper|folia> <path to ChunkKeeper jar>
set -euo pipefail

PROJECT="$1"
PLUGIN_JAR="$2"
MC_VERSION="26.2"
HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(pwd)/smoke-server-$PROJECT"
USER_AGENT="ChunkKeeper-CI (https://github.com/1Thillo/ChunkKeeper)"

echo "::group::Download $PROJECT $MC_VERSION"
build=$(curl -fsS -H "User-Agent: $USER_AGENT" "https://fill.papermc.io/v3/projects/$PROJECT/versions/$MC_VERSION/builds/latest")
url=$(jq -r '.downloads["server:default"].url' <<< "$build")
sha=$(jq -r '.downloads["server:default"].checksums.sha256' <<< "$build")
echo "$(jq -r '.downloads["server:default"].name' <<< "$build") ($(jq -r '.channel' <<< "$build"))"
mkdir -p "$SERVER/plugins"
curl -fsS -H "User-Agent: $USER_AGENT" -o "$SERVER/server.jar" "$url"
echo "$sha  $SERVER/server.jar" | sha256sum --check --quiet
echo "::endgroup::"

# Test setup: the plugin, KeepChunks data for the import, and a small, quick server.
cp "$PLUGIN_JAR" "$SERVER/plugins/"
mkdir -p "$SERVER/plugins/KeepChunks"
printf 'chunks:\n- 10#10#world\n- -3#-4#world\n- broken\n' > "$SERVER/plugins/KeepChunks/data.yml"
printf 'general:\n  releaseallprotection: false\n' > "$SERVER/plugins/KeepChunks/config.yml"
echo "eula=true" > "$SERVER/eula.txt"   # the repository owner agreed to run test servers in CI
cat > "$SERVER/server.properties" <<'EOF'
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
EOF

cd "$SERVER"
mkfifo console
java -Xms1G -Xmx2G -jar server.jar --nogui < console > console.log 2>&1 &
pid=$!
exec 3> console

wait_for() { # pattern, seconds
  for _ in $(seq 1 "$2"); do
    grep -qF "$1" logs/latest.log 2>/dev/null && return 0
    kill -0 "$pid" 2>/dev/null || { echo "Server exited early"; return 1; }
    sleep 1
  done
  echo "Timed out waiting for: $1"; return 1
}

status=0
if wait_for 'Done (' 300; then
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" == wait\ * ]]; then sleep "${line#wait }"; continue; fi
    echo "$line" >&3
    sleep 1
  done < "$HERE/commands.txt"
  sleep 3
else
  status=1
fi
echo "stop" >&3
exec 3>&-
wait "$pid" || true

echo "::group::Server log"
cat logs/latest.log
echo "::endgroup::"

missing=0
while IFS= read -r expected; do
  [[ -z "$expected" || "$expected" == \#* ]] && continue
  if ! grep -qF -- "$expected" logs/latest.log; then
    echo "::error::Missing in the $PROJECT log: $expected"
    missing=1
  fi
done < "$HERE/expected.txt"

if grep -E 'ChunkKeeper' logs/latest.log | grep -qE 'Exception|generated an exception|Error occurred while enabling'; then
  echo "::error::ChunkKeeper caused an exception on $PROJECT"
  grep -nE -A5 'Exception' logs/latest.log | head -40
  missing=1
fi
if grep -qF 'Unknown or incomplete command' logs/latest.log; then
  echo "::error::A command was not found on $PROJECT"
  missing=1
fi

if [[ $status -ne 0 || $missing -ne 0 ]]; then
  exit 1
fi
echo "Smoke test on $PROJECT passed."
