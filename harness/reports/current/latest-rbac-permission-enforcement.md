# Evidence Report

## Metadata

- Time: 2026-07-20 15:01:04 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/rbac-permission-enforcement
- Commit: 4ae83ba3
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/annotation/RequirePermission.java
backend/src/main/java/com/colonel/saas/aspect/PermissionGuardAspect.java
backend/src/main/java/com/colonel/saas/auth
backend/src/main/java/com/colonel/saas/controller
backend/src/main/java/com/colonel/saas/domain/user
backend/src/main/java/com/colonel/saas/mapper/SysRolePermissionMapper.java
backend/src/main/java/com/colonel/saas/vo/AuthorizationPermissionVO.java
backend/src/main/resources/db/alter-authorization-permission-catalog-20260720.sql
backend/src/main/resources/db/init-db.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test
frontend/src
harness/reports/current/latest-rbac-permission-enforcement.md
package.json
runtime/qa/rbac-permission-real-pre-probe.cjs
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-rbac-permission-enforcement.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                               COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:rbac-permission-enforcement    "sh -c 'java $JAVA_O…"   backend-real-pre    53 seconds ago   Up 47 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement   "/docker-entrypoint.…"   frontend-real-pre   50 seconds ago   Up 23 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                  "docker-entrypoint.s…"   postgres-real-pre   7 minutes ago    Up 7 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                      "docker-entrypoint.s…"   redis-real-pre      42 hours ago     Up 42 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 23 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 47 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 7 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      Up 42 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run qa:real-pre:rbac)
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

RBAC deployment must use an immutable task-specific image tag, apply its idempotent migration, and run qa:real-pre:rbac; shared real-pre image tags can race across concurrent worktrees.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
