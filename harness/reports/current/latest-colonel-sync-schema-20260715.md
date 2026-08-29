# Evidence Report

## Metadata

- Time: 2026-07-15 14:06:33 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 31de7fbe
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/AdminColonelPartnerController.java
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/config/ProductStateMigrationContractTest.java
backend/src/test/java/com/colonel/saas/controller/AdminColonelPartnerControllerTest.java
harness/scripts/commands/deploy-remote.ps1
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/AdminColonelPartnerController.java
 M backend/src/main/resources/db/migrate-all.sql
 M backend/src/test/java/com/colonel/saas/config/ProductStateMigrationContractTest.java
 M harness/scripts/commands/deploy-remote.ps1
?? backend/src/test/java/com/colonel/saas/controller/AdminColonelPartnerControllerTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    35 seconds ago       Up 32 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   57 minutes ago       Up 57 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago          Up 22 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up 32 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 57 minutes (healthy)       127.0.0.1:3001->80/tcp
campus_frontend                   Up 22 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 22 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 22 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 22 hours (healthy)         6379/tcp
saas-test-backend-1               Up 22 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -f backend/pom.xml -Dtest=ProductStateMigrationContractTest test)
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

已将团长同步所需字段纳入统一迁移与远端 schema guard，并补充 context-path 接口回归测试；后续远端部署固定执行该幂等迁移。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
