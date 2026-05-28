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

echo "Waiting for backend: ${BACKEND_URL}"
backend_ok=false
for _ in $(seq 1 60); do
  if curl -fsS "${BACKEND_URL}" | grep -q '"status":"UP"'; then
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

echo "PASS: real-pre backend and frontend are healthy."
