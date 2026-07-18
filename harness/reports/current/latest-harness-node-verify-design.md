# Evidence Report

## Metadata

- Time: 2026-07-18 09:26:21 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/harness-node-verify-phase1
- Commit: 4faee3f8
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/README.md
docs/方案/PLAN-006-Harness跨平台验证核心重构设计.md
docs/决策/ADR-014-Harness跨平台核心目录与渐进迁移.md
~~~

## Owned Git Status

~~~text
M docs/README.md
?? docs/决策/ADR-014-Harness跨平台核心目录与渐进迁移.md
?? docs/方案/PLAN-006-Harness跨平台验证核心重构设计.md
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                                           IMAGE                                                                     COMMAND                  SERVICE             CREATED        STATUS                  PORTS
a19fc2195055_saas-active-postgres-real-pre-1   postgres:15-alpine                                                        "docker-entrypoint.s…"   postgres-real-pre   9 hours ago    Up 9 hours (healthy)    5432/tcp
saas-active-backend-real-pre-1                 colonel-saas/backend:real-pre                                             "sh -c 'java $JAVA_O…"   backend-real-pre    9 hours ago    Up 9 hours (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1                sha256:a0c02fe0b2486870f06d22454e31740a67427a88a20d2cb28471a95946a3aca4   "/docker-entrypoint.…"   frontend-real-pre   10 hours ago   Up 10 hours (healthy)   127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   redis:7-alpine                                                            "docker-entrypoint.s…"   redis-real-pre      2 days ago     Up 2 days (healthy)     6379/tcp
NAMES                                          STATUS                  PORTS
saas-active-backend-real-pre-1                 Up 9 hours (healthy)    127.0.0.1:8081->8080/tcp
a19fc2195055_saas-active-postgres-real-pre-1   Up 9 hours (healthy)    5432/tcp
saas-active-frontend-real-pre-1                Up 10 hours (healthy)   127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   Up 2 days (healthy)     6379/tcp
campus_frontend                                Up 3 days               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                 Up 3 days (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                Up 3 days (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                            Up 3 days (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
