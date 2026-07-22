#!/usr/bin/env bash
# scripts/cd/release-real-pre.sh
#
# Canonical entry point for any mutation of the real-pre environment.
#
# Both the Jenkins real-pre CD job AND any BREAK-GLASS manual recovery MUST
# invoke this script (never call deploy-real-pre.sh / backup-db.sh /
# run-real-pre-db-migrations.sh / rollback-real-pre.sh directly).
#
# The flock wrapper guarantees:
#   1. No two mutating subcommands can race each other. Jenkins also holds its
#      `saas-real-pre-deploy` stage lock for the complete release when the
#      Lockable Resources plugin is enabled.
#   2. No concurrent BREAK-GLASS manual command can race a Jenkins run
#      (or another BREAK-GLASS run) and corrupt the running state.
#
# Usage:
#   scripts/cd/release-real-pre.sh preflight                # compose config + env checks
#   scripts/cd/release-real-pre.sh backup                   # pre-deploy DB backup
#   scripts/cd/release-real-pre.sh migrate                  # run compatible migrations
#   scripts/cd/release-real-pre.sh deploy                   # the full deploy flow
#   scripts/cd/release-real-pre.sh rollback <previous_sha>  # rollback to a SHA
#   scripts/cd/release-real-pre.sh rollback-immutable       # lock-scoped digest rollback
#
# Env (inherited or set by caller):
#   ENV_FILE         path to .env.real-pre (default /opt/saas/env/.env.real-pre)
#   APP_DIR          repo root (default: parent of this script)
#   PROJECT_NAME     compose project name (default: saas-active)
#   IMAGE_TAG        full 40-char commit SHA (REQUIRED for deploy/rollback)
#   DEPLOY_BRANCH    default release/real-pre
#
# Exit codes:
#   0   success
#   10  lock busy (another release is in progress; caller should retry)
#   11  another release was already running and we waited past LOCK_TIMEOUT_SEC
#   1   subcommand failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# --- Lock configuration ------------------------------------------------------
LOCK_FILE="${REAL_PRE_LOCK_FILE:-/var/lock/saas-real-pre-deploy.lock}"
LOCK_TIMEOUT_SEC="${REAL_PRE_LOCK_TIMEOUT_SEC:-1800}"   # 30 min default
LOCK_PID_FILE="${LOCK_FILE}.pid"

# --- Logging -----------------------------------------------------------------
log()  { printf '[cd] %s\n' "$*"; }
warn() { printf '[cd][warn] %s\n' "$*" >&2; }
die()  { printf '[cd][fatal] %s\n' "$*" >&2; exit 1; }

# --- Argument parsing --------------------------------------------------------
SUBCOMMAND="${1:-}"
case "${SUBCOMMAND}" in
  preflight|backup|migrate|deploy|rollback|rollback-immutable) ;;
  -h|--help|"")
    sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    die "Unknown subcommand: ${SUBCOMMAND}. Run with --help."
    ;;
esac
shift

# --- Preconditions -----------------------------------------------------------
[ "$(uname -s)" = "Linux" ] || die "flock requires Linux (current: $(uname -s))."
command -v flock >/dev/null 2>&1 || die "flock(1) not found in PATH."

# --- Lock acquisition --------------------------------------------------------
#
# We hold the lock for the duration of a single subcommand invocation.
# This is intentional: each step (preflight / backup / migrate / deploy /
# rollback) is independently atomic, so if Jenkins fails partway, the lock
# is released and the next attempt can re-acquire it cleanly.
#
# The non-blocking (-n) check happens first so we fail fast on contention;
# if LOCK_TIMEOUT_SEC > 0 we then fall back to a blocking wait so a queued
# pipeline does not abort on transient overlap.
if ! command -v flock >/dev/null 2>&1; then
  die "flock binary missing despite PATH lookup; refusing to proceed without real lock."
fi

# Ensure the lock directory is writable. /var/lock may be on tmpfs and not
# exist on first boot; fall back to a directory under /opt/saas/runtime.
if ! mkdir -p "$(dirname "${LOCK_FILE}")" 2>/dev/null; then
  LOCK_FILE="/opt/saas/runtime/saas-real-pre-deploy.lock"
  mkdir -p "$(dirname "${LOCK_FILE}")"
fi
: > "${LOCK_FILE}"

# Use a fixed file descriptor (9) so behaviour is identical on every bash
# version and the trap/cleanup is trivial.
RELEASE_LOCK_FD=9

acquire_lock() {
  # Open LOCK_FILE for read/write on FD 9. flock operates on the FD, not the
  # path, so subsequent flock calls on FD 9 refer to the same lock.
  eval "exec ${RELEASE_LOCK_FD}<>\"${LOCK_FILE}\""

  if ! flock -n "${RELEASE_LOCK_FD}"; then
    if [ "${LOCK_TIMEOUT_SEC}" -gt 0 ]; then
      warn "Another release holds ${LOCK_FILE}; waiting up to ${LOCK_TIMEOUT_SEC}s..."
      if ! flock -w "${LOCK_TIMEOUT_SEC}" "${RELEASE_LOCK_FD}"; then
        eval "exec ${RELEASE_LOCK_FD}>&-"
        return 11
      fi
    else
      eval "exec ${RELEASE_LOCK_FD}>&-"
      return 10
    fi
  fi

  # Record holder PID so BREAK-GLASS operators can identify the active run.
  echo "$$" > "${LOCK_PID_FILE}"
  trap 'release_lock' EXIT INT TERM
  return 0
}

release_lock() {
  # Drop the lock and close the FD. Safe to call from a trap.
  flock -u "${RELEASE_LOCK_FD}" 2>/dev/null || true
  eval "exec ${RELEASE_LOCK_FD}>&-"
  rm -f "${LOCK_PID_FILE}"
}

acquire_lock
acquire_rc=$?
if [ "${acquire_rc}" -ne 0 ]; then
  case "${acquire_rc}" in
    10) die "Lock busy (${LOCK_FILE}); another release is in progress. Refusing to start." ;;
    11) die "Timed out after ${LOCK_TIMEOUT_SEC}s waiting on ${LOCK_FILE}. Investigate before retrying." ;;
    *)  die "Failed to acquire ${LOCK_FILE} (rc=${acquire_rc})." ;;
  esac
fi
log "Acquired release lock: ${LOCK_FILE} (held by pid $$)"

# --- Subcommand dispatch -----------------------------------------------------
cd "${REPO_ROOT}"

case "${SUBCOMMAND}" in
  preflight)
    log "Subcommand: preflight"
    IMAGE_TAG="${IMAGE_TAG:-0000000000000000000000000000000000000000}" \
    PROJECT_NAME="${PROJECT_NAME:-saas-active}" \
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
      bash "${REPO_ROOT}/scripts/real-pre-startup-check.sh"
    ;;

  backup)
    log "Subcommand: backup"
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
      bash "${REPO_ROOT}/scripts/backup-db.sh" "$@"
    ;;

  migrate)
    log "Subcommand: migrate"
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
    IMAGE_TAG="${IMAGE_TAG:-}" \
    BACKEND_IMAGE="${BACKEND_IMAGE:-}" \
    FRONTEND_IMAGE="${FRONTEND_IMAGE:-}" \
    BACKEND_IMAGE_DIGEST="${BACKEND_IMAGE_DIGEST:-}" \
    REQUIRE_PINNED_IMAGE="${REQUIRE_PINNED_IMAGE:-false}" \
      bash "${REPO_ROOT}/scripts/run-real-pre-db-migrations.sh" "$@"
    ;;

  deploy)
    log "Subcommand: deploy (IMAGE_TAG=${IMAGE_TAG:-<unset>})"
    : "${IMAGE_TAG:?IMAGE_TAG must be set to the full 40-character commit SHA}"
    if ! printf '%s' "${IMAGE_TAG}" | grep -Eq '^[0-9a-f]{40}$'; then
      die "IMAGE_TAG must be the full 40-character commit SHA (got: ${IMAGE_TAG})"
    fi
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
    IMAGE_TAG="${IMAGE_TAG}" \
    PROJECT_NAME="${PROJECT_NAME:-saas-active}" \
      bash "${REPO_ROOT}/scripts/deploy-real-pre.sh"
    ;;

  rollback)
    PREVIOUS_SHA="${1:-${PREVIOUS_SHA:-}}"
    : "${PREVIOUS_SHA:?PREVIOUS_SHA must be set to a 40-character commit SHA}"
    if ! printf '%s' "${PREVIOUS_SHA}" | grep -Eq '^[0-9a-f]{40}$'; then
      die "PREVIOUS_SHA must be the full 40-character commit SHA (got: ${PREVIOUS_SHA})"
    fi
    log "Subcommand: rollback to ${PREVIOUS_SHA}"
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
    ROLLBACK_REF="${PREVIOUS_SHA}" \
    PROJECT_NAME="${PROJECT_NAME:-saas-active}" \
      bash "${REPO_ROOT}/scripts/rollback-real-pre.sh"
    ;;

  rollback-immutable)
    log "Subcommand: rollback-immutable"
    ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}" \
    COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}" \
    COMPOSE_PROJECT_NAME="${PROJECT_NAME:-saas-active}" \
    RELEASE_STATE_DIR="${RELEASE_STATE_DIR:-runtime/qa/out/jenkins/release-state}" \
    ROLLBACK_SOURCE_MAIN_SHA="${ROLLBACK_SOURCE_MAIN_SHA:-}" \
    ROLLBACK_BACKEND_IMAGE="${ROLLBACK_BACKEND_IMAGE:-}" \
    ROLLBACK_FRONTEND_IMAGE="${ROLLBACK_FRONTEND_IMAGE:-}" \
      bash "${REPO_ROOT}/scripts/cd/rollback-real-pre.sh"
    ;;
esac

log "Subcommand ${SUBCOMMAND} completed under lock ${LOCK_FILE}."
