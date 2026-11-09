#!/usr/bin/env bash
# Copy to <_ENV>/env.sh and edit the two roots for this machine.
# Source it before any Gradle or Flutter command:  . <_ENV>/env.sh

TOOLCHAINS="${TOOLCHAINS:-/d/_toolchains}"
ENV_ROOT="${ENV_ROOT:-/d/_env}"
ANDROID_SDK="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"

export JAVA_HOME="$TOOLCHAINS/jdk-17"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PUB_CACHE="$ENV_ROOT/pub-cache"
export FLUTTER_ROOT="$TOOLCHAINS/flutter"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"

# flutter test talks to flutter_tester over loopback; a machine proxy breaks it.
export NO_PROXY='127.0.0.1,localhost,::1'
export no_proxy="$NO_PROXY"

# Git Bash needs POSIX-style PATH entries.
to_posix() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}

export PATH="$(to_posix "$JAVA_HOME")/bin:$(to_posix "$FLUTTER_ROOT")/bin:$(to_posix "$ANDROID_HOME")/platform-tools:$PATH"
