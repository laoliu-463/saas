# Evidence Report

## Metadata

- Time: 2026-07-16 21:11:50 +08:00
- Environment: real-pre
- Scope: backend
- Branch: feature/auth-system
- Commit: d724aaa5
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
backend/src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAttributionAdjustmentService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAttributionResolver.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationRetryService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentService.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAttributionPolicy.java
backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java
backend/src/main/java/com/colonel/saas/domain/product/application/port/CopyPromotionSupportPort.java
backend/src/main/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicy.java
backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java
backend/src/main/java/com/colonel/saas/entity/PerformanceAdjustmentLedger.java
backend/src/main/java/com/colonel/saas/entity/PerformanceAttributionAdjustment.java
backend/src/main/java/com/colonel/saas/entity/PerformanceCalculationExecution.java
backend/src/main/java/com/colonel/saas/entity/PerformanceRecord.java
backend/src/main/java/com/colonel/saas/entity/PickSourceMapping.java
backend/src/main/java/com/colonel/saas/entity/PromotionLink.java
backend/src/main/java/com/colonel/saas/event/OrderSyncedEvent.java
backend/src/main/java/com/colonel/saas/job/PerformanceRecalculateFailedJob.java
backend/src/main/java/com/colonel/saas/listener/OrderSyncedEventListener.java
backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
backend/src/main/java/com/colonel/saas/mapper/PerformanceAdjustmentLedgerMapper.java
backend/src/main/java/com/colonel/saas/mapper/PerformanceAttributionAdjustmentMapper.java
backend/src/main/java/com/colonel/saas/mapper/PerformanceCalculationExecutionMapper.java
backend/src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java
backend/src/main/java/com/colonel/saas/service/ExclusiveMerchantService.java
backend/src/main/java/com/colonel/saas/service/ExclusiveTalentService.java
backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/main/resources/db/alter-order-default-attribution-dimensions-20260716.sql
backend/src/main/resources/db/alter-performance-final-attribution-20260716.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml
backend/src/main/resources/mapper/PerformanceRecordMapper.xml
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceDomainInventoryTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceExceptionAndDuplicateContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceOrderBoundaryContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRefundReversalSummaryContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceSummaryRefreshEntrypointContractTest.java
backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java
backend/src/test/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapperTest.java
backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationRetryServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java
backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAttributionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicyTest.java
backend/src/test/java/com/colonel/saas/listener/OrderSyncedEventListenerTest.java
backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java
backend/src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java
 M backend/src/main/java/com/colonel/saas/controller/ProductController.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapper.java
 M backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java
 M backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScope.java
 M backend/src/main/java/com/colonel/saas/domain/performance/policy/PerformanceAttributionPolicy.java
 M backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/product/application/port/CopyPromotionSupportPort.java
 M backend/src/main/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicy.java
 M backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java
 M backend/src/main/java/com/colonel/saas/entity/PerformanceRecord.java
 M backend/src/main/java/com/colonel/saas/entity/PickSourceMapping.java
 M backend/src/main/java/com/colonel/saas/entity/PromotionLink.java
 M backend/src/main/java/com/colonel/saas/event/OrderSyncedEvent.java
 M backend/src/main/java/com/colonel/saas/job/PerformanceRecalculateFailedJob.java
 M backend/src/main/java/com/colonel/saas/listener/OrderSyncedEventListener.java
 M backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
 M backend/src/main/java/com/colonel/saas/service/DashboardPerformanceSummaryService.java
 M backend/src/main/java/com/colonel/saas/service/ExclusiveMerchantService.java
 M backend/src/main/java/com/colonel/saas/service/ExclusiveTalentService.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
 M backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java
 M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M backend/src/main/resources/db/migrate-all.sql
 M backend/src/main/resources/mapper/ColonelsettlementOrderMapper.xml
 M backend/src/main/resources/mapper/PerformanceRecordMapper.xml
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceDomainInventoryTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceExceptionAndDuplicateContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceOrderBoundaryContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRefundReversalSummaryContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceSummaryRefreshEntrypointContractTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/event/OrderEventPayloadMapperTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceSummaryApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAccessScopeTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/policy/PerformanceAttributionPolicyTest.java
 M backend/src/test/java/com/colonel/saas/domain/product/policy/PromotionAttributionOwnerPolicyTest.java
 M backend/src/test/java/com/colonel/saas/listener/OrderSyncedEventListenerTest.java
 M backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java
 M backend/src/test/java/com/colonel/saas/service/DashboardPerformanceSummaryServiceTest.java
?? backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAttributionAdjustmentService.java
?? backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAttributionResolver.java
?? backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionService.java
?? backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceCalculationRetryService.java
?? backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentService.java
?? backend/src/main/java/com/colonel/saas/entity/PerformanceAdjustmentLedger.java
?? backend/src/main/java/com/colonel/saas/entity/PerformanceAttributionAdjustment.java
?? backend/src/main/java/com/colonel/saas/entity/PerformanceCalculationExecution.java
?? backend/src/main/java/com/colonel/saas/mapper/PerformanceAdjustmentLedgerMapper.java
?? backend/src/main/java/com/colonel/saas/mapper/PerformanceAttributionAdjustmentMapper.java
?? backend/src/main/java/com/colonel/saas/mapper/PerformanceCalculationExecutionMapper.java
?? backend/src/main/resources/db/alter-order-default-attribution-dimensions-20260716.sql
?? backend/src/main/resources/db/alter-performance-final-attribution-20260716.sql
?? backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionServiceTest.java
?? backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationRetryServiceTest.java
?? backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceRefundAdjustmentServiceTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    32 seconds ago   Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   9 minutes ago    Up 8 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 hours ago      Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      30 hours ago     Up 30 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 8 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 30 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS ($deadline = (Get-Date).AddSeconds(60); do { try { $response = Invoke-WebRequest -UseBasicParsing http://127.0.0.1:3001/ -TimeoutSec 3; if ($response.StatusCode -eq 200) { npm run e2e:real-pre:p0:preflight; exit $LASTEXITCODE } } catch { } Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw "frontend real-pre did not become ready within 60 seconds")
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

改进：退款计算执行台账保存最小事件输入快照，失败重试先恢复同一调整流水再发布计算完成事件；验证：PerformanceCalculationRetryServiceTest 覆盖退款恢复路径。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
