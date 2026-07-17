# Evidence Report

## Metadata

- Time: 2026-07-17 19:28:21 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: b33bf84c
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java
backend/src/main/java/com/colonel/saas/domain/performance/application/DefaultPerformanceAttributionResolver.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/main/resources/db/alter-cso-dual-attribution-status-20260716.sql
backend/src/main/resources/db/init-db.sql
backend/src/test/java/com/colonel/saas/architecture/ColonelsettlementOrderMapperDualDimensionContractTest.java
backend/src/test/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolverTest.java
backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/DefaultPerformanceAttributionResolverTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionResult.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/DefaultPerformanceAttributionResolver.java
 M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
 M backend/src/main/resources/db/alter-cso-dual-attribution-status-20260716.sql
 M backend/src/main/resources/db/init-db.sql
 M backend/src/test/java/com/colonel/saas/architecture/ColonelsettlementOrderMapperDualDimensionContractTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/application/OrderDefaultAttributionResolverTest.java
 M backend/src/test/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicyTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/DefaultPerformanceAttributionResolverTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
 M backend/src/test/resources/db/mapper-integration-schema.sql
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    56 seconds ago   Up 52 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   5 hours ago      Up 5 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 52 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 5 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 2 days (healthy)       6379/tcp
campus_frontend                   Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation skipped by -SkipBusinessValidation; not a full PASS.
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

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
