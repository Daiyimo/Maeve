#!/usr/bin/env bash
# Verify the pinned toolchain. Exits 1 on any mismatch.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
PARENT="$(dirname "$REPO")"
TOOLCHAINS="${TOOLCHAINS:-$PARENT/_toolchains}"
ENV_ROOT="${ENV_ROOT:-$PARENT/_env}"

read_version() {
  python3 - "$HERE/versions.json" "$1" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding='utf-8'))
node = data
for part in sys.argv[2].split('.'):
    node = node[part]
print(node)
PY
}

fail=0
check() {
  local name="$1" expected="$2" actual="$3"
  if printf '%s' "$actual" | grep -q -- "$expected"; then
    printf 'OK   %-16s %s\n' "$name" "$expected"
  else
    printf 'FAIL %-16s expected %s, got %s\n' "$name" "$expected" "$actual" >&2
    fail=1
  fi
}

check JDK "$(read_version java.version)" "$(java -version 2>&1 | head -1)"
GRADLE_VERSION="$(read_version gradle.version)"
check Gradle "$GRADLE_VERSION" "$("$TOOLCHAINS/gradle-$GRADLE_VERSION/gradle-$GRADLE_VERSION/bin/gradle" --version 2>&1 | grep '^Gradle ' | head -1)"
check Flutter "$(read_version flutter.version)" "$("$TOOLCHAINS/flutter/bin/flutter" --version 2>&1 | head -1)"
check Dart "$(read_version flutter.dart)" "$("$TOOLCHAINS/flutter/bin/dart" --version 2>&1 | head -1)"

AGP="$(sed -n 's/^agp *= *"\([^"]*\)"/\1/p' "$REPO/gradle/libs.versions.toml")"
KOTLIN="$(sed -n 's/^kotlin *= *"\([^"]*\)"/\1/p' "$REPO/gradle/libs.versions.toml")"
check AGP "$(read_version android.agp)" "$AGP"
check Kotlin "$(read_version android.kotlin)" "$KOTLIN"

check JAVA_HOME "$TOOLCHAINS/jdk-17" "${JAVA_HOME:-<unset>}"
check FLUTTER_ROOT "$TOOLCHAINS/flutter" "${FLUTTER_ROOT:-<unset>}"

exit "$fail"
