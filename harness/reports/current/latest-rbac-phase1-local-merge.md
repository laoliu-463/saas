# Evidence Report

## Metadata

- Time: 2026-07-14 13:32:34 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 03ca7f45
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/config/DomainPolicyConfig.java
backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationDecision.java
backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationReason.java
backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationScope.java
backend/src/main/java/com/colonel/saas/domain/user/api/PermissionCode.java
backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java
backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSnapshot.java
backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationSubject.java
backend/src/main/java/com/colonel/saas/domain/user/domain/GrantedRolePermission.java
backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicy.java
backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java
backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java
backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationSnapshotRow.java
backend/src/main/resources/db/alter-authorization-foundation-20260713.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java
backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationSchemaContractTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/AuthorizationApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSchemaMigrationIntegrationTest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/AuthorizationSnapshotStoreIntegrationTest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicyTest.java
backend/src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
docs/07-权限与数据范围.md
docs/领域/用户域.md
docs/superpowers/plans/2026-07-13-ddd-rbac-foundation.md
docs/superpowers/plans/2026-07-13-ddd-rbac-program-roadmap.md
harness/reports/content-retire-20260713-221307.md
harness/reports/current/latest-rbac-phase1-local-merge.md
harness/reports/evidence-20260713-221247.md
harness/reports/evidence-20260713-221308.md
harness/reports/evidence-20260714-110352.md
harness/reports/evidence-20260714-120838.md
harness/reports/latest-evidence-20260714.md
harness/reports/retro-20260713-221320.md
harness/reports/retro-20260714-110717.md
harness/reports/retro-20260714-120908.md
~~~

## Owned Git Status

~~~text
?? harness/reports/current/latest-rbac-phase1-local-merge.md
~~~

## Build Result

~~~text
Focused RBAC tests: PASS (91/91, 0 failures/errors/skips). Full backend tests: PASS (3196 tests, 0 failures, 0 errors, 3 skipped). Backend package: PASS.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago       Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About an hour ago   Up About an hour (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago       Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 24 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)        5432/tcp
saas-active-frontend-real-pre-1   Up About an hour (healthy)    127.0.0.1:3001->80/tcp
campus_frontend                   Up 24 hours                   5173/tcp
campus_backend                    Up 24 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 24 hours (healthy)         6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Backend Compose rebuild/restart: PASS. Backend health: PASS (UP). Final local real-pre RBAC tables=0 and authz_version columns=0; migration/runtime activation not executed.
~~~

## Business Validation Result

~~~text
BLOCKED/PENDING: runtime/qa/out/real-pre-preflight-20260714-133121/report.md; frontend/backend/schema/mapping/cleanup-plan PASS; admin login failed after 5 HTTP 401 responses, admin token unavailable, env guard FAIL, Douyin readiness BLOCKED_AUTH.
~~~

## Content Maintenance Result

~~~text
Not executed; local branch merge verification only.
~~~

## Remote Deploy Result

~~~text
NOT EXECUTED: local merge option selected; no base-branch push and no remote deployment.
~~~

## Retro Summary

Local merge verification used fixed build/restart/verify/preflight/evidence scripts instead of agent-do because agent-do automatically pushes on success, which conflicts with the user's explicit local-merge choice. Task gate PASS; repository health remains PARTIAL from historical Harness debt. No standalone retro: no new Harness behavior change is proposed.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
