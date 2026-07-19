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

get_env() {
  local key="$1"
  local default_value="${2:-}"
  if [ ! -f "${ENV_FILE}" ]; then
    printf '%s' "${default_value}"
    return
  fi
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

cd "${REPO_ROOT}"

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(get_env COMPOSE_PROJECT_NAME saas-active)}"
BACKEND_PORT="$(get_env BACKEND_HOST_PORT 8081)"
FRONTEND_PORT="$(get_env FRONTEND_HOST_PORT 3001)"
BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}/api/system/health"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}/healthz"
FRONTEND_VERSION_URL="http://127.0.0.1:${FRONTEND_PORT}/version.json"
EXPECTED_GIT_SHA="${EXPECTED_GIT_SHA:-}"
EXPECTED_BACKEND_DIGEST="${EXPECTED_BACKEND_DIGEST:-}"
EXPECTED_DATABASE_MIGRATION_VERSION="${EXPECTED_DATABASE_MIGRATION_VERSION:-}"
EXPECTED_FLYWAY_VERSION="${EXPECTED_FLYWAY_VERSION:-}"

echo "Waiting for backend: ${BACKEND_URL}"
backend_ok=false
backend_json=''
for _ in $(seq 1 60); do
  backend_json="$(curl -fsS "${BACKEND_URL}" 2>/dev/null || true)"
  if printf '%s' "$backend_json" | grep -q '"status":"UP"'; then
    backend_ok=true
    break
  fi
  sleep 2
done

if [ "${backend_ok}" != "true" ]; then
  echo "FAIL: backend health check failed. Recent backend logs:" >&2
  docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" logs --tail=300 backend-real-pre >&2 || true
  exit 1
fi

if [ -n "$EXPECTED_GIT_SHA" ]; then
  command -v jq >/dev/null 2>&1 || { echo 'FAIL: version-aware health check requires jq.' >&2; exit 1; }
  [[ "$EXPECTED_GIT_SHA" =~ ^[0-9a-f]{40}$ ]] || { echo 'FAIL: EXPECTED_GIT_SHA must be a full SHA.' >&2; exit 1; }
  jq -e \
    --arg sha "$EXPECTED_GIT_SHA" \
    --arg digest "$EXPECTED_BACKEND_DIGEST" \
    --arg migration "$EXPECTED_DATABASE_MIGRATION_VERSION" \
    --arg flyway "$EXPECTED_FLYWAY_VERSION" \
    '.status == "UP" and .gitSha == $sha and .imageDigest == $digest and
     .databaseMigrationVersion == $migration and .flywayVersion == $flyway' \
    <<<"$backend_json" >/dev/null || { echo 'FAIL: backend runtime version mismatch.' >&2; exit 1; }
fi

echo "Waiting for frontend: ${FRONTEND_URL}"
frontend_ok=false
for _ in $(seq 1 60); do
  if curl -fsS "${FRONTEND_URL}" | grep -q 'ok'; then
    frontend_ok=true
    break
  fi
  sleep 2
done

if [ "${frontend_ok}" != "true" ]; then
  echo "FAIL: frontend health check failed. Recent frontend logs:" >&2
  docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" logs --tail=300 frontend-real-pre >&2 || true
  exit 1
fi

if [ -n "$EXPECTED_GIT_SHA" ]; then
  frontend_json="$(curl -fsS "$FRONTEND_VERSION_URL")"
  jq -e --arg sha "$EXPECTED_GIT_SHA" '.gitSha == $sha' <<<"$frontend_json" >/dev/null \
    || { echo 'FAIL: frontend runtime version mismatch.' >&2; exit 1; }
fi

echo "PASS: real-pre backend and frontend are healthy."
