# Evidence Report

## Metadata

- Time: 2026-07-18 14:17:43 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: d99f7c09
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/DashboardController.java
backend/src/main/java/com/colonel/saas/controller/DataController.java
backend/src/main/java/com/colonel/saas/domain/order/facade/LegacyOrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/order/facade/OrderReadFacade.java
backend/src/main/java/com/colonel/saas/domain/order/policy/DashboardOrderAccessPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDataCacheKeyPolicy.java
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDataScopePolicy.java
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/main/java/com/colonel/saas/service/DashboardService.java
backend/src/main/java/com/colonel/saas/service/data/DataApplicationService.java
backend/src/main/java/com/colonel/saas/service/PerformanceMetricsQueryService.java
harness/reports/current/latest-content-retire.md
harness/reports/current/latest-order-policy-remote-deploy.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend local build: PASS; Frontend local build: PASS; Remote Maven clean package: PASS; Remote immutable images: PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    5 minutes ago   Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   5 minutes ago   Up 4 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   5 minutes ago   Up 5 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      5 minutes ago   Up 5 minutes (healthy)   6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 4 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 5 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 5 minutes (healthy)   6379/tcp
campus_frontend                   Up 3 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 2 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 2 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
Local backend/frontend/Postgres/Redis health: PASS. Remote backend /api/system/health: PASS. Remote frontend /healthz: PASS. Remote compose services healthy.
~~~

## Business Validation Result

~~~text
Skipped by -SkipBusinessValidation; not a full PASS. Permission business regression still requires authenticated channel/recruiter/manager/admin account verification.
~~~

## Content Maintenance Result

~~~text
Content maintenance: plan; retirement report collected.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS. Remote fast-forwarded to expected commit and deployed immutable backend/frontend images. Schema guards passed. Warning: ProductActivitySyncJob config log not found.
~~~

## Retro Summary

Root cause evidence: remote credentials and Redis token cache were present; the local preflight used a different appId/clientKey pair and therefore could not resolve the local token cache. No remote key was missing. Deployment used the fixed two-stage SHA flow: local validation/evidence push first, then remote IMAGE_TAG alignment and deploy. Remaining actionable work is authenticated role-boundary regression and investigation of the missing ProductActivitySyncJob config log.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
