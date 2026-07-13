# Evidence Report

## Metadata

- Time: 2026-07-13 23:49:15 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/harness-file-governance
- Commit: 05617d55
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/方案/PLAN-005-Harness分层文件门禁实施.md
~~~

## Owned Git Status

~~~text
M docs/方案/PLAN-005-Harness分层文件门禁实施.md
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    34 minutes ago      Up 34 minutes (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About an hour ago   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 days ago          Up 10 hours (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 10 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-backend-real-pre-1    Up 34 minutes (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 10 hours (healthy)        5432/tcp
campus_frontend                   Up 10 hours                  5173/tcp
campus_backend                    Up 10 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 10 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 10 hours (healthy)        6379/tcp
saas-test-backend-1               Up 9 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

Fresh verification: safety PASS; Pester 25/25; PowerShell parser 0 errors; changed-document links 0; TASK_GATE=PASS; REPOSITORY_HEALTH=PASS; reports root 12; archive groups 15/30/30. code-review-graph parsed no PowerShell functions or flows, so Pester, Parser, and reference scans are authoritative. No remaining actionable Harness improvement; no standalone retro.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
