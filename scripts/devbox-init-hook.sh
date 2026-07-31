#!/usr/bin/env bash
# Run by the devbox init_hook whenever the devbox environment is entered.
# Seeds .env from the committed example on first use, then points APP_HOST at the
# public Decision Control URL (port 8880, the host the browser uses to reach DC;
# see process-compose.yaml): this Codespace's forwarded URL when running in a
# GitHub Codespace, otherwise http://localhost:8880.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# First use: seed a working .env from the committed example.
if [ ! -f .env ] && [ -f .env.example ]; then
  cp .env.example .env
  echo "Seeded .env from .env.example"
fi

# Point APP_HOST at the public DC (8880) URL: this Codespace's forwarded URL when
# in a Codespace, otherwise localhost.
if [ -f .env ]; then
  if [ -n "${CODESPACE_NAME:-}" ]; then
    dom="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
    url="https://${CODESPACE_NAME}-8880.${dom}"
  else
    url="http://localhost:8880"
  fi
  if grep -q '^APP_HOST=' .env; then
    sed -i "s|^APP_HOST=.*|APP_HOST=${url}|" .env
  else
    printf 'APP_HOST=%s\n' "$url" >> .env
  fi
  echo "Set APP_HOST=${url} in .env"
fi
