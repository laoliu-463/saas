# Evidence Report

## Metadata

- Time: 2026-07-16 22:19:20 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: 1a70bf08
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java
backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/service/OrderAttributionReplayService.java
 M backend/src/test/java/com/colonel/saas/service/OrderAttributionReplayServiceTest.java
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
NAME                              IMAGE                            COMMAND                   SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O鈥?   backend-real-pre    31 seconds ago   Up 26 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.鈥?   frontend-real-pre   29 seconds ago   Up 10 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s鈥?   postgres-real-pre   3 hours ago      Up 3 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s鈥?   redis-real-pre      31 hours ago     Up 31 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 10 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 27 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 3 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 31 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
