# Evidence Report

## Metadata

- Time: 2026-07-19 14:28:12 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/ddd-user-role-application
- Commit: 96b98eed
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/04-事件契约总表.md
docs/06-数据模型总表.md
docs/09-测试验收总览.md
docs/10-部署运行总览.md
docs/对接/物流接口.md
docs/领域/寄样域.md
~~~

## Owned Git Status

~~~text
M docs/04-事件契约总表.md
 M docs/06-数据模型总表.md
 M docs/09-测试验收总览.md
 M docs/10-部署运行总览.md
 M docs/对接/物流接口.md
 M docs/领域/寄样域.md
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    3 minutes ago   Up 3 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   3 minutes ago   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   20 hours ago    Up 20 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      18 hours ago    Up 18 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 3 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 18 hours (healthy)    6379/tcp
saas-test-frontend-1              Up 19 hours (healthy)    0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 19 hours (healthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 19 hours (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 20 hours (healthy)    5432/tcp
campus_frontend                   Up 21 hours              0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 21 hours (healthy)    0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 21 hours (healthy)    0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 21 hours (healthy)    6379/tcp
~~~

## Health Check Result

~~~text
Scope=docs: compose restart and HTTP health checks skipped by scoped local harness path.
~~~

## Business Validation Result

~~~text
Scope=docs: business validation not applicable; safety check executed.
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

中文路径必须以未转义仓库相对路径传给 OwnedFiles，避免 evidence 已采集但文档未进入提交。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
