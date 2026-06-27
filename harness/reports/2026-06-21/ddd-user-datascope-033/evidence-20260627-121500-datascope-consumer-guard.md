# Evidence - DDD100 User DataScope Issue #33

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Report Base Commit | `33303d1c` before #33 commit |
| Worktree Status | DIRTY only with planned #33 files at report generation |
| Scope | backend test guard |
| Gate | Gate 1 - Backend test/architecture guard |
| Issue | #33 `[DDD100-USER-DATASCOPE] 数据范围剩余消费点收口` |

## Changed Files

- `backend/src/test/java/com/colonel/saas/architecture/DddUserDataScopeRemainingConsumerGuardTest.java`
- `backend/src/test/resources/ddd/data-scope-consumer-legacy-whitelist.txt`
- `harness/reports/2026-06-21/ddd-user-datascope-033/evidence-20260627-121500-datascope-consumer-guard.md`
- `harness/reports/2026-06-21/ddd-user-datascope-033/retro-20260627-121500-datascope-consumer-guard.md`
- `harness/engineering/issues-index.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- `harness/rules/changelog.md`

## Guard Contract

The new guard freezes remaining direct `DataScope` consumers outside the user domain.

- New non-user-domain direct self/group/all consumers fail.
- Retired consumers must be removed from the whitelist.
- Remaining business consumers must expose a `DataScopePolicy` path, except explicit test-time glue/context entries.

Frozen current consumers:

- `controller/OrderController.java`
- `domain/order/facade/LegacyOrderDomainFacade.java`
- `domain/performance/policy/PerformanceAccessContext.java`
- `domain/performance/policy/PerformanceAccessScope.java`
- `job/PerformanceCacheWarmupJob.java`
- `service/DashboardService.java`
- `service/data/DataApplicationService.java`
- `service/OrderAttributionService.java`
- `service/OrderQueryService.java`
- `service/OrderService.java`
- `service/PerformanceMetricsQueryService.java`
- `service/sample/SampleApplicationService.java`
- `service/SampleFilterOptionsService.java`
- `service/TalentQueryService.java`
- `service/TalentService.java`

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph intake | PASS | 13,450 nodes, 153,352 edges, 1,671 files; low risk for guard slice |
| New guard test | PASS | `mvn -q -f backend/pom.xml -Dtest=DddUserDataScopeRemainingConsumerGuardTest test` |
| DataScope guard + targeted consumers | PASS | `DddUserDataScopeRemainingConsumerGuardTest`, existing DataScope architecture tests, `OrderServiceTest`, `OrderQueryServiceTest`, `TalentServiceTest`, `TalentQueryServiceTest`, `PerformanceQueryServiceTest` |
| Backend compile | PASS | `mvn -q -f backend/pom.xml -DskipTests compile` |
| real-pre safety check | PASS | `safety-check.ps1 -Env real-pre -Scope backend` |
| Backend health | PASS | `verify-local.ps1 -Env real-pre -Scope backend`, health returned `{"status":"UP"}` |
| Docker status | PASS | `docker compose -f docker-compose.real-pre.yml ps`: backend/frontend/postgres/redis all `Up` and `healthy` |
| Harness limits | PASS | `check-harness-limits.ps1` returned `PASS` |
| Docker restart | SKIP | test-only guard; no production runtime artifact changed |
| Business E2E | SKIP | no behavior change; guard verifies regression boundary |

## Result

Status: PASS for #33 guard slice.

This does not claim all legacy data-scope branches are removed. It makes the remaining direct consumers explicit and prevents growth while preserving default Legacy grey behavior.

## Remaining Risks

- Frozen whitelist entries must be retired by later domain slices; they are debt, not completion.
- Legacy branches still duplicate self/group/all behavior when `ddd.refactor.data-scope-policy.enabled=false`.
- Full runtime data-scope parity remains tied to later behavior-changing issues and real-pre samples.
