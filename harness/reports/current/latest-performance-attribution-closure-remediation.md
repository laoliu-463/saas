# Evidence Report

## Metadata

- Time: 2026-07-16 18:52:01 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 1c5e8b89
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java
backend/src/main/java/com/colonel/saas/controller/OrderController.java
backend/src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
backend/src/main/java/com/colonel/saas/domain/order/event/OrderAttributionReplayedEvent.java
backend/src/main/java/com/colonel/saas/domain/order/event/OrderDomainEventPublisher.java
backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/order/facade/OrderDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessContext.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessScope.java
backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java
backend/src/main/java/com/colonel/saas/service/OrderQueryService.java
backend/src/main/java/com/colonel/saas/service/OrderService.java
backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
backend/src/test/java/com/colonel/saas/controller/OrderSyncControllerTest.java
backend/src/test/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java
backend/src/test/java/com/colonel/saas/domain/order/policy/OrderAccessScopeTest.java
backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java
backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java
backend/src/test/java/com/colonel/saas/service/OrderServiceTest.java
backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java
docs/04-事件契约总表.md
docs/superpowers/plans/2026-07-16-performance-attribution-closure-remediation.md
docs/领域/订单域.md
docs/领域/业绩域.md
docs/流程/订单归因链路.md
docs/流程/业绩计算链路.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/constant/OrderDomainEventTypes.java
 M backend/src/main/java/com/colonel/saas/controller/OrderController.java
 M backend/src/main/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/domain/order/event/OrderDomainEventPublisher.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderDomainFacade.java
 M backend/src/main/java/com/colonel/saas/domain/order/facade/OrderDomainFacade.java
 M backend/src/main/java/com/colonel/saas/listener/PerformanceRecordSyncListener.java
 M backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java
 M backend/src/main/java/com/colonel/saas/service/OrderQueryService.java
 M backend/src/main/java/com/colonel/saas/service/OrderService.java
 M backend/src/main/java/com/colonel/saas/service/OrderSyncPersistenceService.java
 M backend/src/test/java/com/colonel/saas/controller/OrderControllerTest.java
 M backend/src/test/java/com/colonel/saas/controller/OrderSyncControllerTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/application/OrderDetailQueryApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/listener/PerformanceRecordSyncListenerTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/OrderSyncPersistenceServiceTest.java
 M docs/04-事件契约总表.md
 M docs/流程/业绩计算链路.md
 M docs/流程/订单归因链路.md
 M docs/领域/业绩域.md
 M docs/领域/订单域.md
?? backend/src/main/java/com/colonel/saas/domain/order/event/OrderAttributionReplayedEvent.java
?? backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessContext.java
?? backend/src/main/java/com/colonel/saas/domain/order/policy/OrderAccessScope.java
?? backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java
?? backend/src/test/java/com/colonel/saas/domain/order/policy/OrderAccessScopeTest.java
?? docs/superpowers/plans/2026-07-16-performance-attribution-closure-remediation.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    46 seconds ago   Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   43 seconds ago   Up 11 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   47 seconds ago   Up 40 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      27 hours ago     Up 27 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 12 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 29 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 41 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 27 hours (healthy)     6379/tcp
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
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
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

已补齐订单归因冻结、招商个人查询和版本化 Outbox 重放；后续需由业务确认招商组长使用历史部门快照还是当前组织关系。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
