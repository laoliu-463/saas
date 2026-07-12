#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${APIFOX_ENV_FILE:-$ROOT_DIR/.env}"
ENV_EXAMPLE_FILE="${APIFOX_ENV_EXAMPLE_FILE:-$ROOT_DIR/.env.example}"
APIFOX_CLI="${APIFOX_CLI:-apifox}"
SYNC_SCRIPT="$ROOT_DIR/scripts/sync-apifox.sh"

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

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

warn() {
  echo "WARN: $*" >&2
}

pass() {
  echo "PASS: $*"
}

is_placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == __FILL_ME_* || "$value" == \<*\> || "$value" == 你的* ]]
}

load_env_file() {
  local file="$1"
  local key line value

  [[ -f "$file" ]] || return 0

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

    line="$(grep -E "^${key}=" "$file" | tail -n 1 || true)"
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

apifox_cmd() {
  "$APIFOX_CLI" "$@"
}

require_command() {
  local command_name="$1"
  local message="$2"
  command -v "$command_name" >/dev/null 2>&1 || fail "$message"
}

require_declared() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is not declared in process env, .env, or .env.example"
}

check_help() {
  local label="$1"
  shift
  local output
  output="$("$@" 2>&1)" || fail "$label help is not available"
  echo "$output"
}

scan_staged_secrets() {
  local staged_diff="$1"
  STAGED_DIFF="$staged_diff" "$PYTHON_BIN" - <<'PY'
import os
import re
import sys

text = os.environ.get("STAGED_DIFF") or ""

allowed_markers = ("__FILL_ME_", "<redacted>", "REDACTED", "****")
patterns = [
    ("APIFOX_ACCESS_TOKEN", re.compile(r"^\+.*APIFOX_ACCESS_TOKEN\s*=\s*([^\s#]+)", re.M)),
    ("APIFOX_PROJECT_ID", re.compile(r"^\+.*APIFOX_PROJECT_ID\s*=\s*([^\s#]+)", re.M)),
]

for label, pattern in patterns:
    for match in pattern.finditer(text):
        value = match.group(1).strip().strip('"').strip("'")
        if value and not value.startswith(allowed_markers):
            print(f"FAIL: staged diff contains non-placeholder {label}", file=sys.stderr)
            sys.exit(1)

suspicious = [
    re.compile(r"^\+.*Authorization\s*:\s*Bearer\s+[A-Za-z0-9._~+/=-]{16,}", re.I | re.M),
    re.compile(r"^\+.*Bearer\s+[A-Za-z0-9._~+/=-]{24,}", re.I | re.M),
    re.compile(r"^\+.*[A-Za-z0-9_-]{24,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}", re.M),
]

for pattern in suspicious:
    if pattern.search(text):
        print("FAIL: staged diff contains a suspicious token shape", file=sys.stderr)
        sys.exit(1)
PY
}

echo "Apifox / OpenAPI local verification"
echo "Repo: $ROOT_DIR"
echo "Mode: local verification"

cd "$ROOT_DIR"

load_env_file "$ENV_FILE"
load_env_file "$ENV_EXAMPLE_FILE"

require_declared APIFOX_ACCESS_TOKEN
require_declared APIFOX_PROJECT_ID
require_declared APIFOX_BRANCH
require_declared APIFOX_OPENAPI_FILE
require_declared APIFOX_DEV_BASE_URL
require_declared APIFOX_DEV_PORT

if git check-ignore -q .env; then
  pass ".env is ignored by Git"
else
  fail ".env must be ignored by Git"
fi

if git diff --cached --name-only -- .env | grep -qx ".env"; then
  fail ".env must not be staged"
else
  pass ".env is not staged"
fi

staged_diff="$(git diff --cached -- . ':!docs/openapi/saas-openapi.json' || true)"
scan_staged_secrets "$staged_diff"
pass "staged secret scan"

OPENAPI_FILE="${APIFOX_OPENAPI_FILE:-docs/openapi/saas-openapi.json}"
OPENAPI_FILE_PATH="$OPENAPI_FILE"
if [[ "$OPENAPI_FILE_PATH" != /* && ! "$OPENAPI_FILE_PATH" =~ ^[A-Za-z]:[\\/] ]]; then
  OPENAPI_FILE_PATH="$ROOT_DIR/$OPENAPI_FILE_PATH"
fi

[[ -s "$OPENAPI_FILE_PATH" ]] || fail "OpenAPI file not found or empty: $OPENAPI_FILE"

openapi_stats="$(
  APIFOX_OPENAPI_FILE_PATH="$OPENAPI_FILE_PATH" \
  APIFOX_DEV_BASE_URL="${APIFOX_DEV_BASE_URL:-}" \
  APIFOX_DEV_PORT="${APIFOX_DEV_PORT:-}" \
  "$PYTHON_BIN" - <<'PY'
import json
import os
import sys

path = os.environ["APIFOX_OPENAPI_FILE_PATH"]
dev_base = os.environ.get("APIFOX_DEV_BASE_URL") or ""
dev_port = os.environ.get("APIFOX_DEV_PORT") or ""

with open(path, encoding="utf-8-sig") as f:
    data = json.load(f)

if not data.get("openapi"):
    print("FAIL: OpenAPI `openapi` field missing", file=sys.stderr)
    sys.exit(1)

paths = data.get("paths") or {}
schemas = (data.get("components") or {}).get("schemas") or {}
servers = data.get("servers") or []
security = (data.get("components") or {}).get("securitySchemes") or {}

ops = []
for p, item in paths.items():
    if not isinstance(item, dict):
        continue
    for method, op in item.items():
        if method.lower() in {"get", "post", "put", "delete", "patch", "options", "head"}:
            ops.append((method.upper(), p, op if isinstance(op, dict) else {}))

print(f"paths={len(paths)}")
print(f"operations={len(ops)}")
print(f"schemas={len(schemas)}")
print(f"servers={len(servers)}")
print("securitySchemes=" + ",".join(sorted(security.keys())))
print("bearerAuth=" + ("true" if "bearerAuth" in security else "false"))

if not paths:
    print("FAIL: OpenAPI paths is empty", file=sys.stderr)
    sys.exit(1)
if not ops:
    print("FAIL: OpenAPI operations is empty", file=sys.stderr)
    sys.exit(1)
if "bearerAuth" not in security:
    print("FAIL: bearerAuth security scheme missing", file=sys.stderr)
    sys.exit(1)
if not servers:
    print("FAIL: OpenAPI servers missing", file=sys.stderr)
    sys.exit(1)

bad = []
for method, p, op in ops[:20]:
    if not (op.get("responses") or {}):
        bad.append(f"{method} {p} missing responses")
if bad:
    print("FAIL: sampled operations missing responses", file=sys.stderr)
    for item in bad:
        print(item, file=sys.stderr)
    sys.exit(1)

server_urls = [s.get("url", "") for s in servers if isinstance(s, dict)]
dev_configured = bool(dev_base and not dev_base.startswith("__FILL_ME_") and dev_port and not dev_port.startswith("__FILL_ME_"))

if dev_configured:
    if dev_port not in dev_base:
        print("FAIL: APIFOX_DEV_BASE_URL must contain APIFOX_DEV_PORT", file=sys.stderr)
        sys.exit(1)
    if not any(dev_base in url or dev_port in url for url in server_urls):
        print("FAIL: configured Apifox development endpoint not found in OpenAPI servers", file=sys.stderr)
        sys.exit(1)
    print("devEndpointMatched=true")
else:
    print("devEndpointMatched=placeholder")

print("OpenAPI content guard PASS")
PY
)"

echo "$openapi_stats"

require_command "$APIFOX_CLI" "Apifox CLI is not installed or not in PATH."
apifox_version="$(apifox_cmd -v 2>&1)" || fail "apifox -v failed"
echo "apifox version=$apifox_version"

import_help="$(check_help "apifox import" apifox_cmd import --help)"
for option in --project --format --file --branch; do
  grep -q -- "$option" <<<"$import_help" || fail "apifox import --help missing $option"
done
pass "apifox import help supports target branch import"

endpoint_list_help="$(check_help "apifox endpoint list" apifox_cmd endpoint list --help)"
endpoint_get_help="$(check_help "apifox endpoint get" apifox_cmd endpoint get --help)"
grep -q -- "--branch" <<<"$endpoint_list_help" || fail "apifox endpoint list --help missing --branch"
grep -q -- "--branch" <<<"$endpoint_get_help" || fail "apifox endpoint get --help missing --branch"
pass "endpoint list/get help supports branch readback"

if apifox_cmd environment list --help >/dev/null 2>&1; then
  pass "environment list help"
else
  warn "apifox environment list --help unavailable"
fi

if apifox_cmd environment get --help >/dev/null 2>&1; then
  pass "environment get help"
else
  warn "apifox environment get --help unavailable"
fi

bash -n "$SYNC_SCRIPT"
pass "sync-apifox.sh syntax"

dry_run_output="$(
  APIFOX_DRY_RUN=true \
  APIFOX_CLI="$APIFOX_CLI" \
  bash "$SYNC_SCRIPT" --dry-run 2>&1
)" || {
  echo "$dry_run_output" >&2
  fail "sync-apifox.sh dry-run failed"
}
echo "$dry_run_output"
if grep -Eq "Cloud import: (expected blocked by placeholders|dry-run blocked before apifox import|dry-run PASS)" <<<"$dry_run_output"; then
  pass "placeholder/dry-run cloud import protection"
else
  fail "dry-run output did not prove cloud import was blocked"
fi

echo "Apifox / OpenAPI local verification PASS"
