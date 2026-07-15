# Evidence Report

## Metadata

- Time: 2026-07-15 18:25:15 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 2df82ba2
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/product/application/ProductQuickSampleApplicationService.java
backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductApplicationRoutingTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/product/application/ProductQuickSampleApplicationService.java
 M backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductApplicationRoutingTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   18 minutes ago       Up 18 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago    Up About an hour (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago          Up 3 hours (healthy)          6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 18 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)    5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)          6379/tcp
campus_frontend                   Up 27 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 26 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -f backend/pom.xml -Dtest=DddProduct003ProductApplicationRoutingTest test)
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

寄样入口的标识语义必须通过真实数据形态回归测试固化：页面 relationId 是 product_snapshot.id，不能用 product.id 做前置存在性判断。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
