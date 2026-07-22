#!/usr/bin/env bash
set -euo pipefail

if [ "${BREAK_GLASS_APPROVED:-false}" != "true" ] || [ -z "${BREAK_GLASS_REASON:-}" ]; then
  echo "Direct real-pre deployment is retired. Use Jenkins saas-real-pre-cd; set BREAK_GLASS_APPROVED=true and BREAK_GLASS_REASON only for an approved emergency recovery." >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

APP_DIR="${APP_DIR:-${DEFAULT_APP_DIR}}"
ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}"
if [ ! -f "${ENV_FILE}" ] && [ -f "${APP_DIR}/.env.real-pre" ]; then
  ENV_FILE="${APP_DIR}/.env.real-pre"
fi
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
PROJECT_NAME="${PROJECT_NAME:-${COMPOSE_PROJECT_NAME:-saas-active}}"
BACKUP_DIR="${BACKUP_DIR:-/opt/saas/backups}"
EVIDENCE_ROOT="${EVIDENCE_ROOT:-/opt/saas/runtime/qa/out/deploy-real-pre-${TIMESTAMP}}"

POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres-real-pre}"
REDIS_SERVICE="${REDIS_SERVICE:-redis-real-pre}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend-real-pre}"
FRONTEND_SERVICE="${FRONTEND_SERVICE:-frontend-real-pre}"

cd "${APP_DIR}"

mkdir -p "${EVIDENCE_ROOT}" "${BACKUP_DIR}" /opt/saas/logs

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: missing ${ENV_FILE}. Copy .env.real-pre.example to /opt/saas/env/.env.real-pre and fill real values first." >&2
  exit 1
fi

if [ ! -f "${COMPOSE_FILE}" ]; then
  echo "ERROR: missing compose file ${COMPOSE_FILE} in ${APP_DIR}." >&2
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

compose() {
  docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}

validate_real_pre_env() {
  expect_env COMPOSE_PROJECT_NAME saas-active
  if [ "${PROJECT_NAME}" != "saas-active" ]; then
    echo "ERROR: PROJECT_NAME/COMPOSE_PROJECT_NAME must be saas-active, got ${PROJECT_NAME}." >&2
    exit 1
  fi

  expect_env SPRING_PROFILES_ACTIVE real-pre
  expect_env APP_TEST_ENABLED false
  expect_env DOUYIN_TEST_ENABLED false
  expect_env DOUYIN_REAL_UPSTREAM_MODE live
  expect_env DB_NAME saas_real_pre
  expect_env ORDER_SYNC_ENABLED true
  expect_env TALENT_COLLECT_MODE api
  expect_env TALENT_COLLECT_API_ENABLED true
  expect_env TALENT_PUBLIC_PAGE_CRAWL_ENABLED false
  expect_env LOGISTICS_PROVIDER kuaidi100
  expect_env LOGISTICS_KD100_ENABLED true
  expect_env LOGISTICS_KD100_SUBSCRIBE_ENABLED true
  expect_env LOGISTICS_SYNC_ENABLED true
  expect_env EXCLUSIVE_ENABLED false

  promotion_write="$(get_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED false | tr '[:upper:]' '[:lower:]')"
  allow_promotion_write="$(get_env ALLOW_REAL_PROMOTION_WRITE false | tr '[:upper:]' '[:lower:]')"
  if [ "${promotion_write}" = "true" ] && [ "${allow_promotion_write}" != "true" ]; then
    echo "ERROR: DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true requires ALLOW_REAL_PROMOTION_WRITE=true." >&2
    exit 1
  fi
  if [ "${allow_promotion_write}" = "true" ] && [ "${promotion_write}" != "true" ]; then
    echo "ERROR: ALLOW_REAL_PROMOTION_WRITE=true requires DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true." >&2
    exit 1
  fi
  if [ "${promotion_write}" = "true" ]; then
    if [ "${REAL_PROMOTION_WRITE_CONFIRMED:-false}" != "true" ]; then
      echo "ERROR: real promotion write is disabled by default for real-pre controlled deployment. Set REAL_PROMOTION_WRITE_CONFIRMED=true only after manual approval." >&2
      exit 1
    fi
    echo "WARN: real Douyin promotion write is enabled for a manually approved write window."
  else
    expect_env DOUYIN_REAL_PROMOTION_WRITE_ENABLED false
    expect_env ALLOW_REAL_PROMOTION_WRITE false
  fi

  for key in \
    DB_PASSWORD \
    ADMIN_PASSWORD \
    REDIS_PASSWORD \
    JWT_SECRET \
    CORS_ALLOWED_ORIGIN_PATTERNS \
    DOUYIN_BASE_URL \
    DOUYIN_APP_ID \
    DOUYIN_CLIENT_KEY \
    DOUYIN_CLIENT_SECRET \
    DOUYIN_OAUTH_REDIRECT_URI \
    DOUYIN_OAUTH_FRONTEND_SUCCESS_URL \
    DOUYIN_OAUTH_FRONTEND_FAILURE_URL; do
    require_env "${key}"
  done

  if [ "$(get_env LOGISTICS_KD100_ENABLED false | tr '[:upper:]' '[:lower:]')" = "true" ]; then
    require_env LOGISTICS_KD100_CUSTOMER
    require_env LOGISTICS_KD100_KEY
  fi

  if [ "$(get_env LOGISTICS_KD100_SUBSCRIBE_ENABLED false | tr '[:upper:]' '[:lower:]')" = "true" ]; then
    require_env LOGISTICS_KD100_CALLBACK_URL
    require_env LOGISTICS_KD100_CALLBACK_SALT
  fi
}

echo "Evidence directory: ${EVIDENCE_ROOT}"

echo "Validating real-pre environment before git pull ..."
validate_real_pre_env

before_commit="$(git rev-parse --short HEAD 2>/dev/null || true)"
printf '%s\n' "${before_commit:-unknown}" > "${EVIDENCE_ROOT}/commit-before.txt"
echo "Commit before deploy: ${before_commit:-unknown}"

echo "Running git pull --ff-only ..."
git pull --ff-only

after_commit="$(git rev-parse --short HEAD 2>/dev/null || true)"
printf '%s\n' "${after_commit:-unknown}" > "${EVIDENCE_ROOT}/commit-after.txt"
echo "Commit after deploy: ${after_commit:-unknown}"

export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
export REAL_PRE_ENV_FILE="${ENV_FILE}"

echo "Rendering docker compose config ..."
compose config > "${EVIDENCE_ROOT}/docker-compose.config.yml"

db_name="$(get_env DB_NAME saas_real_pre)"
db_user="$(get_env DB_USER saas)"
db_password="$(get_env DB_PASSWORD)"
postgres_cid="$(compose ps -q "${POSTGRES_SERVICE}" 2>/dev/null || true)"

if [ -n "${postgres_cid}" ] && [ "$(docker inspect -f '{{.State.Running}}' "${postgres_cid}" 2>/dev/null || true)" = "true" ]; then
  backup_file="${BACKUP_DIR}/${db_name}-${TIMESTAMP}.dump"
  echo "Backing up database to ${backup_file} ..."
  compose exec -T -e PGPASSWORD="${db_password}" "${POSTGRES_SERVICE}" \
    pg_dump -U "${db_user}" -d "${db_name}" -F c > "${backup_file}"
  printf '%s\n' "${backup_file}" > "${EVIDENCE_ROOT}/backup-file.txt"
else
  echo "WARN: ${POSTGRES_SERVICE} is not running yet; skip database backup for first deployment." | tee "${EVIDENCE_ROOT}/backup-skip.txt"
fi

echo "Starting PostgreSQL and Redis ..."
compose up -d "${POSTGRES_SERVICE}" "${REDIS_SERVICE}"

if [ -x "${SCRIPT_DIR}/run-real-pre-db-migrations.sh" ]; then
  echo "Running database migrations ..."
  REAL_PRE_COMPOSE_ENV="${ENV_FILE}" \
  REAL_PRE_COMPOSE_PROJECT="${PROJECT_NAME}" \
  REAL_PRE_COMPOSE_FILE="${COMPOSE_FILE}" \
    "${SCRIPT_DIR}/run-real-pre-db-migrations.sh" | tee "${EVIDENCE_ROOT}/db-migrations.log"
else
  echo "WARN: scripts/run-real-pre-db-migrations.sh is not executable; skip explicit migration." | tee "${EVIDENCE_ROOT}/db-migrations-skip.txt"
fi

echo "Building and starting real-pre stack ..."
compose up -d --build

compose ps > "${EVIDENCE_ROOT}/docker-compose.ps.txt"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" > "${EVIDENCE_ROOT}/docker.ps.txt"

backend_port="$(get_env BACKEND_HOST_PORT 8081)"
frontend_port="$(get_env FRONTEND_HOST_PORT 3001)"
backend_health_url="http://127.0.0.1:${backend_port}/api/system/health"
frontend_health_url="http://127.0.0.1:${frontend_port}/healthz"

echo "Checking backend health: ${backend_health_url}"
backend_ok=false
for _ in $(seq 1 60); do
  if curl -fsS "${backend_health_url}" | tee "${EVIDENCE_ROOT}/backend-health.json" | grep -q '"status":"UP"'; then
    backend_ok=true
    break
  fi
  sleep 2
done

if [ "${backend_ok}" != "true" ]; then
  echo "ERROR: backend health check failed." >&2
  compose logs --tail=300 "${BACKEND_SERVICE}" > "${EVIDENCE_ROOT}/backend-health-failed.log" || true
  exit 1
fi

echo "Checking frontend health: ${frontend_health_url}"
frontend_ok=false
for _ in $(seq 1 60); do
  if curl -fsS "${frontend_health_url}" | tee "${EVIDENCE_ROOT}/frontend-health.txt" | grep -q 'ok'; then
    frontend_ok=true
    break
  fi
  sleep 2
done

if [ "${frontend_ok}" != "true" ]; then
  echo "ERROR: frontend health check failed." >&2
  compose logs --tail=300 "${FRONTEND_SERVICE}" > "${EVIDENCE_ROOT}/frontend-health-failed.log" || true
  exit 1
fi

compose logs --tail=500 > "${EVIDENCE_ROOT}/docker-compose.logs.txt" || true
compose logs --tail=300 "${BACKEND_SERVICE}" > "${EVIDENCE_ROOT}/backend-real-pre.log" || true
compose logs --tail=300 "${FRONTEND_SERVICE}" > "${EVIDENCE_ROOT}/frontend-real-pre.log" || true
compose logs --tail=300 "${POSTGRES_SERVICE}" > "${EVIDENCE_ROOT}/postgres-real-pre.log" || true
compose logs --tail=300 "${REDIS_SERVICE}" > "${EVIDENCE_ROOT}/redis-real-pre.log" || true

echo "PASS: real-pre controlled deployment completed."
echo "Evidence directory: ${EVIDENCE_ROOT}"
