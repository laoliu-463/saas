# Evidence Report

## Metadata

- Time: 2026-07-18 14:27:34 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 235e2354
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
harness/reports/current/latest-order-policy-remote-deploy.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS; Frontend build: PASS; Targeted permission/performance tests: PASS (68/68); Local container restart: PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago    Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   15 minutes ago   Up 14 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      15 minutes ago   Up 14 minutes (healthy)       6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 14 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      Up 14 minutes (healthy)       6379/tcp
campus_frontend                   Up 3 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 2 hours (healthy)          0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 2 hours (healthy)          6379/tcp
~~~

## Health Check Result

~~~text
Local backend/frontend/Postgres/Redis health: PASS. Remote backend /api/system/health: PASS. Remote frontend /healthz: PASS. Remote compose services healthy.
~~~

## Business Validation Result

~~~text
Targeted unit tests PASS (DataControllerTest 50/50, PerformanceAggregateApplicationServiceTest 10/10, PerformanceMetricsQueryServiceTest 8/8). Authenticated business validation remains skipped by -SkipBusinessValidation; channel/recruiter/manager/admin account boundary regression is still pending.
~~~

## Content Maintenance Result

~~~text
Content maintenance: plan; retirement report collected.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS for runtime fix commit d99f7c0976b39f92033d97223b9ab29e2ab7fcd6. Remote fast-forward, schema guards, immutable images, restart, and health checks passed. Later commit 235e2354 contains tests/report only and was not rolled out because it has no runtime code change. Warning: ProductActivitySyncJob config log not found.
~~~

## Retro Summary

Root cause evidence: remote credentials and Redis token cache were present; the local preflight used a different appId/clientKey pair and therefore could not resolve the local token cache. No remote key was missing. Production permission fix is deployed at d99f7c0. Test assertions were aligned to final attribution fields and 68 targeted tests now pass. Harness check reports TASK_GATE=FAIL only because an unrelated pre-existing untracked timestamp report exists under harness/reports; it was preserved and not staged. Remaining actionable work is authenticated role-boundary regression and investigation of the missing ProductActivitySyncJob config log.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
