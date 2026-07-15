# Evidence Report

## Metadata

- Time: 2026-07-15 15:54:15 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/fix-channel-product-access-20260715
- Commit: 1bd75f08
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
frontend/src/views/profile/UserProfile.test.ts
frontend/src/views/profile/UserProfile.vue
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS: npm --prefix frontend run build; backend Maven package PASS for local Compose dependency.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   9 minutes ago        Up 9 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      12 minutes ago       Up 12 minutes (healthy)       6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 9 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      Up 12 minutes (healthy)       6379/tcp
campus_frontend                   Up 24 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 24 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 24 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: local backend /api/system/health=UP; local frontend /healthz=ok; remote backend /api/system/health=UP; remote frontend /healthz=ok; remote Compose 4/4 healthy.
~~~

## Business Validation Result

~~~text
PARTIAL: focused profile/auth/router regression 5 files, 49/49 tests PASS; UserProfile activation flow 3/3 PASS; remote account remains active channel_staff and last_login_at is still 2026-07-15 07:21:41 UTC. Actual relogin and product-library page access are PENDING because the user has not logged in again after password reset and no user credential was requested or used.
~~~

## Content Maintenance Result

~~~text
Not applicable.
~~~

## Remote Deploy Result

~~~text
PASS: deployed exact source commit 1bd75f081695070ec681b36f295d4baeabe9cc72; remote worktree clean; canonical env link and container provenance verified; frontend artifact contains forced-relogin hotfix; true ERROR/FATAL logs 0.
~~~

## Retro Summary

Root cause: activation password change updated database state but retained the old pendingActivation JWT in the browser, so business APIs remained blocked. Governance action implemented: clear local authentication and force navigation to /login after successful activation password change. Remaining validation requires the affected user to log in with the new password and open the product library.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
