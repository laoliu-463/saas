# Evidence Report

## Metadata

- Time: 2026-07-16 15:23:21 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/role-aware-link-attribution
- Commit: f80e9939
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-01-schema-link.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-02-order-resolution.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-03-performance-replay.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-04-reconcile-frontend-docs.md
docs/superpowers/plans/2026-07-16-role-aware-promotion-link-attribution-05-verification-rollout.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    31 minutes ago   Up 31 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   31 minutes ago   Up 30 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   31 minutes ago   Up 31 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      24 hours ago     Up 24 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 30 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 31 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 31 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 24 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 47 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
