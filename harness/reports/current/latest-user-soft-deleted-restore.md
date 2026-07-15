# Evidence Report

## Metadata

- Time: 2026-07-15 14:42:56 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 582960ac
- Owned worktree: dirty
- Deploy remote: false

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
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/reports/current/latest-user-soft-deleted-restore.md
harness/reports/current/retro-user-soft-deleted-restore.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
 M backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
 M backend/src/main/java/com/colonel/saas/domain/user/port/UserCrudMutationStore.java
 M backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
M backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
 M backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
 M backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
 M docs/领域/用户域.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
?? harness/reports/current/latest-user-soft-deleted-restore.md
?? harness/reports/current/retro-user-soft-deleted-restore.md
~~~

## Build Result

~~~text
Backend build: PASS (`mvn -f backend/pom.xml -DskipTests package`)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    34 seconds ago   Up 30 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   37 minutes ago   Up 37 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 23 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 31 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 37 minutes (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
campus_frontend                   Up 23 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 23 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 23 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 23 hours (healthy)     6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Targeted user-domain tests: PASS (61 tests, 0 failures, 0 errors).
Mapper integration tests: PASS (`SysUserMapperTest`, 15 tests, PostgreSQL Testcontainers, 0 failures, 0 errors).
`git diff --check`: PASS.
Local API smoke: `GET http://127.0.0.1:8081/api/users` returned HTTP 401 without credentials; the endpoint is mounted and protected.
Local SQL probe: no `玄同` row exists in the local real-pre database, so the positive API restore path could not be exercised against local business data.

Real-pre P0 preflight: BLOCKED/FAIL. Frontend, backend health, database schema and cleanup-plan checks passed; admin login failed after 5 attempts with HTTP 401, so the preflight correctly withheld the real business flow.
~~~

## Content Maintenance Result

~~~text
not run because real-pre business validation failed at admin login.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

agent-do failed: Business validation failed: npm run e2e:real-pre:p0:preflight

## Root Cause and Fix

- Remote diagnosis showed `sys_user` contained `玄同` with `deleted=1`, while active list queries use `deleted=0`; the global `sys_user_username_key` still rejected the username.
- New-user creation now restores the matching soft-deleted row in place under `deleted=1`, preserving the primary key and unique constraint while overwriting the requested password/profile/organization/status/channel code, clearing the old login time and rebuilding roles.
- Active users with the same username still return the duplicate-user business error.

## Conclusion

PARTIAL

## Residual Risk

- Code, tests, backend build, container restart and health check passed.
- Positive real-pre API creation could not be proven because the configured QA admin credential receives HTTP 401; this is an environment/authentication blocker, not evidence that the user fix failed.
- Remote deployment was not executed in this turn.
- Harness limits check: BLOCKED by pre-existing timestamp reports in `harness/reports/` and historical report-budget debt; no unrelated report files were deleted.
