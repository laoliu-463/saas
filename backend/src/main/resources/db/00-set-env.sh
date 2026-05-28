#!/bin/bash
# Docker entrypoint script — sets psql variables for SQL init scripts.
# Runs as shell (sourced by postgres entrypoint) before any .sql files.
# Variable names must NOT start with PG; all others are allowed.

if [ -z "${ADMIN_PASSWORD}" ]; then
  echo "ERROR: ADMIN_PASSWORD is not set. Database seed scripts will fail fast." >&2
fi
export ADMIN_PASSWORD
