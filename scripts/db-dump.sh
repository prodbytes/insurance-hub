#!/usr/bin/env bash
# Snapshot the local dev database (shared by ih-vdn and Decision Control) to
# ih-data/insurancehub-dump.sql.
#
# The database process (process-compose.yaml) mounts ih-data/ as the postgres
# container's /docker-entrypoint-initdb.d, and the container is a throwaway
# (--rm, no volume), so initdb runs on every startup and replays this dump.
# Run this after changing state you want to keep across stack restarts —
# e.g. models uploaded/edited in Decision Control — and commit the dump.
set -euo pipefail

# --- Resolve repo root and load .env (if present) ---------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

# --- Configuration (override via .env; matches the process-compose database) -
DB_CONTAINER="${DB_CONTAINER:-insurance-hub-db}"
DB_USER="${QUARKUS_DATASOURCE_USERNAME:-quarkus}"
DB_NAME="${POSTGRES_DB:-insurancehub}"
DUMP_DIR="$REPO_ROOT/ih-data"
DUMP_FILE="$DUMP_DIR/insurancehub-dump.sql"

command -v docker >/dev/null 2>&1 || { echo "❌ docker is required" >&2; exit 1; }
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
  echo "❌ database container '$DB_CONTAINER' is not running (start the stack first)" >&2
  exit 1
fi

mkdir -p "$DUMP_DIR"

# Dump to a temp file and move into place, so a failed pg_dump never
# truncates an existing good dump. --no-owner keeps the restore independent
# of the dumping role (initdb.d scripts run as POSTGRES_USER anyway).
TMP_FILE="$(mktemp "$DUMP_DIR/.insurancehub-dump.XXXXXX")"
trap 'rm -f "$TMP_FILE"' EXIT
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" --no-owner "$DB_NAME" > "$TMP_FILE"
# mktemp creates mode 600; the container's postgres user (a different uid)
# must be able to read the mounted file.
chmod 644 "$TMP_FILE"
mv "$TMP_FILE" "$DUMP_FILE"
trap - EXIT

echo "✅ dumped $DB_NAME to ${DUMP_FILE#"$REPO_ROOT"/} ($(du -h "$DUMP_FILE" | cut -f1)); it will be restored on the next database startup"
