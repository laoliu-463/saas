# Evidence Report

## Metadata

- Time: 2026-07-14 21:08:28 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/rbac-shadow-runtime-plan
- Commit: 21a80ae8
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/07-权限与数据范围.md
docs/领域/用户域.md
harness/reports/current/latest-harness-limits-check.md
harness/reports/current/latest-rbac-phase2-shadow-runtime.md
~~~

## Owned Git Status

~~~text
M docs/07-权限与数据范围.md
 M docs/领域/用户域.md
 M harness/reports/current/latest-harness-limits-check.md
?? harness/reports/current/latest-rbac-phase2-shadow-runtime.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    26 seconds ago   Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   45 minutes ago   Up 44 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 minutes ago    Up 4 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 5 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 4 minutes (healthy)    5432/tcp
saas-active-frontend-real-pre-1   Up 44 minutes (healthy)   127.0.0.1:3001->80/tcp
campus_frontend                   Up 5 hours                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 5 hours (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 5 hours (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 5 hours (healthy)      6379/tcp
saas-test-backend-1               Up 5 hours (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

Full backend: 3419 tests, 0 failures, 0 errors, 3 skipped; package PASS. Local migration ran twice and catalog=4|1|0|0|0|0|0. LEGACY health and unauthenticated 401 PASS; Redis authz snapshot keys=0. Actual P0 preflight ran and admin login returned HTTP 401 after 5 attempts, so token/business validation is BLOCKED_AUTH, not PASS. code-review-graph transport closed after two attempts. SHADOW and remote deployment were not authorized.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
