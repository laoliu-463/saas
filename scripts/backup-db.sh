#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${ENV_FILE:-.env.real-pre}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
BACKUP_DIR="${BACKUP_DIR:-/opt/saas/backup}"
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

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(get_env COMPOSE_PROJECT_NAME saas)}"
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

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="${BACKUP_DIR}/${DB_NAME}-${timestamp}.dump"
tmp_file="${backup_file}.tmp"

if [ -e "${backup_file}" ] || [ -e "${tmp_file}" ]; then
  echo "ERROR: backup file already exists: ${backup_file}" >&2
  exit 1
fi

cleanup() {
  rm -f "${tmp_file}"
}
trap cleanup EXIT

echo "Creating PostgreSQL backup: ${backup_file}"
docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" -f "${COMPOSE_FILE}" \
  exec -T -e PGPASSWORD="${DB_PASSWORD}" "${POSTGRES_SERVICE}" \
  pg_dump -U "${DB_USER}" -d "${DB_NAME}" -F c > "${tmp_file}"

mv "${tmp_file}" "${backup_file}"
trap - EXIT

echo "PASS: database backup created at ${backup_file}"
