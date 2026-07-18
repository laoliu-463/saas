# Evidence Report

## Metadata

- Time: 2026-07-18 12:50:29 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 167d1a0d
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/DashboardController.java
backend/src/main/java/com/colonel/saas/controller/DataController.java
backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/main/java/com/colonel/saas/service/DashboardService.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
backend/src/test/java/com/colonel/saas/controller/DataControllerTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package); Frontend standalone build: PASS (npm run build); Targeted permission tests: PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-frontend-real-pre-1   colonel-saas/frontend:0fb9e3c834df5ddf6614d23c1bd6782479ac42f3   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago     Up 2 hours (healthy)     127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                               "docker-entrypoint.s…"   postgres-real-pre   6 minutes ago   Up 6 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                   "docker-entrypoint.s…"   redis-real-pre      6 minutes ago   Up 6 minutes (healthy)   6379/tcp
NAMES                             STATUS                    PORTS
saas-active-postgres-real-pre-1   Up 6 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      Up 6 minutes (healthy)    6379/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
campus_frontend                   Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 46 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 46 minutes (healthy)   6379/tcp
~~~

## Health Check Result

~~~text
BLOCKED: copied worktree env does not match the existing PostgreSQL volume; backend startup failed with password authentication failed for user saas. Backend container was stopped to prevent a restart loop. PostgreSQL, Redis, and frontend containers remain healthy.
~~~

## Business Validation Result

~~~text
BLOCKED: real-pre business validation was not executed because backend could not authenticate to PostgreSQL.
~~~

## Content Maintenance Result

~~~text
Not run: runtime gate blocked by database credential mismatch.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Actionable improvement: recover the original real-pre env credential or explicitly authorize resetting the local PostgreSQL saas role password, then rerun backend agent-do and business validation.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
