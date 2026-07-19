# Evidence Report

## Metadata

- Time: 2026-07-19 16:08:22 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 4e11bb6d
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/SampleLogisticsSyncService.java
backend/src/test/java/com/colonel/saas/service/SampleLogisticsSyncServiceTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/service/SampleLogisticsSyncService.java
 M backend/src/test/java/com/colonel/saas/service/SampleLogisticsSyncServiceTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    54 seconds ago   Up 49 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   31 minutes ago   Up 30 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   21 hours ago     Up 21 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      19 hours ago     Up 19 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 50 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 30 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 19 hours (healthy)     6379/tcp
saas-test-frontend-1              Up 21 hours (healthy)     0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 21 hours (healthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 21 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 21 hours (healthy)     5432/tcp
campus_frontend                   Up 22 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 22 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 22 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 22 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -q -f backend/pom.xml '-Djacoco.skip=true' '-Dtest=SampleLogisticsSyncServiceTest,Kuaidi100LogisticsQueryGatewayTest,LogisticsGatewayRouterTest' test)
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

成功路径把字段设为 null 不代表数据库已清空；测试必须覆盖 ORM 的 null 更新策略。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
