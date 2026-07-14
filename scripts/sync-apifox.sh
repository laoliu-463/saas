#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${APIFOX_ENV_FILE:-$ROOT_DIR/.env}"
APIFOX_CLI="${APIFOX_CLI:-apifox}"
DRY_RUN="${APIFOX_DRY_RUN:-false}"

if [[ -d "$HOME/.local/bin" ]]; then
  PATH="$HOME/.local/bin:$PATH"
fi
if [[ -d "$HOME/.hermes/node/bin" ]]; then
  PATH="$HOME/.hermes/node/bin:$PATH"
fi

PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
  elif command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  elif command -v python.exe >/dev/null 2>&1; then
    PYTHON_BIN="python.exe"
  else
    PYTHON_BIN="python"
  fi
fi

for arg in "$@"; do
  case "$arg" in
    --dry-run)
      DRY_RUN="true"
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

load_apifox_env() {
  local key line value

  [[ -f "$ENV_FILE" ]] || return 0

  for key in \
    APIFOX_ACCESS_TOKEN \
    APIFOX_PROJECT_ID \
    APIFOX_BRANCH \
    APIFOX_BRANCH_SOURCE \
    APIFOX_MODULE_ID \
    APIFOX_OPENAPI_FILE \
    APIFOX_IMPORT_OUTPUT \
    APIFOX_DEV_BASE_URL \
    APIFOX_DEV_PORT \
    APIFOX_ENVIRONMENT_ID; do
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

is_true() {
  local value
  value="$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')"
  [[ "$value" == "true" || "$value" == "1" || "$value" == "yes" || "$value" == "y" ]]
}

is_placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == __FILL_ME_* || "$value" == \<*\> || "$value" == 你的* ]]
}

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
  if [[ "$path" = /* || "$path" =~ ^[A-Za-z]:[\\/] ]]; then
    echo "$path"
  else
    echo "$ROOT_DIR/$path"
  fi
}

sanitize_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  APIFOX_SANITIZE_TOKEN="${APIFOX_ACCESS_TOKEN:-}" \
  APIFOX_SANITIZE_PROJECT="${APIFOX_PROJECT_ID:-}" \
  "$PYTHON_BIN" - "$file" <<'PY'
import os
import re
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8", errors="replace") as f:
    text = f.read()

token_value = os.environ.get("APIFOX_SANITIZE_TOKEN") or ""
project = os.environ.get("APIFOX_SANITIZE_PROJECT") or ""

if token_value and not token_value.startswith("__FILL_ME_"):
    text = text.replace(token_value, "***REDACTED_APIFOX_TOKEN***")
if project and not project.startswith("__FILL_ME_"):
    text = re.sub(
        r'(:\s*)' + re.escape(project) + r'(?=\s*[,}])',
        r'\1"***REDACTED_APIFOX_PROJECT_ID***"',
        text,
    )
    text = text.replace(project, "***REDACTED_APIFOX_PROJECT_ID***")

text = re.sub(r"(Authorization\s*:\s*Bearer\s+)[A-Za-z0-9._~+/=-]+", r"\1***REDACTED***", text, flags=re.I)
text = re.sub(r"(access[-_ ]?token[\"'=:\s]+)[A-Za-z0-9._~+/=-]+", r"\1***REDACTED***", text, flags=re.I)

with open(path, "w", encoding="utf-8", newline="") as f:
    f.write(text)
PY
}

sanitize_text() {
  APIFOX_SANITIZE_TOKEN="${APIFOX_ACCESS_TOKEN:-}" \
  APIFOX_SANITIZE_PROJECT="${APIFOX_PROJECT_ID:-}" \
  APIFOX_SANITIZE_TEXT="${1:-}" \
  "$PYTHON_BIN" - <<'PY'
import os
import re

text = os.environ.get("APIFOX_SANITIZE_TEXT") or ""
token_value = os.environ.get("APIFOX_SANITIZE_TOKEN") or ""
project = os.environ.get("APIFOX_SANITIZE_PROJECT") or ""

if token_value and not token_value.startswith("__FILL_ME_"):
    text = text.replace(token_value, "***REDACTED_APIFOX_TOKEN***")
if project and not project.startswith("__FILL_ME_"):
    text = re.sub(
        r'(:\s*)' + re.escape(project) + r'(?=\s*[,}])',
        r'\1"***REDACTED_APIFOX_PROJECT_ID***"',
        text,
    )
    text = text.replace(project, "***REDACTED_APIFOX_PROJECT_ID***")
text = re.sub(r"(Authorization\s*:\s*Bearer\s+)[A-Za-z0-9._~+/=-]+", r"\1***REDACTED***", text, flags=re.I)
print(text)
PY
}

write_sanitized_output() {
  local content="$1"
  local output="$2"
  mkdir -p "$(dirname "$output")"
  printf '%s\n' "$content" > "$output"
  sanitize_file "$output"
}

apifox_cmd() {
  "$APIFOX_CLI" "$@"
}

require_command() {
  local command_name="$1"
  local message="$2"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "$message" >&2
    exit 2
  fi
}

require_help_option() {
  local label="$1"
  local option="$2"
  shift 2
  local output
  output="$("$@" 2>&1)" || {
    echo "$label help is not available." >&2
    echo "$(sanitize_text "$output")" >&2
    exit 2
  }
  if ! grep -q -- "$option" <<<"$output"; then
    echo "$label help does not advertise required option: $option" >&2
    exit 2
  fi
}

load_apifox_env

OPENAPI_FILE="${APIFOX_OPENAPI_FILE:-docs/openapi/saas-openapi.json}"
APIFOX_BRANCH="${APIFOX_BRANCH:-ddd-sync}"
APIFOX_BRANCH_SOURCE="${APIFOX_BRANCH_SOURCE:-main}"
IMPORT_OUTPUT="${APIFOX_IMPORT_OUTPUT:-harness/reports/apifox/import-latest.log}"
ENDPOINT_LIST_OUTPUT="${APIFOX_ENDPOINT_LIST_OUTPUT:-harness/reports/apifox/endpoint-list-latest.json}"
ENVIRONMENT_OUTPUT="${APIFOX_ENVIRONMENT_OUTPUT:-harness/reports/apifox/environment-latest.json}"

OPENAPI_FILE_PATH="$(resolve_workspace_path "$OPENAPI_FILE")"
IMPORT_OUTPUT_PATH="$(resolve_workspace_path "$IMPORT_OUTPUT")"
IMPORT_REQUEST_PATH="${IMPORT_OUTPUT_PATH}.request.json"
ENDPOINT_LIST_OUTPUT_PATH="$(resolve_workspace_path "$ENDPOINT_LIST_OUTPUT")"
ENVIRONMENT_OUTPUT_PATH="$(resolve_workspace_path "$ENVIRONMENT_OUTPUT")"

case "$(printf '%s' "$APIFOX_BRANCH" | tr '[:upper:]' '[:lower:]')" in
  main|master)
    echo "Refusing Apifox import target branch '$APIFOX_BRANCH'; use a development branch such as ddd-sync or ai-sync." >&2
    exit 2
    ;;
esac

missing=()
for name in \
  APIFOX_ACCESS_TOKEN \
  APIFOX_PROJECT_ID \
  APIFOX_BRANCH \
  APIFOX_OPENAPI_FILE \
  APIFOX_DEV_BASE_URL \
  APIFOX_DEV_PORT; do
  if is_placeholder "${!name:-}"; then
    missing+=("$name")
  fi
done

echo "Apifox target endpoint: development"
echo "Apifox project: $(mask_tail "${APIFOX_PROJECT_ID:-}")"
echo "OpenAPI file: $OPENAPI_FILE"
echo "Branch source: $APIFOX_BRANCH_SOURCE"
echo "Import target branch: $APIFOX_BRANCH"
echo "Apifox dev base url: $(if is_placeholder "${APIFOX_DEV_BASE_URL:-}"; then echo "missing-or-placeholder"; else echo "configured"; fi)"
echo "Apifox dev port: $(if is_placeholder "${APIFOX_DEV_PORT:-}"; then echo "missing-or-placeholder"; else echo "configured"; fi)"
echo "Docs site/shared-doc: not published by this script"

if is_true "$DRY_RUN"; then
  echo "Mode: dry-run"
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Cloud import: expected blocked by placeholders (${missing[*]})"
    exit 0
  fi
  echo "Cloud import: dry-run blocked before apifox import"
else
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Cloud import blocked; required variables are missing or placeholders: ${missing[*]}" >&2
    exit 2
  fi
fi

if [[ ! -s "$OPENAPI_FILE_PATH" ]]; then
  echo "OpenAPI file not found or empty: $OPENAPI_FILE" >&2
  exit 2
fi

if [[ "${APIFOX_DEV_BASE_URL:-}" != *"${APIFOX_DEV_PORT:-}"* ]]; then
  echo "APIFOX_DEV_BASE_URL must contain APIFOX_DEV_PORT; refusing to import into an unknown development endpoint." >&2
  exit 2
fi

require_command "$APIFOX_CLI" "Apifox CLI is not installed or not in PATH."
require_command node "Node.js is not installed or not in PATH."
require_command curl "curl is not installed or not in PATH."
require_command "$PYTHON_BIN" "Python is not installed or not in PATH."

require_help_option "apifox import" "--branch" apifox_cmd import --help
require_help_option "apifox endpoint list" "--branch" apifox_cmd endpoint list --help
require_help_option "apifox endpoint get" "--branch" apifox_cmd endpoint get --help

APIFOX_OPENAPI_FILE_PATH="$OPENAPI_FILE_PATH" \
APIFOX_DEV_BASE_URL="${APIFOX_DEV_BASE_URL:-}" \
APIFOX_DEV_PORT="${APIFOX_DEV_PORT:-}" \
node <<'NODE'
const fs = require("fs");

const path = process.env.APIFOX_OPENAPI_FILE_PATH;
const devBase = process.env.APIFOX_DEV_BASE_URL || "";
const devPort = process.env.APIFOX_DEV_PORT || "";
const payload = JSON.parse(fs.readFileSync(path, "utf8").replace(/^\uFEFF/, ""));
const servers = Array.isArray(payload.servers) ? payload.servers : [];
const urls = servers
  .filter((server) => server && typeof server.url === "string")
  .map((server) => server.url);

if (urls.length === 0) {
  console.error("OpenAPI servers missing; cloud import is not allowed.");
  process.exit(2);
}

const matched = urls.some((url) => url.includes(devBase) || url.includes(devPort));
if (!matched) {
  console.error("OpenAPI servers do not contain the configured Apifox development endpoint or port.");
  process.exit(2);
}

console.log("OpenAPI servers contain development endpoint: PASS");
NODE

if is_true "$DRY_RUN"; then
  echo "Cloud import: dry-run PASS; no apifox import executed"
  exit 0
fi

mkdir -p "$(dirname "$IMPORT_OUTPUT_PATH")"

login_output="$(apifox_cmd login --with-token "$APIFOX_ACCESS_TOKEN" 2>&1)" || {
  echo "apifox login failed." >&2
  echo "$(sanitize_text "$login_output")" >&2
  exit 1
}
echo "Apifox login: PASS"

branch_list_output="$(apifox_cmd branch list --project "$APIFOX_PROJECT_ID" --type all 2>&1)" || {
  echo "apifox branch list failed." >&2
  echo "$(sanitize_text "$branch_list_output")" >&2
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
  branch_create_output="$(apifox_cmd branch create --project "$APIFOX_PROJECT_ID" --type sprint --name "$APIFOX_BRANCH" --from "$APIFOX_BRANCH_SOURCE" 2>&1)" || {
    echo "apifox branch create failed." >&2
    echo "$(sanitize_text "$branch_create_output")" >&2
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
echo "Import target branch id: $(mask_tail "$branch_id")"

: > "$IMPORT_OUTPUT_PATH"
if ! APIFOX_BRANCH_ID="$branch_id" APIFOX_IMPORT_REQUEST_PATH="$IMPORT_REQUEST_PATH" node - "$OPENAPI_FILE_PATH" <<'NODE'
const fs = require("fs");

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
  sanitize_file "$IMPORT_OUTPUT_PATH"
  echo "apifox OpenAPI REST import request failed. Output saved to: $IMPORT_OUTPUT" >&2
  exit 1
}
rm -f "$IMPORT_REQUEST_PATH"
sanitize_file "$IMPORT_OUTPUT_PATH"

if [[ "$http_code" -lt 200 || "$http_code" -ge 300 ]]; then
  echo "apifox OpenAPI REST import failed with HTTP $http_code. Output saved to: $IMPORT_OUTPUT" >&2
  exit 1
fi

echo "Apifox import submitted."
echo "File: $OPENAPI_FILE"
echo "Import target branch: $APIFOX_BRANCH"
echo "Import API: /v1/projects/{projectId}/import-openapi"
echo "Import output: $IMPORT_OUTPUT"
if [[ -n "${APIFOX_MODULE_ID:-}" ]]; then
  echo "Import target module id: $(mask_tail "$APIFOX_MODULE_ID")"
else
  echo "Import target module id: <default>"
fi

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
  console.error("WARN: Apifox import counters did not prove endpoint creation/update/ignore; endpoint readback is required.");
}
NODE

endpoint_list_output="$(apifox_cmd endpoint list --project "$APIFOX_PROJECT_ID" --branch "$APIFOX_BRANCH" --page 1 --page-size 10 2>&1)" || {
  echo "apifox endpoint list failed." >&2
  echo "$(sanitize_text "$endpoint_list_output")" >&2
  exit 1
}
write_sanitized_output "$endpoint_list_output" "$ENDPOINT_LIST_OUTPUT_PATH"
echo "Endpoint list output: $ENDPOINT_LIST_OUTPUT"

endpoint_ids="$(ENDPOINT_LIST_JSON="$endpoint_list_output" node <<'NODE'
const payload = JSON.parse(process.env.ENDPOINT_LIST_JSON || "{}");
const ids = [];
const seen = new Set();

function visit(value) {
  if (!value || ids.length >= 3) return;
  if (Array.isArray(value)) {
    for (const item of value) visit(item);
    return;
  }
  if (typeof value !== "object") return;
  const id = value.id || value.endpointId || value.endpoint_id || value._id;
  if (id && !seen.has(String(id))) {
    seen.add(String(id));
    ids.push(String(id));
    if (ids.length >= 3) return;
  }
  for (const child of Object.values(value)) visit(child);
}

visit(payload);
console.log(ids.join("\n"));
NODE
)"

if [[ -z "$endpoint_ids" ]]; then
  echo "apifox endpoint list returned no parseable endpoint id." >&2
  exit 1
fi

sample_index=0
while IFS= read -r endpoint_id; do
  [[ -n "$endpoint_id" ]] || continue
  sample_index=$((sample_index + 1))
  sample_output="harness/reports/apifox/endpoint-sample-${sample_index}.json"
  sample_output_path="$(resolve_workspace_path "$sample_output")"
  endpoint_detail_output="$(apifox_cmd endpoint get "$endpoint_id" --project "$APIFOX_PROJECT_ID" --branch "$APIFOX_BRANCH" 2>&1)" || {
    echo "apifox endpoint get failed for sample $sample_index." >&2
    echo "$(sanitize_text "$endpoint_detail_output")" >&2
    exit 1
  }
  write_sanitized_output "$endpoint_detail_output" "$sample_output_path"
  ENDPOINT_DETAIL_JSON="$endpoint_detail_output" node <<'NODE'
const payload = JSON.parse(process.env.ENDPOINT_DETAIL_JSON || "{}");

function hasKey(value, names) {
  if (!value) return false;
  if (Array.isArray(value)) return value.some((item) => hasKey(item, names));
  if (typeof value !== "object") return false;
  for (const [key, child] of Object.entries(value)) {
    if (names.includes(key)) return true;
    if (hasKey(child, names)) return true;
  }
  return false;
}

const hasMethod = hasKey(payload, ["method"]);
const hasPath = hasKey(payload, ["path", "url"]);
const hasInputs = hasKey(payload, ["parameters", "requestBody", "request_body"]);
const hasResponses = hasKey(payload, ["responses", "response"]);

if (!hasMethod || !hasPath || !hasInputs || !hasResponses) {
  console.error("Endpoint detail is missing method/path/parameters-or-requestBody/responses.");
  process.exit(2);
}
NODE
  echo "Endpoint get sample $sample_index: PASS ($sample_output)"
  if [[ "$sample_index" -ge 3 ]]; then
    break
  fi
done <<<"$endpoint_ids"

if [[ "$sample_index" -lt 1 ]]; then
  echo "No endpoint detail sample was verified." >&2
  exit 1
fi

if [[ -n "${APIFOX_ENVIRONMENT_ID:-}" ]] && ! is_placeholder "$APIFOX_ENVIRONMENT_ID"; then
  if apifox_cmd environment get --help >/dev/null 2>&1; then
    environment_output="$(apifox_cmd environment get "$APIFOX_ENVIRONMENT_ID" --project "$APIFOX_PROJECT_ID" 2>&1)" || {
      echo "apifox environment get failed." >&2
      echo "$(sanitize_text "$environment_output")" >&2
      exit 1
    }
    write_sanitized_output "$environment_output" "$ENVIRONMENT_OUTPUT_PATH"
    APIFOX_ENVIRONMENT_JSON="$environment_output" \
    APIFOX_DEV_BASE_URL="$APIFOX_DEV_BASE_URL" \
    APIFOX_DEV_PORT="$APIFOX_DEV_PORT" \
    node <<'NODE'
const payload = JSON.parse(process.env.APIFOX_ENVIRONMENT_JSON || "{}");
const devBase = process.env.APIFOX_DEV_BASE_URL || "";
const devPort = process.env.APIFOX_DEV_PORT || "";
const values = [];

function visit(value) {
  if (!value) return;
  if (typeof value === "string") {
    values.push(value);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach(visit);
    return;
  }
  if (typeof value === "object") {
    Object.values(value).forEach(visit);
  }
}

visit(payload);
if (!values.some((value) => value.includes(devBase) || value.includes(devPort))) {
  console.error("Apifox environment Base URL does not match the configured development endpoint.");
  process.exit(2);
}
NODE
    echo "Environment Base URL readback: PASS ($ENVIRONMENT_OUTPUT)"
  else
    echo "WARN: apifox environment get is not supported by this CLI; local OpenAPI server guard remains enforced."
  fi
else
  echo "Environment Base URL readback: SKIP (APIFOX_ENVIRONMENT_ID not configured)"
fi

echo "Cloud sync complete: endpoint list/get readback verified; docs-site/shared-doc not published."
