#!/usr/bin/env sh
set -eu

REAL_PRE_COMPOSE_FILE="${REAL_PRE_COMPOSE_FILE:-${COMPOSE_FILE:-docker-compose.real-pre.yml}}"
REAL_PRE_COMPOSE_ENV="${REAL_PRE_COMPOSE_ENV:-${COMPOSE_ENV:-.env.real-pre}}"
REAL_PRE_COMPOSE_PROJECT="${REAL_PRE_COMPOSE_PROJECT:-${COMPOSE_PROJECT_NAME:-saas}}"
DB_MIGRATION_FILE="${DB_MIGRATION_FILE:-/docker-entrypoint-initdb.d/99-migrate-all.sql}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres-real-pre}"

compose() {
  docker compose \
    --env-file "$REAL_PRE_COMPOSE_ENV" \
    --project-name "$REAL_PRE_COMPOSE_PROJECT" \
    -f "$REAL_PRE_COMPOSE_FILE" "$@"
}

echo "Starting ${POSTGRES_SERVICE} before database migration ..."
compose up -d "$POSTGRES_SERVICE"

echo "Waiting for ${POSTGRES_SERVICE} readiness ..."
ready=false
for i in $(seq 1 60); do
  if compose exec -T "$POSTGRES_SERVICE" sh -lc 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

if [ "$ready" != "true" ]; then
  echo "${POSTGRES_SERVICE} did not become ready before migration"
  compose logs --tail=200 "$POSTGRES_SERVICE"
  exit 1
fi

echo "Running managed aggregate migration: $DB_MIGRATION_FILE"
compose exec -T \
  -e DB_MIGRATION_FILE="$DB_MIGRATION_FILE" \
  "$POSTGRES_SERVICE" sh -s <<'CONTAINER_SH'
set -eu
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is required for migration scripts}"
: "${DB_MIGRATION_FILE:?DB_MIGRATION_FILE is required}"

if [ ! -f "$DB_MIGRATION_FILE" ]; then
  echo "Migration file not found: $DB_MIGRATION_FILE"
  exit 2
fi

checksum="$(sha256sum "$DB_MIGRATION_FILE" | awk "{print \$1}")"
version="$(basename "$DB_MIGRATION_FILE")"
safe_version="$(printf "%s" "$version" | sed "s/'/''/g")"

psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "
CREATE TABLE IF NOT EXISTS schema_migration_log (
  version VARCHAR(200) PRIMARY KEY,
  checksum VARCHAR(64) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);"

psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f "$DB_MIGRATION_FILE"

psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 <<SQL
INSERT INTO schema_migration_log(version, checksum, applied_at)
VALUES ('$safe_version', '$checksum', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO UPDATE
SET checksum = EXCLUDED.checksum,
    applied_at = CURRENT_TIMESTAMP;
SQL
CONTAINER_SH

echo "Database migration completed."
