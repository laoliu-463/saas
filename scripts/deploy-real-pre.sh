#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -n "${ENV_FILE:-}" ]; then
  ENV_FILE="${ENV_FILE}"
elif [ -f "/opt/saas/env/.env.real-pre" ]; then
  ENV_FILE="/opt/saas/env/.env.real-pre"
else
  ENV_FILE=".env.real-pre"
fi
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
RUN_GIT_PULL="${GIT_PULL:-false}"

if [ "${1:-}" = "--pull" ]; then
  RUN_GIT_PULL="true"
fi

cd "${REPO_ROOT}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: missing ${ENV_FILE}. Copy .env.real-pre.example to /opt/saas/env/.env.real-pre and fill real values first." >&2
  exit 1
fi

get_env() {
  local key="$1"
  local default_value="${2:-}"
  local value
  value="$(awk -F= -v key="${key}" '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    {
      k=$1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
      if (k == key) {
        v=$0
        sub(/^[^=]*=/, "", v)
        gsub(/\r$/, "", v)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", v)
        gsub(/^"|"$/, "", v)
        print v
        exit
      }
    }
  ' "${ENV_FILE}")"
  printf '%s' "${value:-${default_value}}"
}

require_env() {
  local key="$1"
  local value
  value="$(get_env "${key}")"
  if [ -z "${value}" ] \
    || [ "${value#MUST_CHANGE}" != "${value}" ] \
    || [ "${value#*YOUR_}" != "${value}" ] \
    || [ "${value#*PLACEHOLDER}" != "${value}" ]; then
    echo "ERROR: ${key} is empty or still uses a placeholder in ${ENV_FILE}." >&2
    exit 1
  fi
}

expect_env() {
  local key="$1"
  local expected="$2"
  local actual
  actual="$(get_env "${key}")"
  if [ "${actual}" != "${expected}" ]; then
    echo "ERROR: ${key} must be ${expected}, got ${actual:-<empty>}." >&2
    exit 1
  fi
}

expect_env COMPOSE_PROJECT_NAME saas-active
PROJECT_NAME="${PROJECT_NAME:-$(get_env COMPOSE_PROJECT_NAME)}"
if [ "${PROJECT_NAME}" != "saas-active" ]; then
  echo "ERROR: COMPOSE_PROJECT_NAME must be saas-active for controlled real-pre deployment, got ${PROJECT_NAME}." >&2
  exit 1
fi

expect_env SPRING_PROFILES_ACTIVE real-pre
expect_env APP_TEST_ENABLED false
expect_env DOUYIN_TEST_ENABLED false
expect_env DOUYIN_REAL_UPSTREAM_MODE live
expect_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED false
expect_env ORDER_SYNC_ENABLED true
expect_env DB_NAME saas_real_pre

for key in \
  DB_PASSWORD \
  ADMIN_PASSWORD \
  REDIS_PASSWORD \
  JWT_SECRET \
  CORS_ALLOWED_ORIGIN_PATTERNS \
  DOUYIN_APP_ID \
  DOUYIN_CLIENT_KEY \
  DOUYIN_CLIENT_SECRET \
  DOUYIN_OAUTH_REDIRECT_URI \
  DOUYIN_OAUTH_FRONTEND_SUCCESS_URL \
  DOUYIN_OAUTH_FRONTEND_FAILURE_URL; do
  require_env "${key}"
done

if [ "$(get_env LOGISTICS_KD100_ENABLED false)" = "true" ]; then
  require_env LOGISTICS_KD100_CUSTOMER
  require_env LOGISTICS_KD100_KEY
fi

if [ "$(get_env LOGISTICS_KD100_SUBSCRIBE_ENABLED false)" = "true" ]; then
  require_env LOGISTICS_KD100_CUSTOMER
  require_env LOGISTICS_KD100_KEY
  require_env LOGISTICS_KD100_CALLBACK_URL
  require_env LOGISTICS_KD100_CALLBACK_SALT
fi

export ENV_FILE COMPOSE_FILE
export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
export REAL_PRE_ENV_FILE="${ENV_FILE}"
mkdir -p /opt/saas/logs /opt/saas/backups /opt/saas/runtime/qa/out

if [ "${RUN_GIT_PULL}" = "true" ]; then
  echo "Running git pull --ff-only ..."
  git pull --ff-only
fi

current_commit="$(git rev-parse --short HEAD 2>/dev/null || true)"
if [ -n "${current_commit}" ]; then
  echo "Deploying commit ${current_commit}"
fi

echo "Validating docker compose config ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" config >/dev/null

echo "Starting PostgreSQL and Redis ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d postgres-real-pre redis-real-pre

echo "Backing up database before migration ..."
BACKUP_DIR="${BACKUP_DIR:-/opt/saas/backups}" "${SCRIPT_DIR}/backup-db.sh"

echo "Running database migrations ..."
REAL_PRE_COMPOSE_ENV="${ENV_FILE}" REAL_PRE_COMPOSE_PROJECT="${PROJECT_NAME}" REAL_PRE_COMPOSE_FILE="${COMPOSE_FILE}" \
  "${SCRIPT_DIR}/run-real-pre-db-migrations.sh"

echo "Building and starting backend/frontend ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d --build backend-real-pre frontend-real-pre

echo "Current containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

"${SCRIPT_DIR}/health-check.sh"

log_file="/opt/saas/logs/deploy-real-pre-$(date +%Y%m%d-%H%M%S).log"
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" logs --tail=300 > "${log_file}" || true
echo "Deployment log snapshot: ${log_file}"
