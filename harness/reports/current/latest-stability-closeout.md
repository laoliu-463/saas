# Evidence Report

## Metadata

- Time: 2026-07-19 14:26:34 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: ea7a8f83
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/OutboxAdminController.java
backend/src/main/java/com/colonel/saas/domain/event/DomainEventOutboxMapper.java
backend/src/main/java/com/colonel/saas/domain/event/DomainEventOutboxService.java
backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
backend/src/main/java/com/colonel/saas/entity/OperationLog.java
backend/src/main/java/com/colonel/saas/gateway/logistics/query/Kuaidi100LogisticsQueryGateway.java
backend/src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java
backend/src/main/java/com/colonel/saas/security/OperationLogInterceptor.java
backend/src/main/java/com/colonel/saas/security/OperationLogResponseAdvice.java
backend/src/main/java/com/colonel/saas/service/MerchantService.java
backend/src/main/java/com/colonel/saas/service/OperationLogService.java
backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java
backend/src/main/java/com/colonel/saas/service/SampleLogisticsSyncService.java
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate/V20260719_001__operation_log_observability.sql
backend/src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java
backend/src/test/java/com/colonel/saas/domain/event/DomainEventOutboxServiceTest.java
backend/src/test/java/com/colonel/saas/gateway/logistics/query/Kuaidi100LogisticsQueryGatewayTest.java
backend/src/test/java/com/colonel/saas/gateway/logistics/query/LogisticsGatewayRouterTest.java
backend/src/test/java/com/colonel/saas/job/DomainEventDispatcherJobTest.java
backend/src/test/java/com/colonel/saas/security/OperationLogInterceptorTest.java
backend/src/test/java/com/colonel/saas/security/OperationLogResponseAdviceTest.java
backend/src/test/java/com/colonel/saas/service/OperationLogServiceTest.java
backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java
backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java
backend/src/test/java/com/colonel/saas/service/SampleLogisticsSyncServiceTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
docs//345/257/271/346/216/245//347/211/251/346/265/201/346/216/245/345/217/243.md
docs//351/242/206/345/237/237//345/257/204/346/240/267/345/237/237.md
docs/04-/344/272/213/344/273/266/345/245/221/347/272/246/346/200/273/350/241/250.md
docs/06-/346/225/260/346/215/256/346/250/241/345/236/213/346/200/273/350/241/250.md
docs/09-/346/265/213/350/257/225/351/252/214/346/224/266/346/200/273/350/247/210.md
docs/10-/351/203/250/347/275/262/350/277/220/350/241/214/346/200/273/350/247/210.md
harness/scripts/commands/agent-do.ps1
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/OutboxAdminController.java
 M backend/src/main/java/com/colonel/saas/domain/event/DomainEventOutboxMapper.java
 M backend/src/main/java/com/colonel/saas/domain/event/DomainEventOutboxService.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/entity/OperationLog.java
 M backend/src/main/java/com/colonel/saas/gateway/logistics/query/Kuaidi100LogisticsQueryGateway.java
 M backend/src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java
 M backend/src/main/java/com/colonel/saas/security/OperationLogInterceptor.java
 M backend/src/main/java/com/colonel/saas/service/MerchantService.java
 M backend/src/main/java/com/colonel/saas/service/OperationLogService.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncService.java
 M backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java
 M backend/src/main/java/com/colonel/saas/service/SampleLogisticsSyncService.java
 M backend/src/main/resources/db/init-db.sql
 M backend/src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java
 M backend/src/test/java/com/colonel/saas/domain/event/DomainEventOutboxServiceTest.java
 M backend/src/test/java/com/colonel/saas/gateway/logistics/query/Kuaidi100LogisticsQueryGatewayTest.java
 M backend/src/test/java/com/colonel/saas/gateway/logistics/query/LogisticsGatewayRouterTest.java
 M backend/src/test/java/com/colonel/saas/job/DomainEventDispatcherJobTest.java
 M backend/src/test/java/com/colonel/saas/security/OperationLogInterceptorTest.java
 M backend/src/test/java/com/colonel/saas/service/OperationLogServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderSyncServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/SampleLogisticsSyncServiceTest.java
 M backend/src/test/resources/db/mapper-integration-schema.sql
 M harness/scripts/commands/agent-do.ps1
?? backend/src/main/java/com/colonel/saas/security/OperationLogResponseAdvice.java
?? backend/src/main/resources/db/migrate/V20260719_001__operation_log_observability.sql
?? backend/src/test/java/com/colonel/saas/security/OperationLogResponseAdviceTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   20 hours ago         Up 20 hours (healthy)         5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      18 hours ago         Up 18 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 18 hours (healthy)         6379/tcp
saas-test-frontend-1              Up 19 hours (unhealthy)       0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 19 hours (healthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 19 hours (healthy)         0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 20 hours (healthy)         5432/tcp
campus_frontend                   Up 21 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 21 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 21 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 21 hours (healthy)         6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (& mvn -q -f backend/pom.xml '-Dtest=Kuaidi100LogisticsQueryGatewayTest,LogisticsGatewayRouterTest,SampleLogisticsSyncServiceTest,Kuaidi100CallbackApplicationServiceTest,DomainEventOutboxServiceTest,DomainEventDispatcherJobTest,DddOutbox001OrderRoutingTest,OrderSyncPersistenceServiceTest,OrderSyncServiceTest,OperationLogServiceTest,OperationLogInterceptorTest,OperationLogResponseAdviceTest,MerchantServiceTest,PickSourceMappingServiceTest,OperationLogRetentionAcceptanceTest,LogCleanupJobTest,CurrentUserPasswordAuditIntegrationTest' test)
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

物流限频、Outbox 重放语义和操作日志必须以可观测状态与真实变化为准；回调 HTTPS 与线上积压作为部署后独立验证项。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
