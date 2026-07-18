# Evidence Report

## Metadata

- Time: 2026-07-16 17:59:59 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/ddd-user-role-application
- Commit: f4aa8dba
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
output/product-library-all-activity-products-20260716.csv
~~~

## Owned Git Status

~~~text
?? output/product-library-all-activity-products-20260716.csv
~~~

## Build Result

~~~text
SKIP: 只读数据库导出，无代码变更
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      26 hours ago         Up 26 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 26 hours (healthy)         6379/tcp
campus_frontend                   Up 2 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: curl.exe --noproxy * http://127.0.0.1:8081/api/system/health -> 200 {status:UP}; http://127.0.0.1:3001/healthz -> 200 ok；容器健康状态均 healthy
~~~

## Business Validation Result

~~~text
PASS: DB COPY=96471；CSV=96471行/51列；关系ID全唯一；商品ID和活动ID无空值；状态与商品库可见性已复核
~~~

## Content Maintenance Result

~~~text
PASS: UTF-8 BOM CSV 已生成并可被结构化 CSV 解析器完整读取
~~~

## Remote Deploy Result

~~~text
SKIP: 未部署远端
~~~

## Retro Summary

本次无可执行 Harness 改进；查询保持只读，并同时保留上游状态码/文案与本地商品库入库、可见状态，便于后续与任务货盘清单做集合比对。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
