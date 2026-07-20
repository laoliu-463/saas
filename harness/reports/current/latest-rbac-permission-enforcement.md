# Evidence Report

## Metadata

- Time: 2026-07-20 15:43:37 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/rbac-permission-enforcement
- Commit: 819a5bed
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/architecture/DddAuthorizationDormancyContractTest.java
backend/src/test/java/com/colonel/saas/domain/user/policy/AuthorizationDecisionPolicyTest.java
backend/src/test/java/com/colonel/saas/service/CharacterizationBaselineTest.java
docker-compose.real-pre.yml
docker-compose.test.yml
docker-compose.yml
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                         COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:rbac-permission-enforcement-main-sync    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:rbac-permission-enforcement-main-sync   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                            "docker-entrypoint.s…"   postgres-real-pre   8 minutes ago        Up 8 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                "docker-entrypoint.s…"   redis-real-pre      43 hours ago         Up 43 hours (healthy)         6379/tcp
NAMES                                                      STATUS                        PORTS
angry_sutherland                                           Up 15 seconds                 0.0.0.0:45795->5432/tcp, [::]:45795->5432/tcp
saas-active-frontend-real-pre-1                            Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
testcontainers-ryuk-5e5d9222-6c36-478b-a970-efdbb4974e37   Up 5 minutes                  0.0.0.0:44845->8080/tcp, [::]:44845->8080/tcp
saas-active-postgres-real-pre-1                            Up 8 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1                               Up 43 hours (healthy)         6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (mvn -B -f backend/pom.xml -Dtest=CharacterizationBaselineTest test)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
