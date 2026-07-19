#!/usr/bin/env bash
set -Eeuo pipefail

TARGET_SHA="${1:-}"
MANIFEST_PATH="${2:-}"
RELEASE_ROOT="${RELEASE_ROOT:-/opt/saas/releases}"
REAL_PRE_ENV_FILE="${REAL_PRE_ENV_FILE:-/opt/saas/env/.env.real-pre}"
PROJECT_NAME="${REAL_PRE_COMPOSE_PROJECT:-saas-active}"
RELEASE_COMPOSE_SOURCE="${RELEASE_COMPOSE_SOURCE:-docker-compose.real-pre.release.yml}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8081/api/system/health}"
FRONTEND_VERSION_URL="${FRONTEND_VERSION_URL:-http://127.0.0.1:3001/version.json}"
ROLLBACK_APPROVED="${ROLLBACK_APPROVED:-false}"
RELEASE_CONTROLLER="${RELEASE_CONTROLLER:-}"

fail() {
  printf 'RELEASE_BLOCKED: %s\n' "$*" >&2
  exit 2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

require_command git
require_command docker
require_command jq
require_command curl
require_command sha256sum

[[ "$RELEASE_CONTROLLER" == 'jenkins' ]] || fail '禁止绕过 Jenkins 唯一发布控制器。'
[[ "$TARGET_SHA" =~ ^[0-9a-f]{40}$ ]] || fail '目标提交必须是 40 位小写完整 SHA。'
[[ -f "$MANIFEST_PATH" ]] || fail '发布清单不存在。'
[[ -f "$RELEASE_COMPOSE_SOURCE" ]] || fail '发布 Compose 不存在。'
[[ -f "$REAL_PRE_ENV_FILE" ]] || fail '固定 real-pre 环境文件不存在。'
[[ "$(git rev-parse HEAD)" == "$TARGET_SHA" ]] || fail '工作区 HEAD 与目标 SHA 不一致。'

jq -e \
  --arg sha "$TARGET_SHA" \
  '.schemaVersion == 1 and .environment == "real-pre" and
   .sourceBranch == "release/real-pre" and .gitSha == $sha' \
  "$MANIFEST_PATH" >/dev/null || fail '发布清单身份与目标提交不一致。'

BACKEND_IMAGE="$(jq -er '.backend.image' "$MANIFEST_PATH")"
BACKEND_DIGEST="$(jq -er '.backend.digest' "$MANIFEST_PATH")"
FRONTEND_IMAGE="$(jq -er '.frontend.image' "$MANIFEST_PATH")"
FRONTEND_DIGEST="$(jq -er '.frontend.digest' "$MANIFEST_PATH")"
DATABASE_MIGRATION_VERSION="$(jq -er '.databaseMigrationVersion' "$MANIFEST_PATH")"
FLYWAY_VERSION="$(jq -er '.flywayVersion' "$MANIFEST_PATH")"

[[ "$BACKEND_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || fail '后端 digest 非法。'
[[ "$FRONTEND_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || fail '前端 digest 非法。'
[[ "$BACKEND_IMAGE" == *":$TARGET_SHA" ]] || fail '后端镜像 tag 必须等于完整目标 SHA。'
[[ "$FRONTEND_IMAGE" == *":$TARGET_SHA" ]] || fail '前端镜像 tag 必须等于完整目标 SHA。'
[[ -n "$DATABASE_MIGRATION_VERSION" && "$DATABASE_MIGRATION_VERSION" != 'NOT_MANAGED' ]] || fail '数据库迁移版本不可验证。'
[[ -n "$FLYWAY_VERSION" && "$FLYWAY_VERSION" != 'NOT_MANAGED' ]] || fail 'Flyway 版本不可验证。'

CURRENT_POINTER="$RELEASE_ROOT/current.json"
PREVIOUS_POINTER="$RELEASE_ROOT/previous.json"
RELEASE_DIR="$RELEASE_ROOT/$TARGET_SHA"
DEPLOYMENT_RECORD="$RELEASE_DIR/deployment.json"
mkdir -p "$RELEASE_ROOT"

if [[ -f "$CURRENT_POINTER" ]]; then
  CURRENT_SHA="$(jq -er '.gitSha' "$CURRENT_POINTER")"
  [[ "$CURRENT_SHA" =~ ^[0-9a-f]{40}$ ]] || fail 'current.json 中的 SHA 非法。'
  if [[ "$CURRENT_SHA" != "$TARGET_SHA" ]] && ! git merge-base --is-ancestor "$CURRENT_SHA" "$TARGET_SHA"; then
    [[ "${ROLLBACK_APPROVED,,}" == 'true' ]] || fail '目标提交不是当前版本的后继；仅 ROLLBACK_APPROVED=true 可回滚。'
  fi
fi

validate_image() {
  local tagged_image="$1"
  local expected_digest="$2"
  local repository="${tagged_image%:$TARGET_SHA}"
  local revision
  revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$tagged_image")"
  [[ "$revision" == "$TARGET_SHA" ]] || fail "$tagged_image 的 OCI revision 与目标 SHA 不一致。"
  docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$tagged_image" \
    | grep -Fx "$repository@$expected_digest" >/dev/null \
    || fail "$tagged_image 的本地 RepoDigest 与发布清单不一致。"
}

validate_image "$BACKEND_IMAGE" "$BACKEND_DIGEST"
validate_image "$FRONTEND_IMAGE" "$FRONTEND_DIGEST"

mkdir -p "$RELEASE_DIR"
if [[ -f "$RELEASE_DIR/release.json" ]]; then
  cmp -s "$MANIFEST_PATH" "$RELEASE_DIR/release.json" || fail '同一 SHA 的不可变发布清单已存在且内容不同。'
else
  install -m 0444 "$MANIFEST_PATH" "$RELEASE_DIR/release.json"
  install -m 0444 "$RELEASE_COMPOSE_SOURCE" "$RELEASE_DIR/docker-compose.yml"
fi

record_failed_attempt() {
  local exit_code="$1"
  local failed_line="$2"
  local attempts_dir="$RELEASE_DIR/attempts"
  local attempt_id="${BUILD_NUMBER:-manual}-$(date -u +%Y%m%dT%H%M%SZ)"
  set +e
  mkdir -p "$attempts_dir"
  jq -n \
    --arg deployment 'FAIL' \
    --arg gitSha "$TARGET_SHA" \
    --arg failedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson exitCode "$exit_code" \
    --argjson failedLine "$failed_line" \
    '{deployment:$deployment,gitSha:$gitSha,exitCode:$exitCode,failedLine:$failedLine,failedAt:$failedAt}' \
    > "$attempts_dir/$attempt_id.json.tmp"
  mv "$attempts_dir/$attempt_id.json.tmp" "$attempts_dir/$attempt_id.json"
  chmod 0444 "$attempts_dir/$attempt_id.json"
}

trap 'code=$?; record_failed_attempt "$code" "$LINENO"; exit "$code"' ERR

BACKEND_REPOSITORY="${BACKEND_IMAGE%:$TARGET_SHA}"
FRONTEND_REPOSITORY="${FRONTEND_IMAGE%:$TARGET_SHA}"
export BACKEND_IMAGE_REF="$BACKEND_REPOSITORY@$BACKEND_DIGEST"
export FRONTEND_IMAGE_REF="$FRONTEND_REPOSITORY@$FRONTEND_DIGEST"
export APP_GIT_SHA="$TARGET_SHA"
export APP_IMAGE_DIGEST="$BACKEND_DIGEST"
export APP_DATABASE_MIGRATION_VERSION="$DATABASE_MIGRATION_VERSION"
export APP_FLYWAY_VERSION="$FLYWAY_VERSION"
export REAL_PRE_ENV_FILE

compose_release() {
  docker compose \
    --env-file "$REAL_PRE_ENV_FILE" \
    --project-name "$PROJECT_NAME" \
    -f "$RELEASE_DIR/docker-compose.yml" "$@"
}

compose_release config --quiet
compose_release up -d --no-build --no-deps backend-real-pre
compose_release up -d --no-build --no-deps frontend-real-pre

verify_runtime_versions() {
  local backend_json frontend_json attempt
  for attempt in $(seq 1 60); do
    backend_json="$(curl -fsS --max-time 5 "$BACKEND_HEALTH_URL" 2>/dev/null || true)"
    frontend_json="$(curl -fsS --max-time 5 "$FRONTEND_VERSION_URL" 2>/dev/null || true)"
    if jq -e \
      --arg sha "$TARGET_SHA" \
      --arg digest "$BACKEND_DIGEST" \
      --arg migration "$DATABASE_MIGRATION_VERSION" \
      --arg flyway "$FLYWAY_VERSION" \
      '.status == "UP" and .gitSha == $sha and .imageDigest == $digest and
       .databaseMigrationVersion == $migration and .flywayVersion == $flyway' \
      <<<"$backend_json" >/dev/null 2>&1 \
      && jq -e --arg sha "$TARGET_SHA" '.gitSha == $sha' <<<"$frontend_json" >/dev/null 2>&1; then
      printf '%s\n' "$backend_json" > "$RELEASE_DIR/backend-health.json"
      printf '%s\n' "$frontend_json" > "$RELEASE_DIR/frontend-version.json"
      return 0
    fi
    sleep 5
  done
  printf '运行版本一致性验证失败；current.json 未更新。\n' >&2
  return 1
}

verify_runtime_versions
trap - ERR

jq -n \
  --arg status 'PASS' \
  --arg gitSha "$TARGET_SHA" \
  --arg backendDigest "$BACKEND_DIGEST" \
  --arg frontendDigest "$FRONTEND_DIGEST" \
  --arg databaseMigrationVersion "$DATABASE_MIGRATION_VERSION" \
  --arg flywayVersion "$FLYWAY_VERSION" \
  --arg deployedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{deployment:$status,gitSha:$gitSha,backendDigest:$backendDigest,
    frontendDigest:$frontendDigest,databaseMigrationVersion:$databaseMigrationVersion,
    flywayVersion:$flywayVersion,deployedAt:$deployedAt}' > "$DEPLOYMENT_RECORD.tmp"
mv "$DEPLOYMENT_RECORD.tmp" "$DEPLOYMENT_RECORD"
chmod 0444 "$DEPLOYMENT_RECORD"

if [[ -f "$CURRENT_POINTER" ]]; then
  cp "$CURRENT_POINTER" "$PREVIOUS_POINTER.tmp"
  mv "$PREVIOUS_POINTER.tmp" "$PREVIOUS_POINTER"
fi
cp "$RELEASE_DIR/release.json" "$CURRENT_POINTER.tmp"
mv "$CURRENT_POINTER.tmp" "$CURRENT_POINTER"

printf 'DEPLOYMENT=PASS gitSha=%s\n' "$TARGET_SHA"
