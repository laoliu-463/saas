# Evidence Report

## Metadata

- Time: 2026-07-19 15:10:10 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 22157098
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
harness/reports/current/latest-stability-closeout.md
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago   Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   4 minutes ago   Up 4 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   20 hours ago    Up 20 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      18 hours ago    Up 18 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-frontend-real-pre-1   Up 4 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1      Up 18 hours (healthy)    6379/tcp
saas-test-frontend-1              Up 20 hours (healthy)    0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 20 hours (healthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 20 hours (healthy)    0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1   Up 20 hours (healthy)    5432/tcp
campus_frontend                   Up 22 hours              0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 22 hours (healthy)    0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 22 hours (healthy)    0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                 Up 22 hours (healthy)    6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (& mvn -q -f backend/pom.xml '-Dtest=AuthServiceTest,Kuaidi100LogisticsQueryGatewayTest,LogisticsGatewayRouterTest,SampleLogisticsSyncServiceTest,Kuaidi100CallbackApplicationServiceTest,DomainEventOutboxServiceTest,DomainEventDispatcherJobTest,DddOutbox001OrderRoutingTest,OrderSyncPersistenceServiceTest,OrderSyncServiceTest,OperationLogServiceTest,OperationLogInterceptorTest,OperationLogResponseAdviceTest,MerchantServiceTest,PickSourceMappingServiceTest,OperationLogRetentionAcceptanceTest,LogCleanupJobTest,CurrentUserPasswordAuditIntegrationTest' test)
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

直接写 operation_log 的业务入口也必须设置结构化错误码；线上失败样本是验证字段完整性的必要证据。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
