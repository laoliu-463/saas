# Evidence Report

## Metadata

- Time: 2026-07-18 17:44:20 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-product-copy-link-admin
- Commit: ac7b9f46
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
harness/rules/changelog.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M frontend/src/views/product/ProductLibrary.test.ts
 M frontend/src/views/product/ProductLibrary.vue
 M harness/rules/changelog.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                            PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    36 seconds ago   Up 21 seconds (healthy)           127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   34 seconds ago   Up 5 seconds (health: starting)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   36 seconds ago   Up 32 seconds (healthy)           5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 hours ago      Up 5 minutes (healthy)            6379/tcp
NAMES                             STATUS                             PORTS
saas-active-frontend-real-pre-1   Up 5 seconds (health: starting)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 21 seconds (healthy)            127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 33 seconds (healthy)            5432/tcp
saas-active-redis-real-pre-1      Up 5 minutes (healthy)             6379/tcp
campus_frontend                   Up 5 minutes                       0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 5 minutes (healthy)             0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 5 minutes (healthy)             0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 39 seconds (health: starting)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 5 minutes (healthy)             0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 5 minutes (healthy)             6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation skipped by -SkipBusinessValidation; not a full PASS.
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
