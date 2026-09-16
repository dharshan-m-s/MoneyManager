#!/usr/bin/env sh
set -eu

GRADLE_VERSION="9.5.0"
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/money-manager-gradle/${GRADLE_VERSION}"
GRADLE_BIN="$CACHE_DIR/gradle-${GRADLE_VERSION}/bin/gradle"
DIST_ZIP="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$DIST_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 20 -o "$DIST_ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$DIST_ZIP" "$DIST_URL"
    else
      echo "curl or wget is required to bootstrap Gradle $GRADLE_VERSION" >&2
      exit 1
    fi
  fi
  rm -rf "$CACHE_DIR/gradle-${GRADLE_VERSION}"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$DIST_ZIP" -d "$CACHE_DIR"
  else
    echo "unzip is required to bootstrap Gradle $GRADLE_VERSION" >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" --project-dir "$ROOT_DIR" "$@"
