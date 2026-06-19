#!/usr/bin/env bash
# Periodic local-dev health check: docker registry auth, database, and ih-vdn app.
set -uo pipefail

# --- Resolve repo root and load .env (if present) ---------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

# --- Configuration (override via .env) --------------------------------------
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-20}"

# Registries to verify a docker login exists for.
DOCKERHUB_REGISTRY="${DOCKERHUB_REGISTRY:-https://index.docker.io/v1/}"
ALETYX_REGISTRY="${ALETYX_REGISTRY:-registry-innovator.aletyx.services}"

# Database (matches the process-compose `database` process).
DB_CONTAINER="${DB_CONTAINER:-insurance-hub-db}"
DB_USER="${QUARKUS_DATASOURCE_USERNAME:-quarkus}"
DB_NAME="${POSTGRES_DB:-insurancehub}"

# Decision Control container (matches the process-compose `decision-control` process).
DC_CONTAINER="${DC_CONTAINER:-decision-control}"

# ih-vdn readiness endpoint (same as the process-compose http_get probe).
IH_VDN_HEALTH_URL="${IH_VDN_HEALTH_URL:-http://127.0.0.1:8080/q/health/ready}"

# --- Output helpers ----------------------------------------------------------
EMOJI_OK="✅"
EMOJI_FAIL="❌"

# Run a check command and emit "<emoji> <name>". The check's exit status is
# captured directly here, so the result never depends on statement ordering.
report() {
  local name="$1"; shift
  if "$@"; then
    printf '%s %s' "$EMOJI_OK" "$name"
  else
    printf '%s %s' "$EMOJI_FAIL" "$name"
  fi
}

# --- Checks (silent: return status only) ------------------------------------

# A docker login leaves an entry under .auths in the docker config. We check for
# that entry's presence (this confirms a login was performed, not that the
# stored credential is still valid server-side).
check_registry_auth() {
  local registry="$1"
  local cfg="${DOCKER_CONFIG:-$HOME/.docker}/config.json"

  [ -f "$cfg" ] || return 1
  if command -v jq >/dev/null 2>&1; then
    jq -e --arg r "$registry" '.auths // {} | has($r)' "$cfg" >/dev/null 2>&1
  else
    grep -q "\"${registry}\"" "$cfg"
  fi
}

check_database() {
  command -v docker >/dev/null 2>&1 || return 1
  docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER" || return 1
  docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1
}

check_decision_control() {
  command -v docker >/dev/null 2>&1 || return 1
  docker ps --format '{{.Names}}' | grep -qx "$DC_CONTAINER"
}

check_ih_vdn() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$IH_VDN_HEALTH_URL" 2>/dev/null)"
  [ "$code" = "200" ]
}

# --- Loop --------------------------------------------------------------------
while true; do
  line=""
  line+="$(report docker-hub-registry check_registry_auth "$DOCKERHUB_REGISTRY")  "
  line+="$(report aletyx-registry     check_registry_auth "$ALETYX_REGISTRY")  "
  line+="$(report database            check_database)  "
  line+="$(report decision-control    check_decision_control)  "
  line+="$(report ih-vdn              check_ih_vdn)"
  printf '%s\n' "$line"
  sleep "$HEALTH_INTERVAL_SECONDS"
done
