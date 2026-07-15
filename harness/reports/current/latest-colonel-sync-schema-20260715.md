# Evidence Report

## Metadata

- Time: 2026-07-15 14:10:31 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 2a8b08ca
- Owned worktree: clean
- Deploy remote: true

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
(clean)
~~~

## Build Result

~~~text
Backend build PASS; targeted tests: ProductStateMigrationContractTest 3/3, AdminColonelPartnerControllerTest 1/1, ColonelPartnerSyncApplicationServiceTest 7/7.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago       Up 4 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About an hour ago   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   5 minutes ago       Up 5 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 22 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-backend-real-pre-1    Up 4 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 5 minutes (healthy)       5432/tcp
saas-active-frontend-real-pre-1   Up About an hour (healthy)   127.0.0.1:3001->80/tcp
campus_frontend                   Up 22 hours                  0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 22 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 22 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 22 hours (healthy)        6379/tcp
saas-test-backend-1               Up 22 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local backend rebuild/restart PASS; local /api/system/health PASS; remote backend/frontend health PASS; remote four real-pre containers healthy.
~~~

## Business Validation Result

~~~text
Local real API sync PASS: login 200, POST /api/admin/colonel-partners/sync 200, upserted=138. Remote real API sync PASS: 200, upserted=139. Local schema query returned 13 mapping rows without missing-column error.
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Remote deploy PASS via deploy-remote.ps1: gitee/feature/auth-system d52e5a6f; schema guard for pick_source_mapping.colonel_name PASS; remote Maven/Docker/JAR guard PASS; remote health PASS; remote sync endpoint PASS; no recent colonel_name/BadSqlGrammarException logs.
~~~

## Retro Summary

根因是字段迁移未接入统一迁移与固定远端部署入口；同时管理员同步控制器重复声明 /api，导致真实 context-path 下接口不可达。已补齐幂等迁移、远端 schema guard、路径回归测试，并完成本地/远端真实接口验证。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
