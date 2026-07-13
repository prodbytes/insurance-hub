# ih-data

Database seed data for local dev.

This directory is mounted as the postgres container's
`/docker-entrypoint-initdb.d` (see the `1-database` process in
`process-compose.yaml`). The database container is a throwaway (`--rm`, no
volume), so postgres runs initdb on every startup and replays any `*.sql`
files here — restoring the state captured in `insurancehub-dump.sql`.

Refresh the dump with `./scripts/db-dump.sh` while the stack is running
(e.g. after uploading or editing models in Decision Control), then commit it
so the state survives stack restarts and fresh Codespaces.
