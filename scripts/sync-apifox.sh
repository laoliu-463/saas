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

  for key in APIFOX_ACCESS_TOKEN APIFOX_PROJECT_ID APIFOX_BRANCH APIFOX_BRANCH_SOURCE APIFOX_MODULE_ID APIFOX_OPENAPI_FILE APIFOX_IMPORT_OUTPUT; do
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

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js is not installed or not in PATH." >&2
  exit 2
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is not installed or not in PATH." >&2
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
IMPORT_REQUEST_PATH="${IMPORT_OUTPUT_PATH}.request.json"

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

branch_id="$(BRANCH_LIST_JSON="$branch_list_output" APIFOX_BRANCH_NAME="$APIFOX_BRANCH" node -e '
const payload = JSON.parse(process.env.BRANCH_LIST_JSON || "{}");
const branch = process.env.APIFOX_BRANCH_NAME;
const item = Array.isArray(payload.data) ? payload.data.find((entry) => entry && entry.name === branch) : null;
if (item && item.id) {
  console.log(item.id);
}
')"

if [[ -z "$branch_id" ]]; then
  branch_create_output="$(apifox branch create --project "$APIFOX_PROJECT_ID" --type sprint --name "$APIFOX_BRANCH" --from "$APIFOX_BRANCH_SOURCE" 2>&1)" || {
    echo "apifox branch create failed." >&2
    echo "$branch_create_output" >&2
    exit 1
  }
  branch_id="$(BRANCH_CREATE_JSON="$branch_create_output" node -e '
const payload = JSON.parse(process.env.BRANCH_CREATE_JSON || "{}");
if (payload.data && payload.data.id) {
  console.log(payload.data.id);
}
')"
  if [[ -z "$branch_id" ]]; then
    echo "apifox branch create did not return a branch id." >&2
    exit 1
  fi
  echo "Apifox branch created: $APIFOX_BRANCH"
else
  echo "Apifox branch exists: $APIFOX_BRANCH"
fi
echo "Import target branch id: $branch_id"

: > "$IMPORT_OUTPUT_PATH"
if ! APIFOX_BRANCH_ID="$branch_id" APIFOX_IMPORT_REQUEST_PATH="$IMPORT_REQUEST_PATH" node - "$OPENAPI_FILE" <<'NODE'
const fs = await import("node:fs");

const openApiPath = process.argv[2];
const openApiText = fs.readFileSync(openApiPath, "utf8");
const branchId = Number(process.env.APIFOX_BRANCH_ID);
const moduleId = process.env.APIFOX_MODULE_ID ? Number(process.env.APIFOX_MODULE_ID) : undefined;
const requestPath = process.env.APIFOX_IMPORT_REQUEST_PATH;

if (!Number.isFinite(branchId)) {
  throw new Error("APIFOX_BRANCH_ID must be numeric.");
}

if (process.env.APIFOX_MODULE_ID && !Number.isFinite(moduleId)) {
  throw new Error("APIFOX_MODULE_ID must be numeric when set.");
}

const options = {
  targetBranchId: branchId,
  endpointOverwriteBehavior: "OVERWRITE_EXISTING",
  schemaOverwriteBehavior: "OVERWRITE_EXISTING",
  updateFolderOfChangedEndpoint: true,
  prependBasePath: false,
  deleteUnmatchedResources: false,
};

if (Number.isFinite(moduleId)) {
  options.moduleId = moduleId;
}

fs.writeFileSync(requestPath, JSON.stringify({ input: openApiText, options }));
NODE
then
  echo "failed to build Apifox OpenAPI import request." >&2
  exit 1
fi

http_code="$(curl --silent --show-error \
  --connect-timeout 30 \
  --max-time 180 \
  --output "$IMPORT_OUTPUT_PATH" \
  --write-out "%{http_code}" \
  --request POST "https://api.apifox.com/v1/projects/${APIFOX_PROJECT_ID}/import-openapi?locale=zh-CN" \
  --header "X-Apifox-Api-Version: 2024-03-28" \
  --header "Authorization: Bearer ${APIFOX_ACCESS_TOKEN}" \
  --header "Content-Type: application/json" \
  --data-binary "@${IMPORT_REQUEST_PATH}")" || {
  rm -f "$IMPORT_REQUEST_PATH"
  echo "apifox OpenAPI REST import request failed. Output saved to: $IMPORT_OUTPUT" >&2
  exit 1
}
rm -f "$IMPORT_REQUEST_PATH"

if [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
  echo "apifox OpenAPI REST import failed with HTTP $http_code. Output saved to: $IMPORT_OUTPUT" >&2
  sed -E 's/(access[-_ ]?token|token)["=: ]+[^", ]+/\1=***REDACTED***/ig' "$IMPORT_OUTPUT_PATH" >&2
  exit 1
fi

echo "Apifox import submitted."
echo "File: $OPENAPI_FILE"
echo "Import target branch: $APIFOX_BRANCH"
echo "Import API: /v1/projects/{projectId}/import-openapi"
if [[ -n "${APIFOX_MODULE_ID:-}" ]]; then
  echo "Import target module id: $APIFOX_MODULE_ID"
else
  echo "Import target module id: <default>"
fi
echo "Import output: $IMPORT_OUTPUT"

node - "$IMPORT_OUTPUT_PATH" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const payload = JSON.parse(fs.readFileSync(path, "utf8"));

const countersPayload = (payload.data && payload.data.counters) || {};
const counters = {
  endpointCreated: Number(countersPayload.endpointCreated || 0),
  endpointUpdated: Number(countersPayload.endpointUpdated || 0),
  endpointIgnored: Number(countersPayload.endpointIgnored || 0),
  endpointFailed: Number(countersPayload.endpointFailed || 0),
  schemaCreated: Number(countersPayload.schemaCreated || 0),
  schemaUpdated: Number(countersPayload.schemaUpdated || 0),
  schemaIgnored: Number(countersPayload.schemaIgnored || 0),
  schemaFailed: Number(countersPayload.schemaFailed || 0),
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
