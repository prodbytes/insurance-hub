#!/usr/bin/env bash
set -euo pipefail

# Resolve the ih-vdn module dir (parent of this script's dir) and the repo root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(git -C "$MODULE_DIR" rev-parse --show-toplevel 2>/dev/null || dirname "$MODULE_DIR")"

cd "$MODULE_DIR"

# Upload the DMN models to Decision Control first; sourcing keeps the
# IH_<MODEL>_URL exports in this shell so quarkus:dev inherits them.
# shellcheck disable=SC1091
. "$REPO_ROOT/scripts/models-upload.sh"
if [ "$_models_upload_rc" -ne 0 ]; then
  echo "❌ models upload failed" >&2
  echo "❌❌❌❌❌❌❌❌❌ " >&2
fi
set -euo pipefail  # the sourced script leaves set -e off; restore it

ARGS=()
if [ -f "$REPO_ROOT/settings.xml" ]; then
  ARGS+=(-s "$REPO_ROOT/settings.xml")
fi

exec ./mvnw "${ARGS[@]}" quarkus:dev
