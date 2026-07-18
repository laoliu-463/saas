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
BACKUP_DIR="${BACKUP_DIR:-/opt/saas/backups}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres-real-pre}"

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

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(get_env COMPOSE_PROJECT_NAME saas-active)}"
DB_NAME="$(get_env DB_NAME saas_real_pre)"
DB_USER="$(get_env DB_USER saas)"
DB_PASSWORD="$(get_env DB_PASSWORD)"

if [ -z "${DB_PASSWORD}" ]; then
  echo "ERROR: DB_PASSWORD is empty in ${ENV_FILE}." >&2
  exit 1
fi

export REAL_PRE_ENV_FILE="${ENV_FILE}"

umask 077
mkdir -p "${BACKUP_DIR}"

echo "Waiting for ${POSTGRES_SERVICE} readiness before backup ..."
ready=false
for _ in $(seq 1 60); do
  if docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" \
    exec -T "${POSTGRES_SERVICE}" sh -lc 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

if [ "${ready}" != "true" ]; then
  echo "ERROR: ${POSTGRES_SERVICE} did not become ready before backup." >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="${BACKUP_DIR}/${DB_NAME}-${timestamp}.dump"
tmp_file="${backup_file}.tmp"
list_file="${backup_file}.list"
list_tmp="${list_file}.tmp"

if [ -e "${backup_file}" ] || [ -e "${tmp_file}" ] || [ -e "${list_file}" ] || [ -e "${list_tmp}" ]; then
  echo "ERROR: backup file already exists: ${backup_file}" >&2
  exit 1
fi

cleanup() {
  rm -f "${tmp_file}"
  rm -f "${list_tmp}"
}
trap cleanup EXIT

echo "Creating PostgreSQL backup: ${backup_file}"
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" \
  exec -T -e PGPASSWORD="${DB_PASSWORD}" "${POSTGRES_SERVICE}" \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" -F c > "${tmp_file}"

if [ ! -s "${tmp_file}" ]; then
  echo "ERROR: PostgreSQL backup is empty." >&2
  exit 1
fi

echo "Validating backup catalog with pg_restore --list ..."
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" \
  exec -T "${POSTGRES_SERVICE}" pg_restore --list < "${tmp_file}" > "${list_tmp}"
if [ ! -s "${list_tmp}" ]; then
  echo "ERROR: pg_restore returned an empty catalog." >&2
  exit 1
fi

mv "${tmp_file}" "${backup_file}"
mv "${list_tmp}" "${list_file}"
trap - EXIT

echo "PASS: database backup created and recovery catalog validated: ${backup_file}"
