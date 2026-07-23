#!/usr/bin/env bash
set -eu

: "${BACKEND_IMAGE:?BACKEND_IMAGE is required}"
: "${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
: "${FULL_COMMIT:?FULL_COMMIT is required}"

pull_timeout_seconds="${PULL_TIMEOUT_SECONDS:-900}"
pull_attempts="${PULL_ATTEMPTS:-2}"

case "$pull_timeout_seconds" in
  ''|*[!0-9]*)
    echo "ERROR: PULL_TIMEOUT_SECONDS must be a positive integer."
    exit 1
    ;;
esac
case "$pull_attempts" in
  ''|*[!0-9]*)
    echo "ERROR: PULL_ATTEMPTS must be a positive integer."
    exit 1
    ;;
esac
if [ "$pull_timeout_seconds" -le 0 ] || [ "$pull_attempts" -le 0 ]; then
  echo "ERROR: PULL_TIMEOUT_SECONDS and PULL_ATTEMPTS must be greater than zero."
  exit 1
fi

for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
  if ! printf '%s' "$image" | grep -Eq '^[^@[:space:]]+@sha256:[0-9a-f]{64}$'; then
    echo "ERROR: immutable image must use repository@sha256:digest."
    exit 1
  fi
done

if ! printf '%s' "$FULL_COMMIT" | grep -Eq '^[0-9a-f]{40}$'; then
  echo "ERROR: FULL_COMMIT must be a full 40-character SHA."
  exit 1
fi

mkdir -p runtime/qa/out/jenkins

record_pull_diagnostics() {
  docker system df > runtime/qa/out/jenkins/docker-system-df.txt 2>&1 || true
  df -h /var/lib/docker > runtime/qa/out/jenkins/docker-disk-free.txt 2>&1 || df -h > runtime/qa/out/jenkins/docker-disk-free.txt 2>&1 || true
}

image_is_ready() {
  image="$1"
  revision="$(docker image inspect "$image" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' 2>/dev/null || true)"
  [ "$revision" = "$FULL_COMMIT" ] || return 1
  docker image inspect "$image" --format '{{join .RepoDigests "\n"}}' 2>/dev/null | grep -Fx "$image" >/dev/null
}

pull_image_with_retry() {
  image="$1"
  if image_is_ready "$image"; then
    echo "Immutable image already available locally: $image"
    return 0
  fi
  attempt=1
  while [ "$attempt" -le "$pull_attempts" ]; do
    echo "Pulling immutable image (attempt ${attempt}/${pull_attempts}): $image"
    if timeout --foreground --kill-after=30s "${pull_timeout_seconds}s" docker pull "$image"; then
      return 0
    else
      status=$?
    fi
    echo "Immutable image pull attempt ${attempt} failed or timed out (status=${status})."
    if [ "$attempt" -lt "$pull_attempts" ]; then
      echo "Retrying with Docker's partially downloaded layer cache."
      sleep 5
    fi
    attempt=$((attempt + 1))
  done
  echo "ERROR: failed to pull immutable image after ${pull_attempts} attempts: $image"
  record_pull_diagnostics
  return 1
}

command -v timeout >/dev/null 2>&1 || {
  echo "ERROR: GNU timeout is required for bounded immutable image pulls."
  exit 1
}

pull_image_with_retry "$BACKEND_IMAGE"
pull_image_with_retry "$FRONTEND_IMAGE"

backend_revision="$(docker image inspect "$BACKEND_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
frontend_revision="$(docker image inspect "$FRONTEND_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
test "$backend_revision" = "$FULL_COMMIT"
test "$frontend_revision" = "$FULL_COMMIT"

docker image inspect "$BACKEND_IMAGE" --format '{{.Id}}' > runtime/qa/out/jenkins/backend-local-image-id.txt
docker image inspect "$FRONTEND_IMAGE" --format '{{.Id}}' > runtime/qa/out/jenkins/frontend-local-image-id.txt
docker image inspect "$BACKEND_IMAGE" --format '{{join .RepoDigests "\n"}}' > runtime/qa/out/jenkins/backend-repo-digests.txt
docker image inspect "$FRONTEND_IMAGE" --format '{{join .RepoDigests "\n"}}' > runtime/qa/out/jenkins/frontend-repo-digests.txt
grep -Fx "$BACKEND_IMAGE" runtime/qa/out/jenkins/backend-repo-digests.txt
grep -Fx "$FRONTEND_IMAGE" runtime/qa/out/jenkins/frontend-repo-digests.txt
