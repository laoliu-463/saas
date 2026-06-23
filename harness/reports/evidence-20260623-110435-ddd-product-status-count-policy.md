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
- Docker restart: not run.
- Health check: not run.
- Business API/browser validation: not run.
- Git commit: PASS for code batch `50de3f6d`.
- Git push: pending at this report generation.

## Residual Risk

- This remains a PARTIAL report.
- Current worktree remains dirty with multiple pre-existing changes.
- `harness/rules/state/snapshots/DOMAIN_STATUS.md` is already 201 lines, so this slice was not appended there to avoid increasing a known 200-line limit breach.
- Full completion requires isolating/staging the dirty batches and running the normal backend/full Harness gate.
