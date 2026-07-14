# Evidence Report

## Metadata

- Time: 2026-07-14 16:22:54 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 67f94935
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/test/java/com/colonel/saas/service/ProductServiceFilterTest.java
docs/05-API契约总表.md
docs/领域/商品域.md
frontend/src/api/productManage.ts
frontend/src/views/product/components/ProductEditModal.test.ts
frontend/src/views/product/components/ProductEditModal.vue
harness/rules/changelog.md
harness/rules/instructions/domain/product-domain.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/ProductController.java
 M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M backend/src/test/java/com/colonel/saas/service/ProductServiceFilterTest.java
 M docs/05-API契约总表.md
 M docs/领域/商品域.md
 M frontend/src/api/productManage.ts
 M frontend/src/views/product/components/ProductEditModal.test.ts
 M frontend/src/views/product/components/ProductEditModal.vue
 M harness/rules/changelog.md
 M harness/rules/instructions/domain/product-domain.md
 M harness/rules/state/snapshots/01-当前项目状态.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Build Result

~~~text
Backend mvn package PASS; backend full mvn test PASS (3203 tests, 0 failures, 0 errors, 3 skipped); frontend npm ci/build PASS; frontend full vitest PASS (93 files, 711 tests); typecheck PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    8 minutes ago   Up 8 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   8 minutes ago   Up 8 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   8 minutes ago   Up 8 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago     Up 33 minutes (healthy)   6379/tcp
NAMES                             STATUS                      PORTS
saas-active-frontend-real-pre-1   Up 8 minutes (healthy)      127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 8 minutes (healthy)      127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 8 minutes (healthy)      5432/tcp
campus_frontend                   Up 33 minutes               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 33 minutes (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 33 minutes (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 33 minutes (healthy)     6379/tcp
saas-test-backend-1               Up 17 minutes (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
real-pre backend/frontend/postgres/redis healthy after fixed-entry restart; local GET /api/system/health returned 200 {"status":"UP"}; frontend /healthz returned 200.
~~~

## Business Validation Result

~~~text
BLOCKED_AUTH: real-pre preflight frontend/backend/schema/mapping checks PASS, but admin login failed after 5 attempts with HTTP 401; no admin token, so real product edit API/page flow was not executed.
~~~

## Content Maintenance Result

~~~text
Not executed after business preflight BLOCKED_AUTH; no content retirement requested.
~~~

## Remote Deploy Result

~~~text
remote not deployed (not requested)
~~~

## Retro Summary

本轮新增可执行改进：恢复 real-pre 管理员账号/认证后，重跑 real-pre 商品编辑页面/API 正向验收并补清空回退证据；责任人和验证窗口待业务方提供。当前代码侧未发现新的可执行 Harness 改进。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
- `check-harness-limits.ps1 -BaselineRef HEAD` 已执行；TASK_GATE=FAIL、REPOSITORY_HEALTH=PARTIAL，原因是基线之外已有 `harness/reports/` 根目录时间戳报告 16 个、目录文件数 39 超过 20，且有 1 个历史报告超过 200 行。本轮未删除或归档这些非本任务文件，避免混入清理范围。
- real-pre 管理员账号登录连续 5 次 HTTP 401，真实商品编辑页面/API 正向业务验证待恢复授权后重跑。
