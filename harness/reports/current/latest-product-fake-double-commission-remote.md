# Evidence Report

## Metadata

- Time: 2026-07-14 14:46:06 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 9957b19b
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
backend/src/test/java/com/colonel/saas/service/ProductServiceFilterTest.java
docs/领域/商品域.md
frontend/src/views/product/components/ProductCard.test.ts
frontend/src/views/product/components/ProductCard.vue
frontend/src/views/product/ProductDetail.vue
harness/rules/instructions/domain/product-domain.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Local/remote build PASS: backend package; frontend build; remote Maven clean package; remote JAR guard PASS
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago       Up 4 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   4 minutes ago       Up 4 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 25 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 4 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 4 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
campus_frontend                   Up 25 hours                  5173/tcp
campus_backend                    Up 25 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 25 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 25 hours (healthy)        6379/tcp
saas-test-backend-1               Up 24 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: remote backend health HTTP 200 UP; remote frontend healthz HTTP 200 ok; remote backend/frontend/postgres/redis containers healthy
~~~

## Business Validation Result

~~~text
FAIL/BLOCKED: remote deployment used -SkipBusinessValidation because real-pre preflight admin login previously failed HTTP 401 after 5 attempts; admin token unavailable; Douyin token readiness BLOCKED_AUTH; real business flow not executed. Must rerun npm run e2e:real-pre:p0:preflight after valid credentials are restored.
~~~

## Content Maintenance Result

~~~text
SKIPPED: -ContentMaintenance off. Prior harness limits check remains TASK_GATE=FAIL / REPOSITORY_HEALTH=PARTIAL due pre-existing harness/reports debt.
~~~

## Remote Deploy Result

~~~text
PASS: deploy-remote.ps1 completed remote git pull, schema guards, remote Maven clean package, JAR size guard, compose rebuild, and remote health checks.
~~~

## Retro Summary

远端部署已通过；真实业务预检仍因管理员登录 HTTP 401 未执行。下一步：恢复有效 real-pre 管理员凭证并复跑 preflight；清理/归档历史 harness/reports 根目录时间戳报告后重跑 harness limits。工具限制：当前环境未提供 rtk 命令，已直接执行仓库脚本。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
