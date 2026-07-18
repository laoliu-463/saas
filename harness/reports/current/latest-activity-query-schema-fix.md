# Evidence Report

## Metadata

- Time: 2026-07-18 00:42:04 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 76a5e2e8
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/resources/db/alter-role-aware-promotion-link-attribution-20260716.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/test/java/com/colonel/saas/config/RealPreMigrationContractTest.java
docker-compose.real-pre.yml
harness/reports/current/latest-activity-query-schema-fix.md
harness/rules/changelog.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/scripts/commands/deploy-remote.ps1
harness/scripts/probes/activity-query-schema.ps1
~~~

## Owned Git Status

~~~text
M backend/src/main/resources/db/migrate-all.sql
 M backend/src/test/java/com/colonel/saas/config/RealPreMigrationContractTest.java
 M docker-compose.real-pre.yml
 M harness/scripts/commands/deploy-remote.ps1
?? backend/src/main/resources/db/alter-role-aware-promotion-link-attribution-20260716.sql
?? harness/reports/current/latest-activity-query-schema-fix.md
?? harness/scripts/probes/activity-query-schema.ps1
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                                           IMAGE                                                                     COMMAND                  SERVICE             CREATED          STATUS                    PORTS
a19fc2195055_saas-active-postgres-real-pre-1   postgres:15-alpine                                                        "docker-entrypoint.s…"   postgres-real-pre   22 minutes ago   Up 22 minutes (healthy)   5432/tcp
saas-active-backend-real-pre-1                 colonel-saas/backend:real-pre                                             "sh -c 'java $JAVA_O…"   backend-real-pre    46 seconds ago   Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1                sha256:a0c02fe0b2486870f06d22454e31740a67427a88a20d2cb28471a95946a3aca4   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   redis:7-alpine                                                            "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                                          STATUS                    PORTS
saas-active-backend-real-pre-1                 Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
a19fc2195055_saas-active-postgres-real-pre-1   Up 22 minutes (healthy)   5432/tcp
saas-active-frontend-real-pre-1                Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   Up 2 days (healthy)       6379/tcp
campus_frontend                                Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                 Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                            Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
not collected
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

agent-do failed: Business validation failed: powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\probes\activity-query-schema.ps1

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.
