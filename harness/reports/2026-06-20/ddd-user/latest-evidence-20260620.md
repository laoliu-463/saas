# Latest Evidence: DDD-USER-U7

## Metadata

- Latest capture: 2026-06-21 11:34:53 +08:00
- Env: real-pre
- Scope: backend
- Branch: `feature/ddd/DDD-VERIFY-001`
- Latest observed HEAD: `e7705025`
- Worktree: dirty; no stage/commit/push performed.
- Canonical report: `harness/reports/2026-06-20/ddd-user/evidence-20260620-230204.md`

## Latest Slice

- `SysUserQueryApplicationService` no longer imports QueryWrapper / Mapper / Entity types.
- Added `UserQueryLookup` and `SysUserQueryLookupAdapter`.
- User pagination SQL wrapper construction and role relation reads now sit behind the query lookup port.
- Data-scope decision remains in the application service and is passed to infrastructure as `UserQueryFilter`.

## Verification

- RED boundary test: query application service still imported persistence types before the port extraction.
- GREEN focused: 21 tests, 0 failures.
- GREEN regression: 84 tests, 0 failures.
- Backend package: PASS.
- Backend restart: PASS.
- Backend health: PASS, `{"status":"UP"}`.
- code-review-graph incremental update: PASS.
- Scoped diff check: PASS.
- Current untracked slice trailing-whitespace scan: PASS.
- Staged diff check: PASS.
- Global diff check: BLOCKED_UNRELATED by pre-existing trailing whitespace outside this slice.

## Conclusion

PASS for the current backend domain boundary slice.
