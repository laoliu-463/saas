# Evidence Report

## Metadata

- Time: 2026-07-18 18:02:13 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/product-library-card-ui-latest
- Commit: 2a0e51c5
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
frontend/src/components/product/ProductSelectionCard.test.ts
frontend/src/components/product/ProductSelectionCard.vue
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
frontend/src/views/product/product-library-layout.test.ts
frontend/src/views/product/product-library-layout.ts
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up 41 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   3 minutes ago        Up 3 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago          Up 23 minutes (healthy)       6379/tcp
NAMES                             STATUS                            PORTS
saas-active-frontend-real-pre-1   Up 41 seconds (healthy)           127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 3 minutes (healthy)            5432/tcp
saas-active-redis-real-pre-1      Up 23 minutes (healthy)           6379/tcp
campus_frontend                   Up 23 minutes                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 23 minutes (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 23 minutes (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 7 seconds (health: starting)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 23 minutes (healthy)           0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 23 minutes (healthy)           6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm --prefix frontend run test -- src/components/product/ProductSelectionCard.test.ts src/views/product/ProductLibrary.test.ts src/views/product/product-library-layout.test.ts)
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

Remote branch drift caused the earlier UI rollback; future UI deployments must rebase onto and verify the latest feature/auth-system head before deploy. The default real-pre preflight remains BLOCKED_AUTH because local Douyin access and refresh tokens are absent.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
