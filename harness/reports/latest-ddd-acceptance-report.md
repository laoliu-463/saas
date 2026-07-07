# DDD Acceptance Latest Report

## Metadata

- Time: 2026-07-07 15:58:07 +08:00
- Branch: codex/ddd-user-role-application
- HEAD: 3fda3d61
- Conclusion: PASS

## Dirty Files

- Total: 233
- docs/harness: 54
- known historical tests: 2
- unexpected non docs/harness: 177

~~~text
 M .env.example
 M backend/src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java
 M backend/src/main/java/com/colonel/saas/config/DataScopePolicyConfig.java
 M backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java
 M backend/src/main/java/com/colonel/saas/controller/AdminDouyinQuickSampleController.java
 M backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java
 M backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java
 M backend/src/main/java/com/colonel/saas/controller/ColonelsettlementActivityController.java
 M backend/src/main/java/com/colonel/saas/controller/DataController.java
 M backend/src/main/java/com/colonel/saas/controller/DouyinController.java
 M backend/src/main/java/com/colonel/saas/controller/OrderController.java
 M backend/src/main/java/com/colonel/saas/controller/PerformanceController.java
 M backend/src/main/java/com/colonel/saas/controller/ProductController.java
 M backend/src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
 M backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
 M backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java
 M backend/src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentCheckApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/talent/application/TalentPageApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserQueryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java
 M backend/src/main/java/com/colonel/saas/service/DashboardService.java
 M backend/src/main/java/com/colonel/saas/service/OrderAttributionService.java
 M backend/src/main/java/com/colonel/saas/service/OrderService.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
 M backend/src/main/java/com/colonel/saas/service/PerformanceQueryService.java
 M backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java
 M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M backend/src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java
 M backend/src/main/java/com/colonel/saas/service/SysConfigService.java
... omitted 198 more
~~~

## Whitelist

| File | Active | Total Lines |
| --- | ---: | ---: |
| cross-domain-mapper-legacy-whitelist.txt | 0 | 10 |
| architecture-redline-legacy-whitelist.txt | 0 | 3 |

## Matrix

| DONE | PARTIAL | TODO | BLOCKED | Total |
| ---: | ---: | ---: | ---: | ---: |
| 74 | 71 | 27 | 6 | 178 |

## Checks

| Check | Status | Summary | Command |
| --- | --- | --- | --- |
| git status | WARN | dirtyFiles=233 | git status --short |
| cross-domain mapper whitelist | PASS | active=0, totalLines=10 |  |
| architecture redline whitelist | PASS | active=0, totalLines=3 |  |
| DDD evidence matrix | PASS | DONE=74, PARTIAL=71, TODO=27, BLOCKED=6, total=178 |  |
| git diff --check | PASS | exitCode=0 | git diff --check |
| check-harness-limits | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 |
| safety-check docs dry-run | PASS | exitCode=0 | powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun |
| mvn compile | PASS | exitCode=0 | cd backend; mvn -DskipTests compile |
| DddArchitectureRedlineGuardTest | PASS | exitCode=0 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| DddArchitectureRedlineGuardTest surefire | PASS | tests=4, failures=0, errors=0, skipped=0, files=1 | cd backend; mvn test -Dtest='DddArchitectureRedlineGuardTest' |
| wide DDD architecture tests | PASS | exitCode=0 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |
| wide DDD architecture tests surefire | PASS | tests=246, failures=0, errors=0, skipped=1, files=82 | cd backend; mvn test -Dtest='*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test' |

## Warnings

- Unexpected non docs/harness dirty file: .env.example
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/aspect/RoleGuardAspect.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/config/DataScopePolicyConfig.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/AdminDouyinQuickSampleController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/ColonelActivityController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/ColonelsettlementActivityController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/DataController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/DouyinController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/OrderController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/PerformanceController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/controller/ProductController.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/application/OrderSampleHomeworkBridge.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/sample/application/SampleApplicationPortImpl.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/sample/policy/SampleActionPermissionPolicy.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/talent/application/ExclusiveTalentCheckApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/talent/application/TalentClaimApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/talent/application/TalentPageApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/user/application/SysUserQueryApplicationService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/domain/user/facade/LegacyUserDomainFacade.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/DashboardService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/OrderAttributionService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/OrderService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/PerformanceQueryService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/ProductQuickSampleService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/ProductService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/SampleFilterOptionsService.java
- Unexpected non docs/harness dirty file: backend/src/main/java/com/colonel/saas/service/SysConfigService.java
- ... omitted 144 more

## Failures

(none)

## Next Steps

- If conclusion is FAIL, fix the listed failures and rerun this script.
- Keep cross-domain mapper whitelist at 0.
- Reduce architecture redline debt by lowering -MaxRedlineDebt over future DDD slices.
