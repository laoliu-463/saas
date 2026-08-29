# Evidence Report

## Metadata

- Time: 2026-07-14 14:04:32 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/rbac-shadow-runtime-plan
- Commit: 95ff7355
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
docs/superpowers/plans/2026-07-13-ddd-rbac-program-roadmap.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-01-runtime-modes.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-02a-principal-contracts.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-02b-principal-adapter.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-03-jwt-authz-version.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-04-jwt-filter.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-05a-cache-contracts.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-05b-cache-adapters.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-06a-runtime-coordinator.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-06b-difference-logging.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-07a-version-store.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-07b-version-writers.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-08-activation-guard.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-09-local-migration-gate.md
docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime/task-10-verification.md
harness/reports/current/latest-rbac-phase2-shadow-runtime-plan.md
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    13 minutes ago   Up 13 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   13 minutes ago   Up 12 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   34 minutes ago   Up 33 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 24 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 12 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 13 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 33 minutes (healthy)   5432/tcp
campus_frontend                   Up 24 hours               5173/tcp
campus_backend                    Up 24 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 24 hours (healthy)     6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
