# Evidence - DDD100 Guard Issue #31

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Scope | backend test guard |
| Gate | Gate 1 - Backend test/architecture guard |
| Issue | #31 `[DDD100-GUARD] 架构护栏与跨域依赖扫描收口` |

## Changed Files

- `backend/src/test/java/com/colonel/saas/architecture/DddArchitectureRedlineGuardTest.java`
- `backend/src/test/resources/ddd/architecture-redline-legacy-whitelist.txt`
- `harness/reports/2026-06-21/ddd-architecture-guard-031/evidence-20260627-115000-architecture-redline-guard.md`
- `harness/reports/2026-06-21/ddd-architecture-guard-031/retro-20260627-115000-architecture-redline-guard.md`
- `harness/engineering/issues-index.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- `harness/rules/changelog.md`

## What The Guard Covers

| Redline | Enforcement |
|---|---|
| Controller direct Mapper/Gateway dependency growth | Frozen whitelist blocks new imports under backend Controller packages |
| Controller legacy debt retirement drift | Whitelist must be updated when existing debt is removed |
| Domain api/query/policy/port Mapper penetration | Zero tolerance for MyBatis Mapper imports in strict domain layers |
| Frontend direct third-party HTTP calls | Zero tolerance for `fetch`/axios/request calls to absolute `http(s)` endpoints in non-test frontend source |

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph intake | PASS | 13,435 nodes, 153,236 edges, 1,669 files; medium risk from recent #60 test commit, not this guard |
| Targeted new guard test | PASS | `mvn -q -f backend/pom.xml -Dtest=DddArchitectureRedlineGuardTest test` |
| Combined architecture guard tests | PASS | `mvn -q -f backend/pom.xml "-Dtest=DddArchitectureRedlineGuardTest,DddCrossDomainMapperGuardTest,DddAnalyticsReadOnlyBoundaryTest" test` |
| Backend compile | PASS | `mvn -q -f backend/pom.xml -DskipTests compile` |
| real-pre safety check | PASS | `safety-check.ps1 -Env real-pre -Scope backend` |
| Harness limits | PASS | `check-harness-limits.ps1` returned `PASS`; reports root remains 49 files |
| Backend health | PASS | `verify-local.ps1 -Env real-pre -Scope backend`, `GET /api/system/health` returned `{"status":"UP"}` |
| Docker restart | SKIP | Test-only architecture guard; no production backend artifact, SQL, config, Docker, or frontend change |
| Business flow / E2E | SKIP | No runtime behavior change; guard verifies structural regression risk only |

## Current Frozen Debt

- Controller direct Mapper/Gateway imports are not declared fixed. They are frozen in `architecture-redline-legacy-whitelist.txt`.
- New entries fail the test.
- Removed entries fail the test until the whitelist is cleaned, so debt retirement remains visible.

## Result

Status: PASS for #31 backend architecture guard slice.

The slice adds repeatable scans and evidence for DDD100 redlines without changing API behavior, DB schema, default real-pre config, or Legacy grey boundaries.

## Remaining Risks

- Existing Controller debt remains and must be retired in later domain issues.
- Existing domain application/facade/infrastructure persistence dependencies are not all blocked here because they are active transition seams. #86/#87 should tighten those after ports and Legacy cleanup.
- Frontend guard blocks direct third-party HTTP calls, but not external navigation URLs returned by backend OAuth flows.
