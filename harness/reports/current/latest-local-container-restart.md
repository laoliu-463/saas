# Evidence Report

## Metadata

- Time: 2026-07-18 15:15:50 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 793f82ac
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docker-compose.real-pre.yml
harness/reports/current/latest-local-container-restart.md
~~~

## Owned Git Status

~~~text
?? harness/reports/current/latest-local-container-restart.md
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package); Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago   Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 minutes ago   Up 2 minutes (healthy)        6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      Up 2 minutes (healthy)        6379/tcp
campus_frontend                   Up 3 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 3 hours (healthy)          0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 3 hours (healthy)          6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS; backend 200 {"status":"UP"}; frontend 200; Docker backend/frontend/postgres/redis all healthy; containers sourced from current worktree compose.
~~~

## Business Validation Result

~~~text
FAIL/PARTIAL: real-pre preflight frontend and backend health PASS; admin login failed after 5 attempts with HTTP 401; real-pre env guard FAIL because admin token unavailable; Douyin token readiness BLOCKED_AUTH; database schema, reusable promotion mapping, and cleanup plan PASS.
~~~

## Content Maintenance Result

~~~text
No content maintenance requested.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Root cause of restart failure was a stale root-directory backend container occupying 127.0.0.1:8081. Removed only that duplicate container; recreated all real-pre services from the current worktree. Remaining improvement: local admin credential/token fixture must be aligned before business preflight can pass.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
