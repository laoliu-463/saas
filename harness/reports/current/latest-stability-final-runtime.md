# Evidence Report

## Metadata

- Time: 2026-07-19 17:18:22 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/ddd-user-role-application
- Commit: db930364
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
harness/reports/current/latest-product-display-concurrency.md
harness/reports/current/latest-stability-closeout.md
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-product-display-concurrency.md
 M harness/reports/current/latest-stability-closeout.md
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    28 minutes ago   Up 28 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   22 hours ago     Up 22 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      20 hours ago     Up 20 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 28 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)     6379/tcp
saas-test-frontend-1              Up 22 hours (healthy)     0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 22 hours (healthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 22 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 22 hours (healthy)     5432/tcp
campus_frontend                   Up 24 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 24 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 24 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
Scope=docs: compose restart and HTTP health checks skipped by scoped local harness path.
~~~

## Business Validation Result

~~~text
Scope=docs: business validation not applicable; safety check executed.
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
