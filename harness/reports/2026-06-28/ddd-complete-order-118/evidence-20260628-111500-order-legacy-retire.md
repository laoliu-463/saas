# #118 Order Legacy Service Retire Evidence

## Metadata

- Time: 2026-06-28 11:15 +08:00
- Env: real-pre local
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit before change: 93ca22a6
- Issue: #118 `[DDD-COMPLETE-100-ORDER-06]`
- Scope: backend order domain package migration, tests, docs

## Change Summary

- Retired 13 legacy `com.colonel.saas.service.Order*` source files into `domain/order`.
- `application`: `OrderService`, `OrderSyncService`, `OrderAttributionService`, `OrderAttributionReplayService`.
- `query`: `OrderQueryService`.
- `infrastructure`: 1603/2704/6468 dry-run services, payment schema bootstrap, sync dedup bootstrap, sync persistence.
- `policy`: `OrderCommissionPolicy`, `OrderDualTrackAmountResolver`.
- Updated controllers, jobs, webhook service, test support, facades, application wrappers and tests to the new packages.
- No intentional business rule, sync window, SQL mapper, order fact, attribution, performance event or API contract change.

## Static Evidence

- code-review-graph `detect_changes` after isolating unrelated product work: 74 changed files, 63 affected flows, 9 test gaps, risk 0.55.
- `rg` old `com.colonel.saas.service.Order*` imports in `backend/src/main/java backend/src/test/java`: no matches.
- `backend/src/main/java/com/colonel/saas/service/Order*.java`: no files remain.
- Architecture guard paths updated for moved `OrderSyncService`.

## Build And Test Evidence

- `mvn -f backend/pom.xml -DskipTests compile`: PASS.
- `mvn -f backend/pom.xml test-compile`: PASS, 409 test sources compiled.
- Targeted command:
  `mvn -f backend/pom.xml "-Dtest=OrderControllerTest,OrderSyncControllerTest,OrderAttributionControllerTest,DouyinWebhookControllerTest,DouyinWebhookEventServiceTest,OrderSyncApplicationServiceTest,OrderSyncServiceTest,OrderSyncPersistenceServiceTest,OrderSyncPersistenceInstituteSettlementTest,OrderAttributionReplayServiceTest,OrderAttributionServiceTest,OrderQueryServiceTest,OrderServiceTest,Order1603SettlementDryRunServiceTest,Order2704SettlementDryRunServiceTest,Order6468PaginationDryRunServiceTest,OrderPaymentSchemaBootstrapTest,OrderSyncDedupSchemaBootstrapTest,OrderSyncJobTest,DddOrder003RoutingTest,DddSample004HomeworkRoutingTest,TestDataServiceTest" test`
- Targeted result: PASS, 242 tests, 0 failures, 0 errors.
- Slim guards:
  `mvn -f backend/pom.xml "-Dtest=DddSlimOrder001RoutingTest,DddSlimOrder002RoutingTest" test`
- Slim guard result: PASS, 2 tests, 0 failures, 0 errors.

## Migration Metrics

Command:
`powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\probes\ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown`

- Global raw domain share: 29.7%.
- Global business migration proxy: 39.1%.
- Order domain DDD LOC: 7717.
- Order domain legacy service LOC: 0.
- Order domain legacy entry LOC: 1441.
- Order domain proxy: 84.3%.

## Harness

- Full `agent-do` build/restart/health/business validation is required after this report.
- Final local Harness evidence should be recorded in `harness/reports/latest-evidence-20260628.md`.

## Conclusion

- Local code-level #118 validation is PASS.
- Final Definition of Done remains dependent on `agent-do` PASS and GitHub issue sync.

## Residual Risk

- #117 remains open because real-pre still needs a true `colonelsettlement_order.pick_source` positive sample.
- #93 order epic is not automatically complete just because #118 is complete.
- `OrderDualTrackAmountResolver` remains deprecated and retained only for dry-run/fixture compatibility.
