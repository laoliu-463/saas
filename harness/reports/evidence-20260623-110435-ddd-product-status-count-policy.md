# Evidence Report

## Metadata

- Time: 2026-06-23 11:04:35 +08:00
- Environment: local real-pre workspace
- Branch: feature/product-manage-fallback-fix-20260623
- Code commit: 50de3f6d
- Base before slice: c4529742
- Scope: backend / product-domain DDD slice
- Deploy remote: false
- Conclusion: PARTIAL

## Change Under Test

- Added `ProductDisplayPolicy.normalizeActivityProductStatusCounts`.
- `ProductService.loadActivityProductStatusCounts` now delegates raw aggregate normalization to `ProductDisplayPolicy`.
- No SQL semantic change was introduced by this DDD slice.
- No API field name was changed by this DDD slice.
- No production feature switch or environment variable was changed.

## Boundary Check

- Primary domain: Product domain.
- Related consumer: frontend product activity page.
- Allowed dependency: ProductService may call product policy and product snapshot mapper.
- Forbidden dependency: frontend must not own product status business rules; product domain must not calculate order attribution or commission.
- Stage conclusion: this slice only moves output normalization from service helper to product policy.

## Verification

```text
RED:
cmd /c mvn -f backend/pom.xml -Dtest=ProductDisplayPolicyTest test
Result: FAIL at testCompile, expected
Reason: ProductDisplayPolicy lacked normalizeActivityProductStatusCounts

Targeted GREEN:
cmd /c mvn -f backend/pom.xml -Dtest=ProductDisplayPolicyTest#activityProductStatusCounts_shouldNormalizeRawAggregateContract,DddSlimProduct001DisplayPolicyRoutingTest,ProductServiceActivityStatusIndependenceTest test
Result: PASS
Tests: 19 run, 0 failures, 0 errors, 0 skipped

Follow-up conflict alignment:
cmd /c mvn -f backend/pom.xml -Dtest=ProductDisplayPolicyTest test
Result: PASS
Tests: 21 run, 0 failures, 0 errors, 0 skipped

Combined DDD slice verification:
cmd /c mvn -f backend/pom.xml -Dtest=ProductDisplayPolicyTest,DddSlimProduct001DisplayPolicyRoutingTest,ProductServiceActivityStatusIndependenceTest test
Result: PASS
Tests: 39 run, 0 failures, 0 errors, 0 skipped

Backend package:
cmd /c mvn -f backend/pom.xml -DskipTests package
Result: PASS

Whitespace:
git diff --check
Result: PASS, with CRLF/LF warning for latest-harness-limits-check.md

Harness limits:
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1
Result: PASS

real-pre safety check:
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope backend
Result: PASS
Env guard: APP_TEST_ENABLED=false, DOUYIN_TEST_ENABLED=false, DOUYIN_REAL_UPSTREAM_MODE=live

real-pre backend restart:
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend
Result: PASS
Backend image rebuilt and backend container restarted; no volume deletion was performed.

real-pre health check:
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope backend
Result: PASS
Health URL: http://127.0.0.1:8081/api/system/health
Body: {"status":"UP"}

real-pre P0 preflight:
npm run e2e:real-pre:p0:preflight
Result: PASS
Output: runtime/qa/out/real-pre-preflight-20260623-122649

real-pre read-only API probe:
GET /api/colonel/activities/3916506/products?count=20
Result: PASS, HTTP 200
statusCounts: total=1296, pendingReview=10, promoting=732, rejected=502, terminated=46, expired=6
Output: runtime/qa/out/activity-status-counts-probe-20260623-124330
```

## Resolved Conflict Evidence

```text
cmd /c mvn -f backend/pom.xml -Dtest=ProductDisplayPolicyTest test
Previous result: FAIL
Previous failing test:
ProductDisplayPolicyTest.unsupportedStatusFour_shouldNotFallbackToTerminated

Observed failure:
expected officialStatus not to equal TERMINATED, but actual was TERMINATED.

Resolution:
The current branch already contains commit e34cc92c "fix: map activity product status 4 to terminated".
The test was updated to assert numeric upstream status=4 maps to TERMINATED while text-only "合作前取消" does not.
New result: PASS, 21 tests.
```

This report treats status=4 -> TERMINATED as the current branch's pre-existing production code fact.

## Not Collected

- code-review-graph MCP: unavailable in this session; `tool_search` only exposed Codex thread/automation tools.
- `agent-do.ps1 -Scope backend`: not run because the current worktree already contains unrelated frontend, docs, mapper, report cleanup, and product-status changes.
- Git commit: PASS for code batch `50de3f6d`.
- Git push: PASS for branch `feature/product-manage-fallback-fix-20260623`.
  - gitee: through `e25399f2`
  - origin: through `e25399f2`
- Remote deploy: not requested, not run.

## Residual Risk

- This remains a PARTIAL report.
- Current worktree remains dirty with multiple pre-existing changes.
- `harness/rules/state/snapshots/DOMAIN_STATUS.md` is already 201 lines, so this slice was not appended there to avoid increasing a known 200-line limit breach.
- Full completion still requires isolating/staging the dirty batches or running the integrated Harness gate from a batch-clean worktree.
