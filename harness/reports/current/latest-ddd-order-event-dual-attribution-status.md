# Evidence Report

## Metadata

- Time: 2026-07-17 18:44:32 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 3301bfb4
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java
backend/src/test/java/com/colonel/saas/architecture/DddAnalyticsReadOnlyBoundaryTest.java
backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddTalentPermissionOverreachNegativeEvidenceTest.java
CONTEXT.md
docs/04-事件契约总表.md
docs/领域/订单域.md
~~~

## Owned Git Status

~~~text
M CONTEXT.md
 M backend/src/main/java/com/colonel/saas/domain/order/policy/OrderDefaultAttributionPolicy.java
 M backend/src/test/java/com/colonel/saas/architecture/DddAnalyticsReadOnlyBoundaryTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceAttributionTraceabilityContractTest.java
 M backend/src/test/java/com/colonel/saas/architecture/DddTalentPermissionOverreachNegativeEvidenceTest.java
 M docs/04-事件契约总表.md
 M docs/领域/订单域.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    40 seconds ago   Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   57 minutes ago   Up 57 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   5 hours ago      Up 5 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 57 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 5 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 2 days (healthy)       6379/tcp
campus_frontend                   Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation skipped by -SkipBusinessValidation; not a full PASS.
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

双维状态使部分归因结果的聚合状态为 UNATTRIBUTED，legacy 工厂会清空已存在的渠道事实；改为逐字段兼容映射，并同步修正三项随演进失真的架构守卫。full 首次因主工作树 esbuild.exe 被占用停止，backend 第二次由大文件 no-regression 门禁阻断，已撤回对历史超限矩阵的新增内容；业务 P0 独立执行。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
