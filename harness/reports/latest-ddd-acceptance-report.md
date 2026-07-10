# DDD Acceptance Latest Report

## Metadata

- Time: 2026-07-10 12:59:57 +08:00
- Branch: codex/ddd-user-role-application
- HEAD: 6598d623
- Conclusion: PASS

## Dirty Files

- Total: 137
- docs/harness: 36
- known historical tests: 0
- unexpected non docs/harness: 101

~~~text
 M .claude/skills/README.md
 M .codex/config.toml
 M backend/src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java
 M backend/src/main/java/com/colonel/saas/constant/ProductDomainEventTypes.java
 M backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsAggregationService.java
 M backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java
 M backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventRouter.java
 M backend/src/main/java/com/colonel/saas/domain/analytics/event/AnalyticsEventTypes.java
 M backend/src/main/java/com/colonel/saas/domain/event/ConfigChangedEventRouter.java
 M backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/OrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/entity/CommissionRule.java
 M backend/src/main/java/com/colonel/saas/event/PerformanceCalculatedEvent.java
 M backend/src/main/java/com/colonel/saas/listener/AnalyticsShadowEventListener.java
 M backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
 M backend/src/main/java/com/colonel/saas/service/CommissionRuleService.java
 M backend/src/main/java/com/colonel/saas/service/CommissionService.java
 M backend/src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java
 M backend/src/main/java/com/colonel/saas/service/OrderCommissionPolicy.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
 M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
 M backend/src/main/resources/db/alter-v2-config-20260523.sql
 M backend/src/main/resources/db/migrate-all.sql
 M backend/src/test/java/com/colonel/saas/architecture/DddConfig003ConfigRoutingTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddTalentOrderFacadeBoundaryTest.java
 M backend/src/test/java/com/colonel/saas/controller/PerformanceControllerTest.java
 M backend/src/test/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumerTest.java
... omitted 102 more
~~~

## Whitelist

| File | Active | Total Lines |
| --- | ---: | ---: |
| cross-domain-mapper-legacy-whitelist.txt | 0 | 10 |
| architecture-redline-legacy-whitelist.txt | 0 | 3 |

## Matrix

| DONE | PARTIAL | TODO | BLOCKED | Total |
| ---: | ---: | ---: | ---: | ---: |
| 132 | 38 | 0 | 8 | 178 |

## Checks

| Check | Status | Summary | Command |
| --- | --- | --- | --- |
| git status | WARN | dirtyFiles=137 | git status --short |
| cross-domain mapper whitelist | PASS | active=0, totalLines=10 |  |
| architecture redline whitelist | PASS | active=0, totalLines=3 |  |
| DDD evidence matrix | PASS | DONE=132, PARTIAL=38, TODO=0, BLOCKED=8, total=178 |  |
| git diff --check | PASS | exitCode=0 | git diff --check |
| check-harness-limits | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 |
| safety-check docs dry-run | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun |
| mvn -DskipTests compile | SKIP | skipped by -DocsOnly | cd backend; mvn -DskipTests compile |
| DddArchitectureRedlineGuardTest | SKIP | skipped by -DocsOnly | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| wide DDD architecture tests | SKIP | skipped by -DocsOnly | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |

## Warnings

- Unexpected non docs/harness dirty file: .claude/skills/README.md
- Unexpected non docs/harness dirty file: .codex/config.toml
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/constant/ProductDomainEventTypes.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsAggregationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumer.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/analytics/application/AnalyticsEventRouter.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/analytics/event/AnalyticsEventTypes.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/event/ConfigChangedEventRouter.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/event/OrderDomainEventPublisher.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/entity/CommissionRule.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/event/PerformanceCalculatedEvent.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/listener/AnalyticsShadowEventListener.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/CommissionRuleService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/CommissionService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/OrderCommissionPolicy.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/ProductService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
- Unexpected non docs/harness dirty file: backend/src/main/resources/db/alter-v2-config-20260523.sql
- Unexpected non docs/harness dirty file: backend/src/main/resources/db/migrate-all.sql
- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/architecture/DddConfig003ConfigRoutingTest.java
- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java
- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/architecture/DddTalentOrderFacadeBoundaryTest.java
- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/controller/PerformanceControllerTest.java
- Unexpected non docs/harness dirty file: backend/src/test/java/com/colonel/saas/domain/analytics/application/AnalyticsEventConsumerTest.java
- ... omitted 66 more

## Failures

(none)

## Next Steps

- If conclusion is FAIL, fix the listed failures and rerun this script.
- Keep cross-domain mapper whitelist at 0.
- Reduce architecture redline debt by lowering -MaxRedlineDebt over future DDD slices.
