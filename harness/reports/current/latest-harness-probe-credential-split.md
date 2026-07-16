# Evidence Report

## Metadata

- Time: 2026-07-16 23:19:14 +08:00
- Environment: real-pre
- Scope: docs
- Branch: feature/auth-system
- Commit: 338763e0
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
harness/reports/current/latest-content-retire.md
harness/scripts/probes/_admin-credential.ps1
harness/scripts/probes/backfill-async.ps1
harness/scripts/probes/product-library-phase2-dryrun.ps1
harness/scripts/tests/probe-admin-credential.Tests.ps1
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-content-retire.md
 M harness/scripts/probes/backfill-async.ps1
 M harness/scripts/probes/product-library-phase2-dryrun.ps1
?? harness/scripts/probes/_admin-credential.ps1
?? harness/scripts/tests/probe-admin-credential.Tests.ps1
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    19 minutes ago   Up 18 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   19 minutes ago   Up 18 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   19 minutes ago   Up 19 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      32 hours ago     Up 32 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 18 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 18 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 19 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 32 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
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
