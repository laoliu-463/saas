# Evidence Report

## Metadata

- Time: 2026-07-16 23:06:30 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 78cfc725
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/PerformanceQueryService.java
backend/src/test/java/com/colonel/saas/service/PerformanceQueryServiceTest.java
harness/reports/current/latest-harness-limits-check.md
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-harness-limits-check.md
~~~

## Build Result

~~~text
PASS: focused query/access tests plus agent-do full backend and frontend build.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    6 minutes ago   Up 6 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   6 minutes ago   Up 5 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   6 minutes ago   Up 6 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      31 hours ago    Up 31 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 5 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 6 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 6 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 31 hours (healthy)    6379/tcp
campus_frontend                   Up 2 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: local backend/frontend healthy; remote backend UP and frontend probe ok.
~~~

## Business Validation Result

~~~text
PASS: 仅查询订单 6927995582750227729。管理员详情接口与壮云当前 biz_staff/PERSONAL 授权快照详情接口均返回 code=200；default/final recruiter userId 均为 1c34b680-30b2-41ec-bdc7-2dde1f37e786。金额：pay=990、settle=0、estimateServiceFee=20、effectiveServiceFee=0。只读数据库确认渠道 UNATTRIBUTED、招商 ATTRIBUTED、聚合 PARTIAL；原招商组长测试在目标订单上的 final recruiter 命中数=0。未触发新的订单重放或批量重放。
~~~

## Content Maintenance Result

~~~text
PASS: agent-do content maintenance plan completed; no content retirement was performed.
~~~

## Remote Deploy Result

~~~text
PASS: remote real-pre deployed d89e46a8; deployment evidence commit 78cfc725 pushed.
~~~

## Retro Summary

回溯：查询 SQL 已分别为活动表 colonel_activity 与活动名列 activity_name 添加断言。第四次发布首次本地 Docker 启动瞬时失败，按同一固定入口重试后通过；未绕过部署流程。Harness 最终门禁为 PARTIAL 的唯一原因是任务开始前已存在的未跟踪根目录时间戳报告 harness/reports/evidence-20260713-131800.md 触发 no-regression 检查；该文件不属于本次修改，已保留未删除。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
