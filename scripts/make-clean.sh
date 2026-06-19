#!/usr/bin/env bash
set -exuo pipefail

# Run from repo root regardless of where the script is invoked.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SETTINGS_ARGS=()
if [ -f "$ROOT_DIR/settings.xml" ]; then
  SETTINGS_ARGS=(-s "$ROOT_DIR/settings.xml")
fi

mvn "${SETTINGS_ARGS[@]}" clean
