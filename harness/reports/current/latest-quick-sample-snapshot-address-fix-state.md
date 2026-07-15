# Evidence Report

## Metadata

- Time: 2026-07-15 18:26:15 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/ddd-user-role-application
- Commit: ba66fe51
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
harness/rules/state/snapshots/KNOWN_ISSUES.md
~~~

## Owned Git Status

~~~text
M harness/rules/state/snapshots/KNOWN_ISSUES.md
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago       Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   19 minutes ago      Up 19 minutes (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago         Up 3 hours (healthy)         6379/tcp
NAMES                             STATUS                       PORTS
saas-active-backend-real-pre-1    Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 19 minutes (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)         6379/tcp
campus_frontend                   Up 27 hours                  0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 26 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

状态台账已区分本地修复、远端部署和真实第三方业务验证，未将未执行项写成 PASS。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
