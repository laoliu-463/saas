# Evidence Report

## Metadata

- Time: 2026-07-16 22:34:43 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: e38061dc
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java
backend/src/main/java/com/colonel/saas/entity/PerformanceCalculationExecution.java
backend/src/test/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisherTest.java
backend/src/test/java/com/colonel/saas/domain/performance/application/PerformanceCalculationExecutionServiceTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                   SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O鈥?   backend-real-pre    2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.鈥?   frontend-real-pre   2 minutes ago   Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s鈥?   postgres-real-pre   4 hours ago     Up 4 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s鈥?   redis-real-pre      31 hours ago    Up 31 hours (healthy)    6379/tcp
NAMES                                                      STATUS                   PORTS
testcontainers-ryuk-74d0adf1-15ff-4f5f-b899-c885c0b51066   Up 8 seconds             0.0.0.0:46423->8080/tcp, [::]:46423->8080/tcp
saas-active-frontend-real-pre-1                            Up 2 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 2 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up 4 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1                               Up 31 hours (healthy)    6379/tcp
campus_frontend                                            Up 2 days                0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 2 days (healthy)      0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 2 days (healthy)      0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                                        Up 2 days (unhealthy)    0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Remote deploy: PASS
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
