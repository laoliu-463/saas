#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${APIFOX_ENV_FILE:-$ROOT_DIR/.env}"

if [[ -d "$HOME/.local/bin" ]]; then
  PATH="$HOME/.local/bin:$PATH"
fi
if [[ -d "$HOME/.hermes/node/bin" ]]; then
  PATH="$HOME/.hermes/node/bin:$PATH"
fi

load_apifox_env() {
  local key line value

  [[ -f "$ENV_FILE" ]] || return 0

  for key in APIFOX_ACCESS_TOKEN APIFOX_PROJECT_ID APIFOX_BRANCH APIFOX_BRANCH_SOURCE APIFOX_OPENAPI_FILE APIFOX_IMPORT_OUTPUT; do
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
APIFOX_BRANCH_SOURCE="${APIFOX_BRANCH_SOURCE:-main}"
IMPORT_OUTPUT="${APIFOX_IMPORT_OUTPUT:-runtime/apifox-import-latest.json}"

mask_tail() {
  local value="${1:-}"
  if [[ ${#value} -le 4 ]]; then
    echo "****"
  else
    echo "****${value: -4}"
  fi
}

resolve_workspace_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    echo "$path"
  else
    echo "$ROOT_DIR/$path"
  fi
}

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

if is_placeholder "$APIFOX_BRANCH"; then
  echo "APIFOX_BRANCH is required; refusing to import into the default branch implicitly." >&2
  exit 2
fi

if [[ ! -s "$OPENAPI_FILE" ]]; then
  echo "OpenAPI file not found or empty: $OPENAPI_FILE" >&2
  exit 2
fi

IMPORT_OUTPUT_PATH="$(resolve_workspace_path "$IMPORT_OUTPUT")"
mkdir -p "$(dirname "$IMPORT_OUTPUT_PATH")"

echo "Apifox project: $(mask_tail "$APIFOX_PROJECT_ID")"
echo "OpenAPI file: $OPENAPI_FILE"
echo "Branch source: $APIFOX_BRANCH_SOURCE"
echo "Import target branch: $APIFOX_BRANCH"
echo "Cloud import: enabled"

login_output="$(apifox login --with-token "$APIFOX_ACCESS_TOKEN" 2>&1)" || {
  echo "apifox login failed." >&2
  echo "$login_output" >&2
  exit 1
}
echo "Apifox login: PASS"

branch_list_output="$(apifox branch list --project "$APIFOX_PROJECT_ID" --type all 2>&1)" || {
  echo "apifox branch list failed." >&2
  echo "$branch_list_output" >&2
  exit 1
}

if ! BRANCH_LIST_JSON="$branch_list_output" APIFOX_BRANCH_NAME="$APIFOX_BRANCH" node -e '
const payload = JSON.parse(process.env.BRANCH_LIST_JSON || "{}");
const branch = process.env.APIFOX_BRANCH_NAME;
const exists = Array.isArray(payload.data) && payload.data.some((item) => item && item.name === branch);
process.exit(exists ? 0 : 1);
'; then
  branch_create_output="$(apifox branch create --project "$APIFOX_PROJECT_ID" --type sprint --name "$APIFOX_BRANCH" --from "$APIFOX_BRANCH_SOURCE" 2>&1)" || {
    echo "apifox branch create failed." >&2
    echo "$branch_create_output" >&2
    exit 1
  }
  echo "Apifox branch created: $APIFOX_BRANCH"
else
  echo "Apifox branch exists: $APIFOX_BRANCH"
fi

args=(import --project "$APIFOX_PROJECT_ID" --format openapi --file "$OPENAPI_FILE" --branch "$APIFOX_BRANCH")

if ! apifox "${args[@]}" >"$IMPORT_OUTPUT_PATH" 2>&1; then
  echo "apifox import failed. Output saved to: $IMPORT_OUTPUT" >&2
  sed -E 's/(access[-_ ]?token|token)["=: ]+[^", ]+/\1=***REDACTED***/ig' "$IMPORT_OUTPUT_PATH" >&2
  exit 1
fi

echo "Apifox import submitted."
echo "File: $OPENAPI_FILE"
echo "Import target branch: $APIFOX_BRANCH"
echo "Import output: $IMPORT_OUTPUT"

node - "$IMPORT_OUTPUT_PATH" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const payload = JSON.parse(fs.readFileSync(path, "utf8"));

if (!payload.success) {
  console.error("apifox import returned success=false.");
  process.exit(1);
}

const data = payload.data || {};
const endpoint = (data.apiCollection && data.apiCollection.item) || {};
const schema = (data.schemaCollection && data.schemaCollection.item) || {};
const counters = {
  endpointCreated: Number(endpoint.createCount || 0),
  endpointUpdated: Number(endpoint.updateCount || 0),
  endpointIgnored: Number(endpoint.ignoreCount || 0),
  endpointFailed: Number(endpoint.errorCount || 0),
  schemaCreated: Number(schema.createCount || 0),
  schemaUpdated: Number(schema.updateCount || 0),
  schemaIgnored: Number(schema.ignoreCount || 0),
  schemaFailed: Number(schema.errorCount || 0),
};

for (const [key, value] of Object.entries(counters)) {
  console.log(`${key}=${value}`);
}

if (counters.endpointFailed > 0 || counters.schemaFailed > 0) {
  console.error("Apifox import has failed endpoint/schema counters.");
  process.exit(2);
}

if (counters.endpointCreated + counters.endpointUpdated + counters.endpointIgnored === 0) {
  console.error("Apifox import did not create, update, or ignore any endpoint.");
  process.exit(3);
}
NODE
