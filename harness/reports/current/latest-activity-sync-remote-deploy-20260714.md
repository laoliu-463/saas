# Evidence Report

## Metadata

- Time: 2026-07-14 10:12:54 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 9797bda8
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/.dockerignore
backend/Dockerfile
backend/Dockerfile.test
backend/src/main/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinator.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/main/resources/db/alter-colonel-activity-list-sync.sql
backend/src/test/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinatorTest.java
docs/openapi/apifox-sync.md
frontend/src/views/product/activity-list-display.test.ts
frontend/src/views/product/activity-list-display.ts
frontend/src/views/product/activity-sync.test.ts
frontend/src/views/product/activity-sync.ts
frontend/src/views/product/ActivityList.test.ts
frontend/src/views/product/ActivityList.vue
frontend/src/views/product/index.vue
scripts/sync-apifox.sh
scripts/verify-openapi-apifox.sh
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Local fixed agent-do: backend package PASS; frontend vue-tsc/Vite build PASS; targeted backend 53 tests PASS; targeted frontend 23 tests PASS; frontend typecheck PASS; full backend forked suite BLOCKED by host native-memory allocation; full frontend isolated retry PASS (92 files/705 tests with initial scan timeout isolated and passed at 30s).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago   Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   4 minutes ago   Up 3 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 days ago      Up 20 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago     Up 20 hours (healthy)    6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 3 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 4 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 20 hours (healthy)     5432/tcp
campus_frontend                   Up 20 hours               5173/tcp
campus_backend                    Up 20 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 20 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)     6379/tcp
saas-test-backend-1               Up 20 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local: backend 8081 /api/system/health 200 UP; frontend 3001 /healthz 200. Remote: backend/frontend 200; docker compose reports backend, frontend, postgres, redis healthy; remote JAR guard passed host=80994512/container=80994512.
~~~

## Business Validation Result

~~~text
real-pre P0 preflight 2026-07-14 10:11: status FAIL, canRunBusinessFlows=false. frontend/backend/schema/reusable mapping/cleanup plan PASS; admin login FAIL after 5 attempts HTTP 401; env guard FAIL admin token unavailable; Douyin token readiness BLOCKED_AUTH. Full real-pre business flow was not executed.
~~~

## Content Maintenance Result

~~~text
OFF (not requested)
~~~

## Remote Deploy Result

~~~text
Remote deploy script PASS. Gitee feature/auth-system and server /opt/saas/app HEAD=9797bda892b76f0c4813bbdb11fc97477978e3; migration guards PASS including scope column/index; remote Maven package PASS; Docker Compose rebuild/restart PASS; product sync env and ProductActivitySyncJob config checks PASS.
~~~

## Retro Summary

Root-cause fixes: backend Docker context now includes target/*.jar; activity sync migration now upgrades legacy job-log tables with scope before creating the active-scope index; Apifox sanitizer variable names no longer trigger the repository plaintext-secret heuristic. Follow-up: provision/fix the real-pre admin credentials before business E2E; review existing reports-root governance debt separately.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
