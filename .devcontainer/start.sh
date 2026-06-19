#!/usr/bin/env bash
# Started by the dev container on every boot (postStartCommand).
# Seeds .env if needed, waits for Docker, then launches all process-compose
# services in the background via devbox (so the JDK/Quarkus toolchain is on PATH).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# First boot: seed a working .env from the committed example.
if [ ! -f .env ] && [ -f .env.example ]; then
  cp .env.example .env
  echo "Seeded .env from .env.example"
fi

# docker-in-docker may still be starting; wait for the daemon.
echo "Waiting for Docker daemon..."
for _ in $(seq 1 30); do
  if docker info >/dev/null 2>&1; then break; fi
  sleep 2
done

# Idempotent: skip if a process-compose instance is already up.
if devbox services ls >/dev/null 2>&1 && devbox services ls 2>/dev/null | grep -q .; then
  echo "Services already running."
  exit 0
fi

echo "Starting services"
exec devbox services up 
