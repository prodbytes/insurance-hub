#!/usr/bin/env bash
# Started by the dev container on every boot (postStartCommand).
# Waits for Docker, then launches all process-compose services in the background
# via devbox (so the JDK/Quarkus toolchain is on PATH).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Enter the devbox environment once so its init_hook (scripts/devbox-init-hook.sh)
# runs: it seeds .env from the example and, in a Codespace, sets APP_HOST. This
# must happen before we source .env below for the registry login.
devbox run -- true

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

# In a Codespace, expose decision-control (8880), the ih-vdn app (8881), and
# ih-audit (8882) publicly. Visibility set in devcontainer.json only applies
# at creation; this re-applies on every boot.
make_ports_public() {
  [ -n "${CODESPACE_NAME:-}" ] || { echo "Not a Codespace; skipping public ports."; return 0; }
  ensure_gh || { echo "gh unavailable; set port visibility manually."; return 0; }
  echo "Making ports 8880, 8881 and 8882 public..."
  gh codespace ports visibility 8880:public 8881:public 8882:public -c "$CODESPACE_NAME" \
    || echo "Could not set ports public (org policy may block public ports)."
}
make_ports_public || true

# docker-in-docker may still be starting; wait for the daemon.
echo "Waiting for Docker daemon..."
for _ in $(seq 1 30); do
  if docker info >/dev/null 2>&1; then break; fi
  sleep 2
done

# Log in to the Aletyx private registry so decision-control can be pulled.
#
# In Codespaces the ALETYX_* credentials arrive as Codespaces secrets, which the
# platform injects straight into our environment — nothing in devcontainer.json
# needs to (or should) forward them. For local Dev Containers there are no
# Codespaces secrets, so we source .env, where a developer can keep the same
# vars (it is gitignored). .env never overrides a value already in the
# environment, so the Codespaces-injected secret always wins.
if [ -f .env ]; then
  while IFS= read -r line; do
    case "$line" in ''|'#'*) continue ;; esac   # skip blanks/comments
    key=${line%%=*}
    [ "$key" = "$line" ] && continue            # skip lines without '='
    [ -n "${!key:-}" ] && continue              # keep value already in env
    export "$line"
  done < .env
fi

registry_login() {
  if [ -z "${ALETYX_REGISTRY:-}" ] || [ -z "${ALETYX_USERNAME:-}" ] || [ -z "${ALETYX_PASSWORD:-}" ]; then
    echo "ALETYX_* registry secrets not set; skipping docker login."
    if [ -n "${CODESPACE_NAME:-}" ]; then
      echo "  In Codespaces these come from Codespaces secrets. Check that"
      echo "  ALETYX_REGISTRY / ALETYX_USERNAME / ALETYX_PASSWORD exist AND are"
      echo "  granted to this repository (Settings > Codespaces secrets show the"
      echo "  repo in 'Repository access'), then rebuild the Codespace."
    fi
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
  local b_dc b_vdn b_audit
  if [ -n "${CODESPACE_NAME:-}" ]; then
    local dom="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
    b_dc="https://${CODESPACE_NAME}-8880.${dom}"
    b_vdn="https://${CODESPACE_NAME}-8881.${dom}"
    b_audit="https://${CODESPACE_NAME}-8882.${dom}"
  else
    b_dc="http://localhost:8880"
    b_vdn="http://localhost:8881"
    b_audit="http://localhost:8882"
  fi
  cat <<EOF

────────────────────────────────────────────────────────────
 Services are starting. Once healthy, open:
   • ih-vdn (insurance app) : ${b_vdn}
   • ih-audit (messages)    : ${b_audit}
   • Decision Control       : ${b_dc}
You can also find the ports on the "Ports" panel, and
manage running services witn the following command: 
  devbox services attach
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
