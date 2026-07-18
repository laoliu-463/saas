#!/bin/bash
# Docker entrypoint script — sets psql variables for SQL init scripts.
# Runs as shell (sourced by postgres entrypoint) before any .sql files.
# Variable names must NOT start with PG; all others are allowed.

if [ -z "${ADMIN_PASSWORD}" ]; then
  echo "ERROR: ADMIN_PASSWORD is not set. Database seed scripts will fail fast." >&2
fi
export ADMIN_PASSWORD

# Compose mounts the repository SQL directory read-only at the source path.
# Stage a writable copy for psql \i includes and for apply-test-db-patches.ps1,
# which deliberately replaces /tmp/saas-db on later controlled patch runs.
DB_SOURCE_DIR="/opt/saas-db-source"
DB_STAGE_DIR="/tmp/saas-db"
if [ -d "${DB_SOURCE_DIR}" ]; then
  rm -rf "${DB_STAGE_DIR}"
  mkdir -p "${DB_STAGE_DIR}"
  cp -R "${DB_SOURCE_DIR}/." "${DB_STAGE_DIR}/"
fi
