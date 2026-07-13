#!/usr/bin/env bash
set -euo pipefail

# Resolve the ih-vdn module dir (parent of this script's dir) and the repo root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(git -C "$MODULE_DIR" rev-parse --show-toplevel 2>/dev/null || dirname "$MODULE_DIR")"

cd "$MODULE_DIR"

ARGS=()
if [ -f "$REPO_ROOT/settings.xml" ]; then
  ARGS+=(-s "$REPO_ROOT/settings.xml")
fi

exec ./mvnw "${ARGS[@]}" quarkus:dev
