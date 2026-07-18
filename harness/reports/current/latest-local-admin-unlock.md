# Evidence Report

## Metadata

- Time: 2026-07-18 15:21:38 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 722de6ac
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
harness/reports/current/latest-local-admin-unlock.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Not applicable: runtime Redis login-lock cleanup only; no code changed.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    8 minutes ago   Up 7 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   8 minutes ago   Up 7 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   8 minutes ago   Up 8 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      8 minutes ago   Up 8 minutes (healthy)   6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 7 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 7 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 8 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 8 minutes (healthy)   6379/tcp
campus_frontend                   Up 4 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 4 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 4 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 3 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 3 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
PASS: real-pre backend/frontend/PostgreSQL/Redis containers healthy; backend health HTTP 200; frontend health HTTP 200.
~~~

## Business Validation Result

~~~text
PASS for targeted verification: Redis auth:login:lock:admin deleted (DEL=1; EXISTS=0); admin login with supplied local credential returned HTTP 200. Full real-pre business preflight was not rerun.
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

The lock key is explicitly namespaced as auth:login:lock:{normalizedUsername}; targeted deletion and login verification provide a repeatable recovery path. No broader Redis key scan deletion was performed.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
