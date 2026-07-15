# Evidence Report

## Metadata

- Time: 2026-07-15 18:29:51 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 76e507d0
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
docs/领域/商品域.md
docs/验收/验收证据索引.md
frontend/src/utils/clipboard.test.ts
frontend/src/views/product/product-copy.test.ts
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M docs/领域/商品域.md
 M docs/验收/验收证据索引.md
 M frontend/src/utils/clipboard.test.ts
 M frontend/src/views/product/product-copy.test.ts
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    28 seconds ago      Up 24 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   26 seconds ago      Up 7 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago         Up 3 hours (healthy)         6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 7 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 24 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)         6379/tcp
campus_frontend                   Up 27 hours                  0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 26 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

微信是本阶段必过客户端，飞书图片链接加完整文字是已接受降级；后续若恢复飞书图片渲染目标，应新增独立客户端验收，不修改统一剪贴板合同。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
