# Evidence Report

## Metadata

- Time: 2026-07-18 14:52:06 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 05d4e17b
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
harness/reports/current/latest-local-cors-fix.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS; Frontend build: PASS; Local container restart: PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                                     COMMAND                  SERVICE             CREATED         STATUS                        PORTS
saas-active-frontend-real-pre-1   sha256:48f5c3129058835313b90596672359f594c0b272db7ca9bd1c10917a92b87e48   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
NAMES                  STATUS                  PORTS
campus_frontend        Up 3 days               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend         Up 3 days (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres        Up 3 days (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1    Up 3 days (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1   Up 3 hours (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1      Up 3 hours (healthy)    6379/tcp
~~~

## Health Check Result

~~~text
Local backend/frontend/Postgres/Redis health: PASS.
~~~

## Business Validation Result

~~~text
CORS validation PASS: OPTIONS 3/3 returned HTTP 200 with matching Access-Control-Allow-Origin; login POST from http://127.0.0.1:3001 returned HTTP 200 with matching CORS header.
~~~

## Content Maintenance Result

~~~text
Content maintenance: plan; retirement report collected.
~~~

## Remote Deploy Result

~~~text
Remote not deployed; this is a local-only CORS configuration fix.
~~~

## Retro Summary

Root cause was the local real-pre CORS placeholder https://real-pre.YOUR_DOMAIN while the local frontend runs on localhost/127.0.0.1 ports. Updated only local .env.real-pre to allow localhost and 127.0.0.1 port patterns. No password, role, token, or remote production configuration was changed.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
