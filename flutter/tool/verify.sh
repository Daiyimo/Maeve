# Maeve Flutter validation.
#
#   bash flutter/tool/verify.sh
#
# Runs the same three checks FLUTTER_MIGRATION.md asks for, in order, and stops
# at the first failure: format, analyze, test. A non-zero exit from the format
# step means the tree is not formatted; run `dart format lib test` to fix it.
#
# Toolchain and caches come from the shared env file so this script and manual
# runs cannot drift apart.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT/../_env/env.sh"

if [ -f "$ENV_FILE" ]; then
  # shellcheck source=/dev/null
  . "$ENV_FILE"
else
  echo "warning: $ENV_FILE not found, falling back to defaults" >&2
  export FLUTTER_ROOT="${FLUTTER_ROOT:-D:/dev/flutter/flutter}"
  export PUB_CACHE="${PUB_CACHE:-G:/vibe-coding/daiyimo/_env/pub-cache}"
  export NO_PROXY="127.0.0.1,localhost,::1"
  export no_proxy="$NO_PROXY"
fi

FLUTTER_ROOT="${FLUTTER_ROOT%/}"
FLUTTER_CMD="$FLUTTER_ROOT/bin/flutter"
DART_CMD="$FLUTTER_ROOT/bin/dart"

cd "$ROOT/flutter"

echo "== flutter --version =="
"$FLUTTER_CMD" --version | head -4

echo "== dart format =="
"$DART_CMD" format --output=none --set-exit-if-changed lib test

echo "== flutter analyze =="
"$FLUTTER_CMD" analyze

echo "== flutter test =="
"$FLUTTER_CMD" test
