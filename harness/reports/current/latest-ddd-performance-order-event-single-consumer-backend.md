# Evidence Report

## Metadata

- Time: 2026-07-16 22:55:33 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-performance-event-single-consumer
- Commit: caa5c2e7
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
docs/superpowers/plans/2026-07-16-performance-order-synced-single-consumer.md
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationService.java
 M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
 M backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceAggregateApplicationServiceTest.java
 M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up 59 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   9 minutes ago        Up 8 minutes (healthy)        127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      31 hours ago         Up 31 hours (healthy)         6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up 59 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-frontend-real-pre-1   Up 8 minutes (healthy)        127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1      Up 31 hours (healthy)         6379/tcp
campus_frontend                   Up 2 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
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
