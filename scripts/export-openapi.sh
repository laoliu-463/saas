#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENAPI_BASE_URL:-http://127.0.0.1:8080/api}"
GROUP="${OPENAPI_GROUP:-apifox}"
OUTPUT="${OPENAPI_OUTPUT:-docs/openapi/saas-openapi.json}"
TOKEN="${OPENAPI_BEARER_TOKEN:-}"

endpoint="${BASE_URL%/}/v3/api-docs"
if [[ -n "$GROUP" && "$GROUP" != "root" ]]; then
  endpoint="${endpoint}/${GROUP}"
fi

mkdir -p "$(dirname "$OUTPUT")"
tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

headers=()
if [[ -n "$TOKEN" ]]; then
  headers=(-H "Authorization: Bearer ${TOKEN}")
fi

curl -fsSL "${headers[@]}" "$endpoint" -o "$tmp_file"

if [[ ! -s "$tmp_file" ]]; then
  echo "OpenAPI export failed: empty response from $endpoint" >&2
  exit 1
fi

if command -v jq >/dev/null 2>&1; then
  jq -e '.openapi and .paths' "$tmp_file" >/dev/null
  jq . "$tmp_file" > "$OUTPUT"
else
  cp "$tmp_file" "$OUTPUT"
fi

echo "OpenAPI exported: $OUTPUT"
echo "Source: $endpoint"
