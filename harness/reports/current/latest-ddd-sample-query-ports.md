# Evidence Report

## Metadata

- Time: 2026-07-20 19:56:43 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-sample-query-ports
- Commit: db359b6a
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleBoardQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleExportQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleLogisticsQueryPort.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapter.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapter.java
backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapter.java
backend/src/test/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapterTest.java
backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapterTest.java
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
 M backend/src/test/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationServiceTest.java
 M harness/reports/current/latest-content-retire.md
?? backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleBoardQueryPort.java
?? backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleExportQueryPort.java
?? backend/src/main/java/com/colonel/saas/domain/sample/application/port/SampleLogisticsQueryPort.java
?? backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapter.java
?? backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapter.java
?? backend/src/main/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapter.java
?? backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleBoardQueryAdapterTest.java
?? backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleExportQueryAdapterTest.java
?? backend/src/test/java/com/colonel/saas/domain/sample/infrastructure/LegacySampleLogisticsQueryAdapterTest.java
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    40 seconds ago   Up 25 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   38 seconds ago   Up 8 seconds (healthy)    127.0.0.1:3001->80/tcp
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
