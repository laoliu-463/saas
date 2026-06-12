# Retro Summary - DOUYIN-SIGNATURE-INVALID-AUDIT-001

- Time: 2026-06-05 15:12:56 CST
- Scope: readonly audit / reports only
- Conclusion: no Harness code upgrade performed in this task

## What Worked

- Code-review-graph was checked first, then fallback `rg` was used because the graph had no useful Douyin signature hits.
- Runtime evidence separated 6468/2704 order paths from the shared signing path.
- Local and remote real-pre were compared without printing secrets.
- Preflight confirmed the baseline environment and token readiness are PASS.

## What Was Missing

- There is no single runbook for Douyin app secret rotation/reset validation.
- Existing tests do not pin the request signing contract strongly enough to distinguish local signing drift from platform credential drift.
- Failure logs do not currently include enough sanitized request-signing metadata for fast signature-invalid triage.

## Suggested Harness Improvements

1. Add a Douyin credential drift checklist to the real-pre debug skill.
2. Add a sanitized upstream signature failure collector that records method, app key mask, timestamp length, param JSON hash, response code, subCode and log_id.
3. Add a contract test comparing `DouyinApiClient` request shape to bundled SDK 1.1.0 behavior without real credentials.
4. Add a report template section for "platform-side evidence required" so PARTIAL/BLOCKED conclusions are explicit.

## Non-Actions

- Did not update Harness scripts.
- Did not update source code.
- Did not stage unrelated pre-existing changes.
