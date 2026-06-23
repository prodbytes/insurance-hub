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

# Publish the decision models to Decision Control so they're available at startup,
# and export each model's runtime URL (e.g. QUOTE_VEHICLE_PRICE_URL) into this
# shell. Sourced (not executed) so those exports are inherited by quarkus:dev
# below. Non-fatal: a failed upload (e.g. DC not reachable) must not block dev
# start, and the script skips models that already exist.
# shellcheck disable=SC1091
source "$REPO_ROOT/scripts/models-upload.sh" || echo "warning: model upload failed; continuing without it" >&2

exec ./mvnw "${ARGS[@]}" quarkus:dev
