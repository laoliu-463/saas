# Evidence Report

## Metadata

- Time: 2026-07-18 13:31:32 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 528fba25
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/order/policy/DashboardOrderAccessPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDataCacheKeyPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDataScopePolicy.java
backend/src/main/java/com/colonel/saas/service/DashboardService.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
harness/scripts/commands/_lib.ps1
~~~

## Owned Git Status

~~~text
(clean)
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    43 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   41 seconds ago   Up 7 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   8 minutes ago    Up 8 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      9 minutes ago    Up 8 minutes (healthy)    6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 8 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 39 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 8 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      Up 8 minutes (healthy)       6379/tcp
campus_frontend                   Up 3 days                    0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)          0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)          0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)        0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up About an hour (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up About an hour (healthy)   6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
BLOCKED: npm run e2e:real-pre:p0:preflight
Evidence: runtime/qa/out/real-pre-preflight-20260718-133126/report.md
frontend/backend/admin login/env guard/schema readiness/reusable mapping: PASS
Douyin token readiness: BLOCKED_AUTH, hasAccessToken=false, hasRefreshToken=false, reauthorizeRequired=false,
HTTP 200, requestId=7eb0d291-ef10-4079-8352-1203e37d9f00; no business write flow was started.
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Actionable blocker: release owner must provide an authorized Douyin reauthorization/token refresh path; do not use mock data or enable test mode.

## Conclusion

PARTIAL

## Residual Risk

- Build/restart/health passed; real business flows remain BLOCKED_AUTH by missing Douyin access token.
- Remote deploy, multi-role E2E, real attribution and sample-chain validation were not executed.
