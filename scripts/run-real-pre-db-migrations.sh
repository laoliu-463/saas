#!/usr/bin/env sh
set -eu

# Flyway is the sole migration ledger. This script deliberately does not execute
# SQL files or maintain a second schema_migration_log table. It starts the
# already-built backend with schedulers paused so Spring/Flyway can apply and
# validate the versioned migrations, then verifies the history table read-only.
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
BACKEND_SERVICE="${BACKEND_SERVICE:-backend-real-pre}"
IMAGE_TAG="${IMAGE_TAG:-real-pre}"
BACKEND_IMAGE="${BACKEND_IMAGE:-colonel-saas/backend:${IMAGE_TAG}}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-colonel-saas/frontend:${IMAGE_TAG}}"
BACKEND_IMAGE_DIGEST="${BACKEND_IMAGE_DIGEST:-unknown}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8081/api/system/health}"

if [ "${REQUIRE_PINNED_IMAGE:-false}" = "true" ] && ! printf '%s' "$IMAGE_TAG" | grep -Eq '^[0-9a-fA-F]{40}$'; then
  echo "IMAGE_TAG must be a full commit SHA when pinned-image enforcement is enabled." >&2
  exit 2
fi

compose() {
  docker compose \
    --env-file "$REAL_PRE_COMPOSE_ENV" \
    --project-name "$REAL_PRE_COMPOSE_PROJECT" \
    -f "$REAL_PRE_COMPOSE_FILE" "$@"
}

echo "Starting ${POSTGRES_SERVICE} before Flyway migration ..."
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
  echo "${POSTGRES_SERVICE} did not become ready before Flyway migration" >&2
  compose logs --tail=200 "$POSTGRES_SERVICE" >&2 || true
  exit 1
fi

echo "Starting ${BACKEND_SERVICE} with schedulers paused; Spring/Flyway owns migration ..."
APP_SCHEDULING_ENABLED=false IMAGE_TAG="$IMAGE_TAG" BACKEND_IMAGE="$BACKEND_IMAGE" FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  BACKEND_IMAGE_DIGEST="$BACKEND_IMAGE_DIGEST" COMPOSE_PROJECT_NAME="$REAL_PRE_COMPOSE_PROJECT" \
  docker compose --env-file "$REAL_PRE_COMPOSE_ENV" --project-name "$REAL_PRE_COMPOSE_PROJECT" \
  -f "$REAL_PRE_COMPOSE_FILE" up -d --no-build --no-deps "$BACKEND_SERVICE"

echo "Waiting for backend readiness after Flyway ..."
backend_ready=false
for i in $(seq 1 90); do
  if curl -fsS "$BACKEND_HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    backend_ready=true
    break
  fi
  sleep 2
done

if [ "$backend_ready" != "true" ]; then
  echo "Backend did not become ready after Flyway migration" >&2
  compose logs --tail=300 "$BACKEND_SERVICE" >&2 || true
  exit 1
fi

echo "Checking Flyway history ..."
history="$(compose exec -T "$POSTGRES_SERVICE" sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT version || '\''|'\'' || description FROM flyway_schema_history WHERE success ORDER BY installed_rank;"')"
printf '%s\n' "$history"
printf '%s\n' "$history" | grep -q '^20260718\.001|role aware attribution schema$'
printf '%s\n' "$history" | grep -q '^20260718\.002|activity status sync schema$'
echo "Flyway migration completed and required versions are recorded."
