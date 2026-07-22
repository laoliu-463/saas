#!/usr/bin/env bash
# scripts/cd/evidence-collect.sh
#
# Canonical entry point for collecting and archiving evidence from a CD run.
#
# This is the ONLY script that should write evidence files into the three
# canonical locations. Existing ad-hoc writers (deploy-real-pre.sh,
# verify-github-ci-gate.sh, etc.) MUST be migrated to call this script so
# we have a single taxonomy:
#
#   1. runtime/evidence/<runId>/   — ephemeral run logs (gitignored; not
#                                    meant to be committed; uploaded as
#                                    CI/Jenkins artifacts and retained
#                                    14-30 days).
#
#   2. harness/state/current.json  — last-known machine state for the
#                                    harness inspection. Replaces the
#                                    "latest-*.md" treadmill; the canonical
#                                    state is JSON and Markdown is generated
#                                    FROM JSON for human consumption only.
#
#   3. releases/<sha>/release-manifest.json — long-lived per-release
#                                    manifest (source main SHA, image
#                                    digests, migration versions, CI run
#                                    info, Jenkins build, deploy result,
#                                    rollback target, previous release).
#
# Usage:
#   scripts/cd/evidence-collect.sh stage <run-id> <source> <file> [<file>...]
#     Stage files into runtime/evidence/<run-id>/ (does NOT copy).
#   scripts/cd/evidence-collect.sh archive <run-id> [<file>...]
#     Archive staged files to runtime/evidence/<run-id>/<source>/ and
#     emit a manifest entry.
#   scripts/cd/evidence-collect.sh state-write <run-id> <state.json>
#     Write the machine-state JSON for harness inspection (symlink
#     harness/state/current.json to it).
#   scripts/cd/evidence-collect.sh release-manifest <sha> <manifest.json>
#     Copy a release manifest into releases/<sha>/ and validate it.
#   scripts/cd/evidence-collect.sh help
#     Print this usage.
#
# Environment:
#   REPO_ROOT              repo root (default: parent of this script)
#   REAL_PRE_DEPLOY_HOST   host label for the manifest (default: unknown)
#   REAL_PRE_LOCK_FILE     path to the flock file (default:
#                          /var/lock/saas-real-pre-deploy.lock)
#
# Exit codes:
#   0   success
#   1   invalid arguments
#   2   filesystem error
#   3   validation failed

set -eu
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
RUNTIME_BASE="${RUNTIME_BASE:-${REPO_ROOT}/runtime}"
RELEASES_BASE="${RELEASES_BASE:-${REPO_ROOT}/releases}"
HARNESS_STATE_DIR="${HARNESS_STATE_DIR:-${REPO_ROOT}/harness/state}"

log()  { printf '[evidence] %s\n' "$*"; }
warn() { printf '[evidence][warn] %s\n' "$*" >&2; }
die()  { printf '[evidence][fatal] %s\n' "$*" >&2; exit 1; }

SUBCOMMAND="${1:-}"
shift || true

case "${SUBCOMMAND}" in
  stage|archive|state-write|release-manifest|help|-h|--help|"")
    ;;
  *)
    die "Unknown subcommand: ${SUBCOMMAND}. Run with 'help'."
    ;;
esac

case "${SUBCOMMAND}" in
  help|-h|--help|"")
    sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
esac

case "${SUBCOMMAND}" in
  stage)
    # stage <run-id> <source> <file> [<file>...]
    RUN_ID="${1:?run-id required}"; shift
    SOURCE="${1:?source label required (e.g. github-ci, jenkins)}"; shift
    [ "$#" -gt 0 ] || die "at least one file required"

    TARGET_DIR="${RUNTIME_BASE}/evidence/${RUN_ID}/${SOURCE}"
    mkdir -p "${TARGET_DIR}"
    for f in "$@"; do
      [ -e "${f}" ] || die "source file missing: ${f}"
      cp -p "${f}" "${TARGET_DIR}/$(basename "${f}")"
      log "staged ${f} -> ${TARGET_DIR}/$(basename "${f}")"
    done
    ;;

  archive)
    # archive <run-id> [<file>...]
    RUN_ID="${1:?run-id required}"; shift
    [ "$#" -gt 0 ] || die "at least one file required"

    TARGET_DIR="${RUNTIME_BASE}/evidence/${RUN_ID}/archive"
    mkdir -p "${TARGET_DIR}"
    for f in "$@"; do
      [ -e "${f}" ] || die "source file missing: ${f}"
      cp -p "${f}" "${TARGET_DIR}/$(basename "${f}")"
      log "archived ${f} -> ${TARGET_DIR}/$(basename "${f}")"
    done
    log "run ${RUN_ID} archived. Uploader (Jenkins/CI) should upload ${RUNTIME_BASE}/evidence/${RUN_ID} as a build artifact."
    ;;

  state-write)
    # state-write <run-id> <state.json>
    RUN_ID="${1:?run-id required}"; shift
    STATE_FILE="${1:?state.json path required}"; shift

    [ -f "${STATE_FILE}" ] || die "state.json missing: ${STATE_FILE}"

    # Validate the JSON minimally (object with required top-level fields).
    python -c "
import json, sys
with open('${STATE_FILE}', encoding='utf-8') as f:
    data = json.load(f)
for key in ('runId', 'lastVerifiedSha', 'environment', 'checks', 'timestamp'):
    if key not in data:
        sys.exit(f'missing required field: {key}')
" || die "state.json validation failed"

    mkdir -p "${HARNESS_STATE_DIR}"
    cp -p "${STATE_FILE}" "${HARNESS_STATE_DIR}/${RUN_ID}.json"

    # Atomically update current.json symlink. On Linux this is a single
    # rename; on systems where rename over a symlink fails, fall back to
    # replace.
    TMP_LINK="${HARNESS_STATE_DIR}/.current.json.tmp"
    ln -sfn "${HARNESS_STATE_DIR}/${RUN_ID}.json" "${TMP_LINK}"
    mv -f "${TMP_LINK}" "${HARNESS_STATE_DIR}/current.json"

    log "wrote machine state: ${HARNESS_STATE_DIR}/${RUN_ID}.json (current.json -> it)"
    ;;

  release-manifest)
    # release-manifest <sha> <manifest.json>
    SHA="${1:?sha required}"; shift
    MANIFEST="${1:?manifest.json required}"; shift

    if ! printf '%s' "${SHA}" | grep -Eq '^[0-9a-f]{40}$'; then
      die "sha must be the full 40-character commit SHA, got: ${SHA}"
    fi
    [ -f "${MANIFEST}" ] || die "manifest.json missing: ${MANIFEST}"

    python -c "
import json, sys
with open('${MANIFEST}', encoding='utf-8') as f:
    m = json.load(f)
for key in ('gitSha', 'branch', 'backendDigest', 'frontendDigest', 'migrationVersions', 'ciRun', 'jenkinsBuild', 'deployResult', 'previous', 'rollbackTarget'):
    if key not in m:
        sys.exit(f'missing required field: {key}')
if m['gitSha'] != '${SHA}':
    sys.exit(f'manifest gitSha {m[\"gitSha\"]} does not match argument sha ${SHA}')
" || die "release manifest validation failed"

    TARGET_DIR="${RELEASES_BASE}/${SHA}"
    mkdir -p "${TARGET_DIR}"
    cp -p "${MANIFEST}" "${TARGET_DIR}/release-manifest.json"

    # Also update releases/current.json pointer atomically.
    python - "${RELEASES_BASE}" "${SHA}" <<'PY'
import json, os, sys, tempfile
base = sys.argv[1]
sha = sys.argv[2]
manifest = os.path.join(base, sha, 'release-manifest.json')
with open(manifest, encoding='utf-8') as f:
    data = json.load(f)

current_path = os.path.join(base, 'current.json')
previous_path = os.path.join(base, 'previous.json')

# Atomically replace current.json; previous.json becomes the old current.
if os.path.exists(current_path):
    with open(current_path, encoding='utf-8') as f:
        old = json.load(f)
    if old.get('gitSha') != sha:
        # Write old current -> previous.json
        tmp = previous_path + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(old, f, indent=2, sort_keys=True)
            f.write('\n')
        os.replace(tmp, previous_path)

tmp = current_path + '.tmp'
with open(tmp, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, sort_keys=True)
    f.write('\n')
os.replace(tmp, current_path)
print(f"updated {current_path} (gitSha={sha})")
PY
    log "release manifest accepted: ${TARGET_DIR}/release-manifest.json"
    ;;

esac