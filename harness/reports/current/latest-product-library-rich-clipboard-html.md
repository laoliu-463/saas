# Evidence Report

## Metadata

- Time: 2026-07-15 19:01:05 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/deploy-wechat-clipboard-20260715
- Commit: 4a116585
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
docs/领域/商品域.md
docs/验收/验收证据索引.md
frontend/src/architecture/frontend-business-rule-boundary.test.ts
frontend/src/utils/clipboard.test.ts
frontend/src/utils/clipboard.ts
frontend/src/views/product/product-copy.test.ts
frontend/src/views/product/product-copy.ts
frontend/src/views/product/ProductLibrary.vue
harness/rules/state/snapshots/DOMAIN_STATUS.md
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
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago          Up 3 hours (healthy)          6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)          6379/tcp
campus_frontend                   Up 27 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 27 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm --prefix frontend run test -- src/utils/clipboard.test.ts src/views/product/product-copy.test.ts)
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

远端服务器跟踪 Gitee feature/auth-system；远端部署必须同时校验服务器应用 revision。默认 P0 preflight 因 admin HTTP 401 为 BLOCKED_AUTH，本任务改用 19 项商品复制合同测试作为相关业务验证，不把认证链路标记为 PASS。微信为必过客户端，飞书图片链接加完整文字为已接受降级。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
