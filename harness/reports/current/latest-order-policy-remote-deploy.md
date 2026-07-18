# Evidence Report

## Metadata

- Time: 2026-07-18 14:26:27 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 8207ebce
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/service/PerformanceMetricsQueryServiceTest.java
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up 25 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   13 minutes ago       Up 13 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      14 minutes ago       Up 13 minutes (healthy)       6379/tcp
NAMES                                                      STATUS                        PORTS
musing_grothendieck                                        Up 47 seconds                 0.0.0.0:45381->5432/tcp, [::]:45381->5432/tcp
testcontainers-ryuk-6a32ccae-f933-4cc8-9c51-59a99809454c   Up 52 seconds                 0.0.0.0:45005->8080/tcp, [::]:45005->8080/tcp
saas-active-frontend-real-pre-1                            Up 27 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up 13 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1                               Up 13 minutes (healthy)       6379/tcp
campus_frontend                                            Up 3 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 3 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 3 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                                        Up 3 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1                                       Up 2 hours (healthy)          0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                                          Up 2 hours (healthy)          6379/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
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
