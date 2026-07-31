#!/usr/bin/env bash
# Register (or update) the Debezium postgres connector that streams database
# changes to Kafka. Idempotent: PUT /connectors/<name>/config creates the
# connector if missing and updates it otherwise. Run by process-compose
# (2-debezium-connector) once the Debezium Connect REST API is healthy.
# Stays in the foreground after registering, watching the connector status;
# exits nonzero if it fails or the REST API goes away so process-compose can
# restart it (which re-registers the connector).
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

# --- Configuration (override via .env) --------------------------------------
DEBEZIUM_URL="${DEBEZIUM_URL:-http://127.0.0.1:8083}"
CONNECTOR_NAME="${DEBEZIUM_CONNECTOR_NAME:-insurancehub-postgres}"
TOPIC_PREFIX="${DEBEZIUM_TOPIC_PREFIX:-insurancehub}"

# Connection settings mirror the compose `database` service: Debezium runs in
# a container on the same compose network, so it reaches postgres by service
# name, with the same credentials the database was created with (the bootstrap
# user is a superuser, which covers the REPLICATION privilege Debezium needs
# and the CREATE PUBLICATION ... FOR ALL TABLES that all_tables mode issues).
# No schema/table include lists: every table in every schema is streamed.
# Plain-JSON converters (schemas.enable=false) drop the Connect schema
# envelope so change events show the row fields directly (readable in the
# ih-audit message view) instead of burying them under "schema"/"payload".
echo "Registering connector '$CONNECTOR_NAME' at $DEBEZIUM_URL ..."
curl -sf -X PUT -H 'Content-Type: application/json' \
  "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/config" -d @- <<JSON
{
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "plugin.name": "pgoutput",
  "key.converter": "org.apache.kafka.connect.json.JsonConverter",
  "key.converter.schemas.enable": "false",
  "value.converter": "org.apache.kafka.connect.json.JsonConverter",
  "value.converter.schemas.enable": "false",
  "database.hostname": "database",
  "database.port": "5432",
  "database.user": "${QUARKUS_DATASOURCE_USERNAME:-quarkus}",
  "database.password": "${QUARKUS_DATASOURCE_PASSWORD:-quarkus}",
  "database.dbname": "${POSTGRES_DB:-insurancehub}",
  "topic.prefix": "$TOPIC_PREFIX",
  "publication.autocreate.mode": "all_tables",
  "decimal.handling.mode": "double"
}
JSON
echo

# --- Wait until the connector task reports RUNNING ---------------------------
connector_state() {
  curl -sf "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/status" \
    | grep -o '"state":"[A-Z]*"' | head -1 | cut -d'"' -f4 || true
}

state=""
for _ in $(seq 1 30); do
  state="$(connector_state)"
  case "$state" in
    RUNNING) echo "Connector '$CONNECTOR_NAME' is RUNNING."; break ;;
    FAILED)  echo "Connector '$CONNECTOR_NAME' FAILED:" >&2
             curl -s "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/status" >&2
             exit 1 ;;
    *)       sleep 2 ;;
  esac
done
if [ "$state" != "RUNNING" ]; then
  echo "Connector '$CONNECTOR_NAME' did not reach RUNNING in time." >&2
  exit 1
fi

# --- Foreground watch ---------------------------------------------------------
# Poll the connector so the process-compose entry stays Running instead of
# Completed. Exit nonzero on FAILED or an unreachable REST API; the restart
# policy then reruns this script, re-registering the connector.
while sleep 10; do
  state="$(connector_state)"
  case "$state" in
    RUNNING) ;;
    FAILED)  echo "Connector '$CONNECTOR_NAME' FAILED:" >&2
             curl -s "$DEBEZIUM_URL/connectors/$CONNECTOR_NAME/status" >&2
             exit 1 ;;
    "")      echo "Debezium REST API unreachable at $DEBEZIUM_URL." >&2
             exit 1 ;;
    *)       echo "Connector '$CONNECTOR_NAME' state: $state" ;;
  esac
done
