#!/usr/bin/env bash
set -eu

: "${BACKEND_IMAGE:?BACKEND_IMAGE is required}"
: "${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
: "${FULL_COMMIT:?FULL_COMMIT is required}"

pull_timeout_seconds="${PULL_TIMEOUT_SECONDS:-900}"
pull_attempts="${PULL_ATTEMPTS:-2}"
pull_registry="${IMAGE_PULL_REGISTRY:-}"

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

if [ -n "$pull_registry" ] && ! printf '%s' "$pull_registry" | grep -Eq '^[A-Za-z0-9.-]+(:[0-9]+)?$'; then
  echo "ERROR: IMAGE_PULL_REGISTRY must be a registry host, without a scheme or path."
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
  # range/println avoids Docker 29.5 template parsing differences for \\n.
  docker image inspect "$image" --format '{{range .RepoDigests}}{{println .}}{{end}}' 2>/dev/null | grep -Fx "$image" >/dev/null
}

pull_image_ref() {
  image="$1"
  if [ -z "$pull_registry" ]; then
    printf '%s\n' "$image"
    return 0
  fi
  repository="${image#*/}"
  repository="${repository%@*}"
  digest="${image##*@}"
  printf '%s/%s@%s\n' "$pull_registry" "$repository" "$digest"
}

canonicalize_image_ref() {
  image="$1"
  source_image="$2"
  canonical_tag="${image%@*}:${FULL_COMMIT}"

  # A mirror is transport only. Tagging the pulled image under the canonical
  # repository causes Docker to register the same content digest for the
  # canonical repository, so Compose can continue to use repository@digest.
  docker tag "$source_image" "$canonical_tag"
  docker image inspect "$source_image" --format '{{range .RepoDigests}}{{println .}}{{end}}' \
    | grep -Fx "$source_image" >/dev/null
  image_is_ready "$image"
}

pull_image_with_retry() {
  image="$1"
  if image_is_ready "$image"; then
    echo "Immutable image already available locally: $image"
    return 0
  fi
  source_image="$(pull_image_ref "$image")"
  attempt=1
  while [ "$attempt" -le "$pull_attempts" ]; do
    echo "Pulling immutable image (attempt ${attempt}/${pull_attempts}): $image"
    if [ "$source_image" != "$image" ]; then
      echo "Using pull-only registry transport: $pull_registry"
    fi
    if timeout --foreground --kill-after=30s "${pull_timeout_seconds}s" docker pull "$source_image"; then
      if canonicalize_image_ref "$image" "$source_image"; then
        return 0
      fi
      echo "ERROR: pulled image failed canonical digest or revision verification: $source_image"
      status=1
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
docker image inspect "$BACKEND_IMAGE" --format '{{range .RepoDigests}}{{println .}}{{end}}' > runtime/qa/out/jenkins/backend-repo-digests.txt
docker image inspect "$FRONTEND_IMAGE" --format '{{range .RepoDigests}}{{println .}}{{end}}' > runtime/qa/out/jenkins/frontend-repo-digests.txt
grep -Fx "$BACKEND_IMAGE" runtime/qa/out/jenkins/backend-repo-digests.txt
grep -Fx "$FRONTEND_IMAGE" runtime/qa/out/jenkins/frontend-repo-digests.txt
