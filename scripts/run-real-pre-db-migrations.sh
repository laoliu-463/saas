#!/usr/bin/env sh
set -eu

REAL_PRE_COMPOSE_FILE="${REAL_PRE_COMPOSE_FILE:-${COMPOSE_FILE:-docker-compose.real-pre.yml}}"
if [ -n "${REAL_PRE_COMPOSE_ENV:-${COMPOSE_ENV:-}}" ]; then
  REAL_PRE_COMPOSE_ENV="${REAL_PRE_COMPOSE_ENV:-${COMPOSE_ENV}}"
elif [ -f "/opt/saas/env/.env.real-pre" ]; then
  REAL_PRE_COMPOSE_ENV="/opt/saas/env/.env.real-pre"
else
  REAL_PRE_COMPOSE_ENV=".env.real-pre"
fi
REAL_PRE_COMPOSE_PROJECT="${REAL_PRE_COMPOSE_PROJECT:-${COMPOSE_PROJECT_NAME:-saas-active}}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres-real-pre}"
MIGRATION_FILES="${MIGRATION_FILES:-backend/src/main/resources/db/migrate/V20260718_001__role_aware_attribution_schema.sql backend/src/main/resources/db/migrate/V20260718_002__activity_status_sync_schema.sql}"

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

echo "Preparing managed migration ledger ..."
compose exec -T "$POSTGRES_SERVICE" sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "
CREATE TABLE IF NOT EXISTS schema_migration_log (
  version VARCHAR(200) PRIMARY KEY,
  checksum VARCHAR(64) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);"'

for migration_file in $MIGRATION_FILES; do
  if [ ! -f "$migration_file" ]; then
    echo "Migration file not found: $migration_file" >&2
    exit 2
  fi

  version="$(basename "$migration_file")"
  checksum="$(sha256sum "$migration_file" | awk '{print $1}')"
  recorded_checksum="$(compose exec -T "$POSTGRES_SERVICE" sh -lc \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -v version="$1" -c "SELECT checksum FROM schema_migration_log WHERE version = :'"'"'version'"'"';"' sh "$version")"

  if [ -n "$recorded_checksum" ] && [ "$recorded_checksum" != "$checksum" ]; then
    echo "ERROR: migration checksum mismatch for $version" >&2
    exit 3
  fi

  if [ "$recorded_checksum" = "$checksum" ]; then
    echo "Migration already recorded with matching checksum: $version"
    continue
  fi

  echo "Applying additive migration: $version"
  compose exec -T "$POSTGRES_SERVICE" sh -lc \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < "$migration_file"
  compose exec -T "$POSTGRES_SERVICE" sh -lc \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v version="$1" -v checksum="$2" <<SQL
INSERT INTO schema_migration_log(version, checksum, applied_at)
VALUES (:'"'"'version'"'"', :'"'"'checksum'"'"', CURRENT_TIMESTAMP)
ON CONFLICT (version) DO NOTHING;
SQL
    ' sh "$version" "$checksum"
done

echo "Database migration completed."
