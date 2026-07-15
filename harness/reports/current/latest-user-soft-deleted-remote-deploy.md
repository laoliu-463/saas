# Evidence Report

## Metadata

- Time: 2026-07-15 14:56:56 +08:00
- Environment: real-pre
- Scope: backend
- Branch: feature/auth-system
- Commit: 5c224e53
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/port/UserCrudMutationStore.java
backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
docs/领域/用户域.md
harness/reports/current/latest-user-soft-deleted-restore.md
harness/reports/current/retro-user-soft-deleted-restore.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Remote Maven/Docker build: PASS (`mvn -f backend/pom.xml -DskipTests clean package`; remote JAR size 81065763 bytes).
Remote branch fast-forward: PASS (`d52e5a6f` → `5c224e53`).
User-domain tests on remote deployment baseline: PASS (36 non-container tests; `SysUserMapperTest` 15 tests).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    40 seconds ago   Up 27 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   41 seconds ago   Up 38 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 23 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 27 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 38 seconds (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
campus_frontend                   Up 23 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 23 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 23 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 23 hours (healthy)     6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Remote health verification: PASS (`/api/system/health` returned `{"status":"UP"}`; frontend `/login` returned HTTP 200).
~~~

## Business Validation Result

~~~text
Remote real-pre preflight: PASS. Admin login, real-pre environment guard, token readiness, schema readiness and cleanup plan all passed.
Remote user lifecycle API: PASS. One-time user completed create HTTP 200 → delete HTTP 200 → recreate HTTP 200 → delete HTTP 200; recreate returned the same user ID.
Remote SQL after cleanup: PASS. The one-time user is `deleted=1`; `sys_user_username_key` remains a global unique index.
~~~

## Content Maintenance Result

~~~text
Content maintenance was not applicable to this code deployment.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS. `deploy-remote.ps1` completed remote schema guards, Maven build, backend/frontend Compose rebuild, JAR guard, backend health, frontend health and product-sync environment guard.
~~~

## Retro Summary

The first `agent-do` attempt stopped before remote deployment because the isolated branch name differed from its configured `gitee/feature/auth-system` upstream. The deployment was then completed with the fixed `deploy-remote.ps1` after an explicit `HEAD:feature/auth-system` push. No source code was changed during recovery.

## Conclusion

PASS

## Residual Risk

- The user create/delete/recreate path is verified on the deployed remote real-pre service.
- The temporary verification user was deleted and confirmed soft-deleted in PostgreSQL.
- This report proves the user-domain deployment and lifecycle path, not unrelated full-system P0 business coverage.
