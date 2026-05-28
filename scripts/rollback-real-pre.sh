#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${ENV_FILE:-.env.real-pre}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
TARGET_REF="${1:-${ROLLBACK_REF:-HEAD~1}}"

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

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: missing ${ENV_FILE}." >&2
  exit 1
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: tracked working tree has uncommitted changes. Commit/stash them before rollback." >&2
  exit 1
fi

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(get_env COMPOSE_PROJECT_NAME saas)}"
export ENV_FILE COMPOSE_FILE
export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
export REAL_PRE_ENV_FILE="${ENV_FILE}"

echo "Backing up database before rollback ..."
"${SCRIPT_DIR}/backup-db.sh"

resolved_ref="$(git rev-parse --verify "${TARGET_REF}^{commit}")"
echo "Rolling back code to ${TARGET_REF} (${resolved_ref}) ..."
if git switch --detach "${resolved_ref}" >/dev/null 2>&1; then
  :
else
  git checkout --detach "${resolved_ref}"
fi

echo "Validating docker compose config ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" config >/dev/null

echo "Rebuilding and starting real-pre stack ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d --build

if [ -f "${SCRIPT_DIR}/health-check.sh" ]; then
  "${SCRIPT_DIR}/health-check.sh"
else
  backend_port="$(get_env BACKEND_HOST_PORT 8081)"
  frontend_port="$(get_env FRONTEND_HOST_PORT 3001)"
  curl -fsS "http://127.0.0.1:${backend_port}/api/system/health" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${frontend_port}" >/dev/null
  echo "PASS: rollback health checks passed."
fi
