# Evidence Report

## Metadata

- Time: 2026-07-14 14:48:36 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 35be7a01
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
PASS: local backend tests/build; frontend typecheck/tests/build; remote Maven clean package and JAR size guard passed, but remote build used current remote feature/auth-system checkout rather than codex/ddd-user-role-application
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    7 minutes ago       Up 7 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   7 minutes ago       Up 6 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 25 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 6 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 7 minutes (healthy)       127.0.0.1:8081->8080/tcp
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
BLOCKED: real-pre admin login previously failed HTTP 401 after 5 attempts; admin token unavailable; Douyin token readiness BLOCKED_AUTH; real business flow not executed
~~~

## Content Maintenance Result

~~~text
SKIPPED: -ContentMaintenance off. Prior harness limits check remains TASK_GATE=FAIL / REPOSITORY_HEALTH=PARTIAL due pre-existing harness/reports debt.
~~~

## Remote Deploy Result

~~~text
BLOCKED/PARTIAL: deploy-remote.ps1 completed git pull/build/restart/health on remote feature/auth-system@9797bda8. Requested feature branch codex/ddd-user-role-application@35be7a01 was pushed to origin but was not checked out remotely; remote ProductService/ProductCard/ProductDetail do not contain the new fake-double-commission no-ads rule. Do not claim feature rollout.
~~~

## Retro Summary

功能分支已推送到 origin，但固定远端部署脚本按远端当前 feature/auth-system 分支执行，导致本次功能未被证明上线；且真实业务预检因管理员登录 HTTP 401 未执行。下一步需由负责人确认是将本次提交以合并/cherry-pick 方式集成到远端 feature/auth-system，还是调整部署脚本支持显式目标分支；确认后再部署并复跑业务 preflight。工具限制：当前环境未提供 rtk 命令，已直接执行仓库脚本。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
