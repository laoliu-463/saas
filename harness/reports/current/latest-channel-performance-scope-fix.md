# Evidence Report

## Metadata

- Time: 2026-07-18 13:05:49 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 37ef0931
- Owned worktree: dirty
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
harness/reports/current/latest-channel-performance-scope-fix.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/service/DashboardService.java
 M backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
 M harness/reports/current/latest-channel-performance-scope-fix.md
~~~

## Build Result

~~~text
Backend package: PASS; frontend standalone build: PASS; permission regression subset (DataControllerTest, PerformanceAggregateApplicationServiceTest, PerformanceMetricsQueryServiceTest, DashboardControllerTest): PASS; DashboardServiceTest: FAIL 3 cases because concurrent uncommitted DashboardService refactor removed buildOrderVisibility compatibility method.
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre                                    "sh -c 'java $JAVA_O…"   backend-real-pre    9 minutes ago    Up 9 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:0fb9e3c834df5ddf6614d23c1bd6782479ac42f3   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                               "docker-entrypoint.s…"   postgres-real-pre   22 minutes ago   Up 22 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                   "docker-entrypoint.s…"   redis-real-pre      22 minutes ago   Up 22 minutes (healthy)   6379/tcp
NAMES                             STATUS                       PORTS
saas-active-backend-real-pre-1    Up 9 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 22 minutes (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 22 minutes (healthy)      6379/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)         127.0.0.1:3001->80/tcp
campus_frontend                   Up 3 days                    0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)          0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)          0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)        0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up About an hour (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up About an hour (healthy)   6379/tcp
~~~

## Health Check Result

~~~text
PASS: backend /api/system/health and /api/actuator/health/readiness return UP/HTTP 200; backend, PostgreSQL, Redis, and frontend containers healthy.
~~~

## Business Validation Result

~~~text
BLOCKED_AUTH: real-pre preflight passed frontend, backend health, admin login, REAL-PRE env guard, database schema, reusable mapping, and cleanup plan; full business E2E was not run because Douyin token readiness reports hasAccessToken=false and hasRefreshToken=false.
~~~

## Content Maintenance Result

~~~text
Not run: real-pre business gate blocked by Douyin token readiness.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Actionable improvements: keep local real-pre DB and QA admin credentials synchronized with copied env files; provide a valid Douyin token before running full business E2E; coordinate the concurrent DashboardService refactor with its reflection-based tests before claiming the full permission regression green.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
