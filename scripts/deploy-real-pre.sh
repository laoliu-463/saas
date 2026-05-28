#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${ENV_FILE:-.env.real-pre}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-saas}"
RUN_GIT_PULL="${GIT_PULL:-false}"

if [ "${1:-}" = "--pull" ]; then
  RUN_GIT_PULL="true"
fi

cd "${REPO_ROOT}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: missing ${ENV_FILE}. Copy .env.real-pre.example to .env.real-pre and fill real values first." >&2
  exit 1
fi

export ENV_FILE COMPOSE_FILE
export COMPOSE_PROJECT_NAME="${PROJECT_NAME}"
export REAL_PRE_ENV_FILE="${ENV_FILE}"

if [ "${RUN_GIT_PULL}" = "true" ]; then
  echo "Running git pull --ff-only ..."
  git pull --ff-only
fi

echo "Validating docker compose config ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" config >/dev/null

echo "Starting real-pre stack ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" up -d --build

echo "Current containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

"${SCRIPT_DIR}/health-check.sh"
