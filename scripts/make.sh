#!/usr/bin/env bash
set -exuo pipefail

# Run from repo root regardless of where the script is invoked.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODULE_DIR="ih-vdn"

SETTINGS_ARGS=()
if [ -f "$ROOT_DIR/settings.xml" ]; then
  SETTINGS_ARGS=(-s "$ROOT_DIR/settings.xml")
fi

build() {
  mvn -f "$MODULE_DIR/pom.xml" "${SETTINGS_ARGS[@]}" -U clean verify
}

target="${1:-build}"
case "$target" in
  build) build ;;
  *)
    echo "Unknown target: $target" >&2
    echo "Usage: $0 build" >&2
    exit 1
    ;;
esac
