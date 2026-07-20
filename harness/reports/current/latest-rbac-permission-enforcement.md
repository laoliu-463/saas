# Evidence Report

## Metadata

- Time: 2026-07-20 15:10:39 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/rbac-permission-enforcement
- Commit: 9983c42b
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/architecture/RoleAwareAttributionFlywayIntegrationTest.java
harness/reports/current/latest-rbac-permission-enforcement.md
~~~

## Owned Git Status

~~~text
M backend/src/test/java/com/colonel/saas/architecture/RoleAwareAttributionFlywayIntegrationTest.java
 M harness/reports/current/latest-rbac-permission-enforcement.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                     COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:rbac-permission-enforcement-ci-fix   "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement         "/docker-entrypoint.…"   frontend-real-pre   10 minutes ago       Up 9 minutes (healthy)        127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                        "docker-entrypoint.s…"   postgres-real-pre   17 minutes ago       Up 17 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                            "docker-entrypoint.s…"   redis-real-pre      42 hours ago         Up 42 hours (healthy)         6379/tcp
NAMES                                                      STATUS                        PORTS
saas-active-backend-real-pre-1                             Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
testcontainers-ryuk-d73f4098-5d80-4898-8ede-96d0b2dda05a   Up 2 minutes                  0.0.0.0:45525->8080/tcp, [::]:45525->8080/tcp
saas-active-frontend-real-pre-1                            Up 10 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1                            Up 17 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1                               Up 42 hours (healthy)         6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -B -f backend/pom.xml -Dtest=RoleAwareAttributionFlywayIntegrationTest test)
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

CI migration fixtures must copy every psql include used by production init-db.sql; production directory mounts can hide missing test fixtures.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
