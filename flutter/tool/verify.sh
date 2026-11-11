#!/usr/bin/env bash
# Maeve Flutter validation: format, then analyze, then test.
#
#   bash flutter/tool/verify.sh
#
# Toolchain and caches come from MAEVE_ENV_FILE, then ../_env/env.sh, then PATH.
# A failing format step means the tree is not formatted, not that the build broke.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${MAEVE_ENV_FILE:-$ROOT/../_env/env.sh}"

if [ -f "$ENV_FILE" ]; then
  # shellcheck source=/dev/null
  . "$ENV_FILE"
else
  echo "note: $ENV_FILE not found; resolving the toolchain from PATH" >&2
fi

if [ -n "${FLUTTER_ROOT:-}" ]; then
  FLUTTER_ROOT="${FLUTTER_ROOT%/}"
  # Git Bash needs /d/dev/... rather than D:/dev/...
  if command -v cygpath >/dev/null 2>&1; then
    FLUTTER_ROOT="$(cygpath -u "$FLUTTER_ROOT")"
  fi
  FLUTTER_CMD="$FLUTTER_ROOT/bin/flutter"
  DART_CMD="$FLUTTER_ROOT/bin/dart"
else
  FLUTTER_CMD="$(command -v flutter || true)"
  DART_CMD="$(command -v dart || true)"
fi

if [ -z "$FLUTTER_CMD" ] || [ ! -x "$FLUTTER_CMD" ]; then
  echo "error: flutter not found. Set FLUTTER_ROOT or put flutter on PATH." >&2
  exit 2
fi
if [ -z "$DART_CMD" ] || [ ! -x "$DART_CMD" ]; then
  echo "error: dart not found. It ships with the Flutter SDK." >&2
  exit 2
fi

# flutter test talks to flutter_tester over a loopback WebSocket. A machine-wide
# HTTP proxy breaks the upgrade, so keep loopback out of it for every run.
export NO_PROXY="127.0.0.1,localhost,::1${NO_PROXY:+,$NO_PROXY}"
export no_proxy="$NO_PROXY"

cd "$ROOT/flutter"

echo "== flutter --version =="
"$FLUTTER_CMD" --version | head -4

echo "== locked dependencies =="
"$FLUTTER_CMD" pub get --enforce-lockfile

echo "== dart format =="
"$DART_CMD" format --output=none --set-exit-if-changed lib test tool

echo "== eclipse launcher vectors =="
"$DART_CMD" run tool/export_eclipse_icons.dart --check

echo "== flutter analyze =="
"$FLUTTER_CMD" analyze --no-pub --fatal-infos

echo "== flutter test =="
"$FLUTTER_CMD" test --no-pub --concurrency=2 --reporter=expanded \
  --file-reporter=json:build/test-results/flutter.json \
  --test-randomize-ordering-seed="${MAEVE_TEST_SEED:-20261111}"
