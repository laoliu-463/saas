#!/usr/bin/env bash
set -u -o pipefail

# This helper is called only by the post-failure hook inside the Jenkins
# deployment lock. It never pulls source code or builds an image.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-/opt/saas/env/.env.real-pre}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.real-pre.yml}"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-saas-active}"
STATE_DIR="${RELEASE_STATE_DIR:-runtime/qa/out/jenkins/release-state}"
OLD_BACKEND_IMAGE="${ROLLBACK_BACKEND_IMAGE:-}"
OLD_FRONTEND_IMAGE="${ROLLBACK_FRONTEND_IMAGE:-}"
OLD_SOURCE_SHA="${ROLLBACK_SOURCE_MAIN_SHA:-}"

cd "${REPO_ROOT}"

if [ ! -f "${STATE_DIR}/deployment-started" ]; then
  echo "Rollback not required: deployment-started state is absent."
  exit 0
fi

mkdir -p "${STATE_DIR}"
touch "${STATE_DIR}/rollback-started"

failure=0
fail_step() {
  echo "ROLLBACK FAIL: $1" >&2
  failure=1
}

if ! printf '%s' "${OLD_SOURCE_SHA}" | grep -Eq '^[0-9a-f]{40}$'; then
  fail_step 'previous source SHA is not a full 40-character SHA'
fi
for image in "${OLD_BACKEND_IMAGE}" "${OLD_FRONTEND_IMAGE}"; do
  if ! printf '%s' "${image}" | grep -Eq '^[^@[:space:]]+@sha256:[0-9a-f]{64}$'; then
    fail_step 'previous image is not repository@sha256:digest'
  fi
done

if [ "${failure}" -eq 0 ]; then
  # The release-order guard already verified that these exact digests are the
  # running deployment content. Avoid a registry login in the failure hook;
  # the normal pull stage's credential scope has already ended.
  docker image inspect "${OLD_BACKEND_IMAGE}" >/dev/null || fail_step 'find previous backend image locally'
  docker image inspect "${OLD_FRONTEND_IMAGE}" >/dev/null || fail_step 'find previous frontend image locally'
fi

backend_digest="${OLD_BACKEND_IMAGE##*@}"
compose_up() {
  local service="$1"
  local scheduling="$2"
  APP_SCHEDULING_ENABLED="${scheduling}" \
    IMAGE_TAG="${OLD_SOURCE_SHA}" \
    BACKEND_IMAGE="${OLD_BACKEND_IMAGE}" \
    FRONTEND_IMAGE="${OLD_FRONTEND_IMAGE}" \
    BACKEND_IMAGE_DIGEST="${backend_digest}" \
    COMPOSE_PROJECT_NAME="${PROJECT_NAME}" \
    docker compose --env-file "${ENV_FILE}" --project-name "${PROJECT_NAME}" \
      -f "${COMPOSE_FILE}" up -d --no-build --no-deps "${service}"
}

# Keep schedulers paused while both services are replaced. Always make a final
# scheduler-enabled attempt, even if one of the image swaps fails.
if ! compose_up backend-real-pre false; then
  fail_step 'restore previous backend with schedulers paused'
fi
if ! compose_up frontend-real-pre false; then
  fail_step 'restore previous frontend'
fi
if ! compose_up backend-real-pre true; then
  fail_step 'restore previous backend with schedulers enabled'
fi

if ! ENV_FILE="${ENV_FILE}" COMPOSE_FILE="${COMPOSE_FILE}" COMPOSE_PROJECT_NAME="${PROJECT_NAME}" \
  bash scripts/health-check.sh; then
  fail_step 'health check after rollback'
fi

if [ "${failure}" -ne 0 ]; then
  echo "Automatic rollback did not reach a proven healthy previous release." >&2
  echo "The deployment lock remains responsible for this post-failure hook; inspect Jenkins evidence before recovery." >&2
  exit 1
fi

touch "${STATE_DIR}/rollback-completed"
touch "${STATE_DIR}/schedulers-restored"
rm -f "${STATE_DIR}/schedulers-paused"
rm -f runtime/qa/out/jenkins/schedulers-paused
echo "PASS: previous real-pre release restored: ${OLD_SOURCE_SHA}"
