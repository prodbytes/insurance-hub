#!/usr/bin/env bash
set -euo pipefail

# Resolve the repo root (parent of this script's dir).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$REPO_ROOT"

# Load local dev environment (datasource credentials, etc.) if present.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

exec devbox services up
