#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${APIFOX_ENV_FILE:-$ROOT_DIR/.env}"

load_apifox_env() {
  local key line value

  [[ -f "$ENV_FILE" ]] || return 0

  for key in APIFOX_ACCESS_TOKEN APIFOX_PROJECT_ID APIFOX_BRANCH APIFOX_OPENAPI_FILE; do
    if [[ -n "${!key:-}" ]]; then
      continue
    fi

    line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
    if [[ -z "$line" ]]; then
      continue
    fi

    value="${line#*=}"
    value="${value%$'\r'}"
    value="${value%\"}"
    value="${value#\"}"
    value="${value%\'}"
    value="${value#\'}"
    export "$key=$value"
  done
}

is_placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == __FILL_ME_* || "$value" == \<*\> || "$value" == 你的* ]]
}

load_apifox_env

OPENAPI_FILE="${APIFOX_OPENAPI_FILE:-docs/openapi/saas-openapi.json}"
APIFOX_BRANCH="${APIFOX_BRANCH:-ddd-sync}"

if ! command -v apifox >/dev/null 2>&1; then
  echo "Apifox CLI is not installed or not in PATH." >&2
  exit 2
fi

if is_placeholder "${APIFOX_ACCESS_TOKEN:-}"; then
  echo "APIFOX_ACCESS_TOKEN is required; fill the placeholder in .env or export a real token." >&2
  exit 2
fi

if is_placeholder "${APIFOX_PROJECT_ID:-}"; then
  echo "APIFOX_PROJECT_ID is required; fill the placeholder in .env or export a real project id." >&2
  exit 2
fi

if [[ ! -s "$OPENAPI_FILE" ]]; then
  echo "OpenAPI file not found or empty: $OPENAPI_FILE" >&2
  exit 2
fi

apifox login --with-token "$APIFOX_ACCESS_TOKEN"

args=(import --project "$APIFOX_PROJECT_ID" --format openapi --file "$OPENAPI_FILE")
if [[ -n "$APIFOX_BRANCH" ]]; then
  args+=(--branch "$APIFOX_BRANCH")
fi

apifox "${args[@]}"

echo "Apifox import submitted."
echo "File: $OPENAPI_FILE"
echo "Branch: ${APIFOX_BRANCH:-<default>}"
