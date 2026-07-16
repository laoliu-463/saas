# Evidence Report

## Metadata

- Time: 2026-07-16 18:19:34 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/deploy-product-library-density-v2
- Commit: e9e39f1d
- Owned worktree: clean
- Deploy remote: true

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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    3 minutes ago   Up 3 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   3 minutes ago   Up 3 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 minutes ago   Up 3 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      27 hours ago    Up 27 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 3 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 3 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 3 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 27 hours (healthy)    6379/tcp
campus_frontend                   Up 2 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Frontend Regression Result

~~~text
Latest feature/auth-system baseline: PASS (95 files, 729 tests)
Post-cherry-pick full suite: PASS (96 files, 731 tests)
TypeScript typecheck: PASS
Code review graph: risk 0.30, affected flows 0, test gaps 0
~~~

## Remote Artifact Verification

~~~text
Remote app revision: e9e39f1d859aa00a430e747a923be8ea9c760891
Backend health: PASS ({"status":"UP"})
Frontend health: PASS (ok)
ProductLibrary bundle contains: product-copy-id, product-id-value
ProductLibrary bundle excludes: product-copy-url, product-refresh, product-detail-btn
Remote frontend artifact: PASS
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
~~~

## Retro Summary

部署前发现远端基线持续前进，最终从最新 gitee/feature-auth-system 基线建立隔离 v2 分支，仅移植商品库改动；全量回归通过后发布。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
