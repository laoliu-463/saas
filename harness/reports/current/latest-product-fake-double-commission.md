# Evidence Report

## Metadata

- Time: 2026-07-14 14:31:44 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: e2269af8
- Owned worktree: clean
- Deploy remote: false

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
Backend build PASS; Frontend build PASS; Backend tests 3199 run / 0 failures / 0 errors / 3 skipped; Frontend tests 706 run / 0 failures; Frontend typecheck PASS
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago       Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago       Up 2 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 25 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 2 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
campus_frontend                   Up 25 hours                  5173/tcp
campus_backend                    Up 25 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 25 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 25 hours (healthy)        6379/tcp
saas-test-backend-1               Up 24 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: backend 200 UP; frontend /healthz 200; backend/frontend/postgres/redis real-pre containers healthy after restart
~~~

## Business Validation Result

~~~text
FAIL/BLOCKED: real-pre preflight admin login HTTP 401 after 5 attempts; admin token unavailable; Douyin token readiness BLOCKED_AUTH; business flow not executed. Evidence: runtime/qa/out/real-pre-preflight-20260714-142925/report.md
~~~

## Content Maintenance Result

~~~text
FAIL/TASK_GATE: check-harness-limits.ps1 -BaselineRef HEAD blocked by pre-existing harness/reports debt (39 root files, timestamp reports, one 258-line report); REPOSITORY_HEALTH=PARTIAL
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

可执行改进：补充有效 real-pre 管理员凭证并复跑 preflight；清理/归档历史 harness/reports 根目录时间戳报告后重跑 harness limits。当前代码回归测试与构建已通过，业务闭环仍未获得真实证据。

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.
