#!/usr/bin/env bash
# ============================================================================
# scripts/verify-github-ci-gate.sh
# PR #6: GHA SHA Gate
#
# Polls the GitHub Actions REST API and verifies that the EXACT FULL_COMMIT
# being deployed has a successful ci.yml push run on the release/real-pre
# branch. All three required jobs (Backend tests, Frontend tests and build,
# Repository governance) must be completed with conclusion=success.
#
# Failure modes (fail-closed; the deploy must NOT proceed):
#   - API 401 / 403 / 404:                       GITHUB_CI_GATE_API_ERROR
#   - No run found for head_sha within 50 min:   GITHUB_CI_GATE_TIMEOUT
#   - Run conclusion != success:                 GITHUB_CI_GATE_RUN_FAILED
#   - Workflow conclusion success but a job missing/renamed/failed:
#                                               GITHUB_CI_GATE_JOB_FAILED
#
# Required env (exported by the calling Jenkinsfile withCredentials step):
#   GITHUB_REPOSITORY  e.g. laoliu-463/saas
#   GITHUB_TOKEN       Fine-grained PAT with Actions:read on that repo
#   GITHUB_WORKFLOW    e.g. ci.yml
#   GITHUB_BRANCH      e.g. release/real-pre
#   GITHUB_SHA         the 40-character commit SHA being deployed
#
# Evidence files written to $EVIDENCE_DIR (default runtime/qa/out/jenkins):
#   github-ci-gate.txt          PASS / FAIL + summary
#   github-ci-run-id.txt        the run id that satisfied the gate
#   github-ci-run-url.txt       html url of that run
#   github-ci-run.json          full run object (for postmortem)
#   github-ci-jobs.tsv          one row per job (name, conclusion)
# ============================================================================
set -eu
set -o pipefail

EVIDENCE_DIR="${EVIDENCE_DIR:-runtime/qa/out/jenkins}"
mkdir -p "$EVIDENCE_DIR"

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required (e.g. laoliu-463/saas)}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN required (Jenkins credentials binding)}"
: "${GITHUB_WORKFLOW:?GITHUB_WORKFLOW required (e.g. ci.yml)}"
: "${GITHUB_BRANCH:?GITHUB_BRANCH required (e.g. release/real-pre)}"
: "${GITHUB_SHA:?GITHUB_SHA required (40-char commit SHA)}"

if ! printf '%s' "$GITHUB_SHA" | grep -Eq '^[0-9a-f]{40}$'; then
    echo "ERROR: GITHUB_SHA must be the full 40-character commit SHA, got '$GITHUB_SHA'."
    exit 1
fi

REQUIRED_JOBS=("Backend tests" "Frontend tests and build" "Repository governance")
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-15}"
WAIT_LIMIT_SECONDS="${WAIT_LIMIT_SECONDS:-3000}"  # 50 minutes

ELAPSED=0
RUN_ID=""
RUN_HTML_URL=""
RUN_JSON=""
RUN_OBJECT=""

echo "GHA SHA Gate: polling workflow='$GITHUB_WORKFLOW' branch='$GITHUB_BRANCH' head_sha='$GITHUB_SHA'"
echo "GHA SHA Gate: required jobs: ${REQUIRED_JOBS[*]}"

while [ "$ELAPSED" -lt "$WAIT_LIMIT_SECONDS" ]; do
    # Per-page=20 to get latest runs first; we filter by head_sha + event=push client-side.
    RESPONSE=$(curl -sS \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -w "\n__HTTP_STATUS__:%{http_code}" \
        "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/workflows/${GITHUB_WORKFLOW}/runs?branch=${GITHUB_BRANCH}&event=push&per_page=20") || RESPONSE=""

    HTTP_STATUS=$(printf '%s' "$RESPONSE" | sed -n 's/^__HTTP_STATUS__://p' | tail -n1)
    BODY=$(printf '%s' "$RESPONSE" | sed '/^__HTTP_STATUS__:/d')

    case "$HTTP_STATUS" in
        200) ;;
        401|403|404)
            echo "GITHUB_CI_GATE_API_ERROR: GitHub API returned HTTP $HTTP_STATUS for workflow runs."
            echo "$BODY" | head -c 500
            exit 2
            ;;
        "")
            echo "GITHUB_CI_GATE_API_ERROR: empty HTTP response (network or DNS)."
            exit 2
            ;;
        *)
            echo "GITHUB_CI_GATE_API_ERROR: GitHub API returned HTTP $HTTP_STATUS."
            echo "$BODY" | head -c 500
            exit 2
            ;;
    esac

    # Parse the runs JSON with python (jq is not always installed on the Jenkins host).
    # Look for the first run whose head_sha matches and event is push.
    PARSED=$(python3 - "$GITHUB_SHA" <<'PY' <<"$BODY"
import json, sys
data = json.loads(sys.stdin.read())
target = sys.argv[1]
runs = data.get("workflow_runs", [])
for run in runs:
    if run.get("event") != "push":
        continue
    if run.get("head_sha", "").lower() != target.lower():
        continue
    print(run.get("id", ""))
    print(run.get("status", ""))
    print(run.get("conclusion") or "")
    print(run.get("html_url", ""))
    print(json.dumps(run))
    sys.exit(0)
sys.exit(1)
PY
) || PARSED=""

    if [ -n "$PARSED" ]; then
        RUN_ID=$(printf '%s\n' "$PARSED" | sed -n '1p')
        RUN_STATUS=$(printf '%s\n' "$PARSED" | sed -n '2p')
        RUN_CONCLUSION=$(printf '%s\n' "$PARSED" | sed -n '3p')
        RUN_HTML_URL=$(printf '%s\n' "$PARSED" | sed -n '4p')
        RUN_OBJECT=$(printf '%s\n' "$PARSED" | sed -n '5p')

        if [ "$RUN_STATUS" = "completed" ]; then
            if [ "$RUN_CONCLUSION" = "success" ]; then
                break
            else
                echo "GITHUB_CI_GATE_RUN_FAILED: workflow run $RUN_ID conclusion='$RUN_CONCLUSION' for head_sha=$GITHUB_SHA"
                echo "$RUN_HTML_URL"
                echo "$RUN_OBJECT" > "$EVIDENCE_DIR/github-ci-run.json"
                echo "FAIL: run conclusion=$RUN_CONCLUSION" > "$EVIDENCE_DIR/github-ci-gate.txt"
                exit 3
            fi
        fi
        # Status is queued / in_progress / requested / waiting / pending — keep polling.
        echo "GHA SHA Gate: run $RUN_ID status=$RUN_STATUS (waiting for completion, elapsed=${ELAPSED}s)"
    else
        echo "GHA SHA Gate: no run yet for head_sha=$GITHUB_SHA on branch=$GITHUB_BRANCH (elapsed=${ELAPSED}s)"
    fi

    sleep "$POLL_INTERVAL_SECONDS"
    ELAPSED=$((ELAPSED + POLL_INTERVAL_SECONDS))
done

if [ -z "$RUN_ID" ] || [ "$RUN_CONCLUSION" != "success" ]; then
    echo "GITHUB_CI_GATE_TIMEOUT: no successful ci.yml push run for head_sha=$GITHUB_SHA within ${WAIT_LIMIT_SECONDS}s."
    exit 4
fi

# Persist run metadata as evidence.
echo "$RUN_ID" > "$EVIDENCE_DIR/github-ci-run-id.txt"
echo "$RUN_HTML_URL" > "$EVIDENCE_DIR/github-ci-run-url.txt"
echo "$RUN_OBJECT" > "$EVIDENCE_DIR/github-ci-run.json"

# Now verify the three required jobs.
JOBS_RESPONSE=$(curl -sS \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    -w "\n__HTTP_STATUS__:%{http_code}" \
    "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/runs/${RUN_ID}/jobs?per_page=100") || JOBS_RESPONSE=""

HTTP_STATUS=$(printf '%s' "$JOBS_RESPONSE" | sed -n 's/^__HTTP_STATUS__://p' | tail -n1)
JOBS_BODY=$(printf '%s' "$JOBS_RESPONSE" | sed '/^__HTTP_STATUS__:/d')
if [ "$HTTP_STATUS" != "200" ]; then
    echo "GITHUB_CI_GATE_API_ERROR: GitHub API returned HTTP $HTTP_STATUS for run jobs."
    exit 2
fi

# Write a TSV of job name / conclusion for postmortem.
printf '%s\n' "$JOBS_BODY" | python3 -c "
import json, sys
data = json.loads(sys.stdin.read())
for j in data.get('jobs', []):
    print(f\"{j.get('name','')}\t{j.get('status','')}\t{j.get('conclusion') or ''}\")
" > "$EVIDENCE_DIR/github-ci-jobs.tsv"

# Validate each required job.
FAIL_REASON=""
for REQUIRED in "${REQUIRED_JOBS[@]}"; do
    LINE=$(python3 - "$REQUIRED" <<'PY' <<"$JOBS_BODY"
import json, sys
data = json.loads(sys.stdin.read())
target = sys.argv[1]
for j in data.get('jobs', []):
    if j.get('name') == target:
        print(j.get('status', ''))
        print(j.get('conclusion') or '')
        sys.exit(0)
sys.exit(1)
PY
    ) || LINE=""
    if [ -z "$LINE" ]; then
        FAIL_REASON="${FAIL_REASON}MISSING_JOB: '${REQUIRED}' not found in run ${RUN_ID}. "
        continue
    fi
    JOB_STATUS=$(printf '%s\n' "$LINE" | sed -n '1p')
    JOB_CONCLUSION=$(printf '%s\n' "$LINE" | sed -n '2p')
    if [ "$JOB_STATUS" != "completed" ] || [ "$JOB_CONCLUSION" != "success" ]; then
        FAIL_REASON="${FAIL_REASON}JOB_NOT_SUCCESS: '${REQUIRED}' status=${JOB_STATUS} conclusion=${JOB_CONCLUSION}. "
    fi
done

if [ -n "$FAIL_REASON" ]; then
    echo "GITHUB_CI_GATE_JOB_FAILED: $FAIL_REASON"
    echo "See $EVIDENCE_DIR/github-ci-jobs.tsv for the full job list."
    echo "FAIL: $FAIL_REASON" > "$EVIDENCE_DIR/github-ci-gate.txt"
    exit 5
fi

{
    echo "PASS: GHA SHA Gate for head_sha=$GITHUB_SHA on branch=$GITHUB_BRANCH"
    echo "Run id     : $RUN_ID"
    echo "Run url    : $RUN_HTML_URL"
    echo "Conclusion : success"
    echo "All required jobs completed successfully: ${REQUIRED_JOBS[*]}"
} > "$EVIDENCE_DIR/github-ci-gate.txt"

echo "GHA SHA Gate: PASS for head_sha=$GITHUB_SHA run=$RUN_ID"
exit 0