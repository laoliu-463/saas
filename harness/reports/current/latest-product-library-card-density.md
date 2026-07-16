# Evidence Report

## Metadata

- Time: 2026-07-16 14:52:41 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Commit: 04629dde
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/superpowers/plans/2026-07-16-product-library-card-density.md
docs/superpowers/specs/2026-07-16-product-library-card-density-design.md
frontend/src/components/product/ProductSelectionCard.test.ts
frontend/src/components/product/ProductSelectionCard.vue
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
frontend/src/views/product/product-library-layout.test.ts
frontend/src/views/product/product-library-layout.ts
~~~

## Owned Git Status

~~~text
M frontend/src/components/product/ProductSelectionCard.test.ts
 M frontend/src/components/product/ProductSelectionCard.vue
 M frontend/src/views/product/ProductLibrary.test.ts
 M frontend/src/views/product/ProductLibrary.vue
 M frontend/src/views/product/product-library-layout.ts
?? docs/superpowers/plans/2026-07-16-product-library-card-density.md
?? docs/superpowers/specs/2026-07-16-product-library-card-density-design.md
?? frontend/src/views/product/product-library-layout.test.ts
~~~

## Build Result

~~~text
not collected
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    55 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   52 seconds ago   Up 17 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   57 seconds ago   Up 50 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      23 hours ago     Up 23 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 17 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 50 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 23 hours (healthy)     6379/tcp
campus_frontend                   Up 47 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 47 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 47 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 47 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
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

本次未发现需要新增 Harness 规则的可执行改进；无需独立 retro。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
