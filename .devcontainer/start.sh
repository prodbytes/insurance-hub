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

# Install the GitHub CLI to ~/.local/bin if it isn't already available.
# Static binary install needs no root, unlike the apt repo method.
ensure_gh() {
  command -v gh >/dev/null 2>&1 && return 0
  echo "gh CLI not found — installing to ~/.local/bin ..."
  local arch tag
  case "$(uname -m)" in
    x86_64|amd64)  arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) echo "  unsupported arch $(uname -m); skipping"; return 1 ;;
  esac
  tag="$(curl -fsSL ${GITHUB_TOKEN:+-H "Authorization: Bearer $GITHUB_TOKEN"} \
         https://api.github.com/repos/cli/cli/releases/latest \
         | grep -m1 '"tag_name"' | sed -E 's/.*"v?([^"]+)".*/\1/')"
  [ -n "$tag" ] || { echo "  could not resolve latest gh version"; return 1; }
  curl -fsSL "https://github.com/cli/cli/releases/download/v${tag}/gh_${tag}_linux_${arch}.tar.gz" \
    | tar -xz -C /tmp
  mkdir -p "$HOME/.local/bin"
  install -m755 "/tmp/gh_${tag}_linux_${arch}/bin/gh" "$HOME/.local/bin/gh"
  export PATH="$HOME/.local/bin:$PATH"
  command -v gh >/dev/null 2>&1
}

# In a Codespace, expose the ih-vdn app (8080) publicly. Visibility set in
# devcontainer.json only applies at creation; this re-applies on every boot.
make_port_public() {
  [ -n "${CODESPACE_NAME:-}" ] || { echo "Not a Codespace; skipping public port."; return 0; }
  ensure_gh || { echo "gh unavailable; set port 8080 visibility manually."; return 0; }
  echo "Making port 8080 public..."
  gh codespace ports visibility 8080:public -c "$CODESPACE_NAME" \
    || echo "Could not set 8080 public (org policy may block public ports)."
}
make_port_public || true

# docker-in-docker may still be starting; wait for the daemon.
echo "Waiting for Docker daemon..."
for _ in $(seq 1 30); do
  if docker info >/dev/null 2>&1; then break; fi
  sleep 2
done

# Log in to the Aletyx private registry so decision-control can be pulled.
# Credentials come from Codespaces/repository secrets (ALETYX_* env vars);
# if any are unset we skip quietly — the other services still come up.
registry_login() {
  if [ -z "${ALETYX_REGISTRY:-}" ] || [ -z "${ALETYX_USERNAME:-}" ] || [ -z "${ALETYX_PASSWORD:-}" ]; then
    echo "ALETYX_* registry secrets not set; skipping docker login."
    return 0
  fi
  echo "Logging in to ${ALETYX_REGISTRY}..."
  printf '%s' "$ALETYX_PASSWORD" \
    | docker login "$ALETYX_REGISTRY" --username "$ALETYX_USERNAME" --password-stdin \
    || echo "docker login to ${ALETYX_REGISTRY} failed; decision-control may stay red."
}
registry_login

# Print the URLs to reach the apps once they finish starting.
print_endpoints() {
  local b8080 b8081
  if [ -n "${CODESPACE_NAME:-}" ]; then
    local dom="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
    b8080="https://${CODESPACE_NAME}-8080.${dom}"
    b8081="https://${CODESPACE_NAME}-8081.${dom}"
  else
    b8080="http://localhost:8080"
    b8081="http://localhost:8081"
  fi
  cat <<EOF

────────────────────────────────────────────────────────────
 Services are starting. Once healthy, open:
   • ih-vdn (insurance app) : ${b8080}
   • Decision Control       : ${b8081}
────────────────────────────────────────────────────────────
EOF
}

# Idempotent: skip if a process-compose instance is already up.
if devbox services ls >/dev/null 2>&1 && devbox services ls 2>/dev/null | grep -q .; then
  echo "Services already running."
  print_endpoints
  exit 0
fi

echo "Starting services (devbox services up --background)..."
devbox services up --background
print_endpoints
