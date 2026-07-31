#!/usr/bin/env bash
# `make test`: bring up the services the test suites need, then run them.
#
# The only integration dependency is Decision Control on :8880 with the
# ih-models DMN models uploaded (CarQuoteDmnTest evaluates them through the DC
# runtime API); DC in turn needs the shared postgres container. The Quarkus
# test profile provides its own throwaway datasource via Dev Services and
# keeps Kafka off, so neither the app database contents nor the broker are
# needed here.
#
# Container names in docker-compose.yaml are fixed, so any docker compose
# command here would collide with a running `devbox services up` stack.
# Because of that, this script first checks for a live stack (or a starting
# process-compose) and only starts database + decision-control itself when
# nothing else is managing them — tearing down on exit exactly what it started.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$REPO_ROOT"

# Load local dev environment (DECISION_CONTROL_IMAGE, datasource creds, etc.).
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

DC_URL="${IH_DC_URL:-http://localhost:8880}"
DB_CONTAINER="${DB_CONTAINER:-insurance-hub-db}"
DB_USER="${QUARKUS_DATASOURCE_USERNAME:-quarkus}"
DB_NAME="${POSTGRES_DB:-insurancehub}"

dc_ready() {
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$DC_URL/" 2>/dev/null)"
  [ "$code" = "200" ]
}

db_ready() {
  docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER" \
    && docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1
}

# wait_for <description> <timeout_seconds> <check-fn>
wait_for() {
  local what="$1" timeout="$2" check="$3" waited=0
  until "$check"; do
    if [ "$waited" -ge "$timeout" ]; then
      echo "❌ timed out after ${timeout}s waiting for $what" >&2
      return 1
    fi
    sleep 3
    waited=$((waited + 3))
  done
  echo "✅ $what is ready"
}

STARTED_SERVICES=0
cleanup() {
  if [ "$STARTED_SERVICES" = 1 ]; then
    echo "Stopping services started for the tests..."
    docker compose down decision-control database
  fi
}
trap cleanup EXIT

if dc_ready; then
  echo "✅ Decision Control already running at $DC_URL — reusing the live stack"
elif pgrep -f process-compose >/dev/null 2>&1; then
  # The dev stack is starting; let it own the containers and just wait.
  echo "process-compose is running — waiting for its Decision Control..."
  wait_for "Decision Control ($DC_URL)" 600 dc_ready
else
  echo "Starting database and decision-control for the tests..."
  STARTED_SERVICES=1
  docker compose up --detach database decision-control
  wait_for "database ($DB_CONTAINER)" 120 db_ready
  wait_for "Decision Control ($DC_URL)" 600 dc_ready
fi

# Upload/refresh the DMN models; the test profile resolves the runtime URLs
# itself (localhost:8880 defaults), so the script's exports are not needed.
./scripts/models-upload.sh

./scripts/make.sh test
