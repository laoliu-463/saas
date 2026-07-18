# Evidence Report

## Metadata

- Time: 2026-07-18 18:59:47 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 3dddfe1a
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
frontend/src/architecture/frontend-business-rule-boundary.test.ts
frontend/src/components/product/ProductSelectionCard.test.ts
frontend/src/components/product/ProductSelectionCard.vue
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M frontend/src/architecture/frontend-business-rule-boundary.test.ts
 M frontend/src/components/product/ProductSelectionCard.test.ts
 M frontend/src/components/product/ProductSelectionCard.vue
 M frontend/src/views/product/ProductLibrary.test.ts
 M frontend/src/views/product/ProductLibrary.vue
 M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    43 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   41 seconds ago   Up 14 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 minutes ago    Up 4 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 minutes ago    Up 4 minutes (healthy)    6379/tcp
NAMES                             STATUS                            PORTS
saas-active-frontend-real-pre-1   Up 14 seconds (healthy)           127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 39 seconds (healthy)           127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 4 minutes (healthy)            5432/tcp
saas-active-redis-real-pre-1      Up 4 minutes (healthy)            6379/tcp
campus_frontend                   Up About an hour                  0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up About an hour (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up About an hour (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 6 seconds (health: starting)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up About an hour (healthy)        0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up About an hour (healthy)        6379/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

前端角色拦截已移除，后端管理员绕过与前后端回归均通过；real-pre preflight 因 Douyin token readiness BLOCKED_AUTH，真实 promotion-links 上游闭环未执行。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
