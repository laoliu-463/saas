# Evidence Report

## Metadata

- Time: 2026-07-19 12:16:19 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/ddd-user-role-application
- Commit: 1c9b14d0
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
harness/scripts/commands/deploy-remote.ps1
~~~

## Owned Git Status

~~~text
M harness/scripts/commands/deploy-remote.ps1
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    12 minutes ago   Up 12 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   12 minutes ago   Up 11 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   17 hours ago     Up 17 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      15 hours ago     Up 15 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 11 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 12 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 15 hours (healthy)     6379/tcp
saas-test-frontend-1              Up 17 hours (healthy)     0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 17 hours (healthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 17 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 17 hours (healthy)     5432/tcp
campus_frontend                   Up 19 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 19 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 19 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 19 hours (healthy)     6379/tcp
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
