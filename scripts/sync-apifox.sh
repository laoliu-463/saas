#!/usr/bin/env bash
set -euo pipefail

OPENAPI_FILE="${APIFOX_OPENAPI_FILE:-docs/openapi/saas-openapi.json}"
APIFOX_BRANCH="${APIFOX_BRANCH:-ai-sync}"

if ! command -v apifox >/dev/null 2>&1; then
  echo "Apifox CLI is not installed or not in PATH." >&2
  exit 2
fi

if [[ -z "${APIFOX_ACCESS_TOKEN:-}" ]]; then
  echo "APIFOX_ACCESS_TOKEN is required; the token is intentionally not stored in the repository." >&2
  exit 2
fi

if [[ -z "${APIFOX_PROJECT_ID:-}" ]]; then
  echo "APIFOX_PROJECT_ID is required." >&2
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
