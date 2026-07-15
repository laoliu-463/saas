# Evidence Report

## Metadata

- Time: 2026-07-15 13:05:16 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: cd1b320a
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/port/UserCrudMutationStore.java
backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
backend/src/test/java/com/colonel/saas/common/exception/GlobalExceptionHandlerTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
harness/reports/current/latest-user-create-username-20260715.md
~~~

## Owned Git Status

~~~text
Owned task files are clean after commit cd1b320a; the repository still contains unrelated pre-existing dirty files outside Owned Files.
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Backend full test: PASS (3207 tests, 0 failures, 0 errors, 3 skipped)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   17 hours ago         Up 17 hours (healthy)         127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago        Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago          Up 21 hours (healthy)         6379/tcp
NAMES                                                      STATUS                        PORTS
nervous_jemison                                            Up 11 seconds                 0.0.0.0:45921->5432/tcp, [::]:45921->5432/tcp
saas-active-backend-real-pre-1                             Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up 2 minutes (healthy)        5432/tcp
testcontainers-ryuk-cdd9c837-2880-4333-80b8-2835b5ed78d8   Up 3 minutes                  0.0.0.0:44931->8080/tcp, [::]:44931->8080/tcp
saas-active-frontend-real-pre-1                            Up 17 hours (healthy)         127.0.0.1:3001->80/tcp
campus_frontend                                            Up 21 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 21 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 21 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1                               Up 21 hours (healthy)         6379/tcp
saas-test-backend-1                                        Up 21 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -f backend/pom.xml '-Dtest=SysUserCRUDApplicationATest,GlobalExceptionHandlerTest,SysUserMapperTest' test)
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote deploy pending: the code commit is pushed to the current branch; deployment branch transplant and remote verification are the remaining steps.
~~~

## Retro Summary

The fix adds a soft-delete-inclusive username precheck and maps the username unique-key race to a duplicate-user response; targeted, mapper, and full backend tests were run. No Harness behavior changed.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
- Harness governance: TASK_GATE=PASS, REPOSITORY_HEALTH=PARTIAL because pre-existing `harness/reports` count is 39 (budget 20) and one historical report is 258 lines.
- State update: `harness/rules/state/snapshots/DOMAIN_STATUS.md` records this user-domain fix.
