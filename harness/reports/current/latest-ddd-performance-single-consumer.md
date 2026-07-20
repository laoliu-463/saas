# Evidence Report

## Metadata

- Time: 2026-07-20 19:36:16 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-performance-single-consumer
- Commit: 2a7d7c00
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M backend/src/test/java/com/colonel/saas/architecture/DddPerformanceRecordGenerationEntrypointTest.java
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    39 seconds ago   Up 25 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   37 seconds ago   Up 9 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   40 seconds ago   Up 36 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      47 hours ago     Up 47 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 9 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 25 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 36 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 47 hours (healthy)     6379/tcp
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
