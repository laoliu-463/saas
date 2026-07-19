#!/usr/bin/env bash
set -Eeuo pipefail

MIGRATION_PATHS=(
  'backend/src/main/resources/db'
  'scripts/run-real-pre-db-migrations.sh'
)

fail() {
  printf 'MIGRATION_DECISION_BLOCKED: %s\n' "$*" >&2
  exit 2
}

if [[ "${1:-}" == '--paths' ]]; then
  printf '%s\n' "${MIGRATION_PATHS[@]}"
  exit 0
fi

TARGET_SHA="${1:-}"
BASE_SHA="${2:-}"
ROLLBACK_APPROVED="${ROLLBACK_APPROVED:-false}"

[[ "$TARGET_SHA" =~ ^[0-9a-f]{40}$ ]] || fail '目标提交必须是 40 位小写完整 SHA。'
git cat-file -e "$TARGET_SHA^{commit}" 2>/dev/null || fail '目标提交不在受控 Git 历史中。'

if [[ -z "$BASE_SHA" ]]; then
  printf 'true\tFIRST_RELEASE\n'
  exit 0
fi

[[ "$BASE_SHA" =~ ^[0-9a-f]{40}$ ]] || fail '当前部署提交必须是 40 位小写完整 SHA。'
git cat-file -e "$BASE_SHA^{commit}" 2>/dev/null || fail '当前部署提交不在受控 Git 历史中。'

if ! git merge-base --is-ancestor "$BASE_SHA" "$TARGET_SHA"; then
  [[ "${ROLLBACK_APPROVED,,}" == 'true' ]] || fail '非后继发布必须明确批准回滚。'
  printf 'false\tROLLBACK_FORWARD_ONLY\n'
  exit 0
fi

if git diff --quiet "$BASE_SHA" "$TARGET_SHA" -- "${MIGRATION_PATHS[@]}"; then
  printf 'false\tNO_MIGRATION_CHANGE\n'
else
  printf 'true\tMIGRATION_PATH_CHANGED\n'
fi
