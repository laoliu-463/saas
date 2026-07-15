# Evidence Report

## Metadata

- Time: 2026-07-15 14:56:53 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/remote-user-soft-delete-deploy-20260715
- Commit: 947a9152
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
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    37 seconds ago   Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   38 seconds ago   Up 34 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 23 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 34 seconds (healthy)   5432/tcp
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

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
