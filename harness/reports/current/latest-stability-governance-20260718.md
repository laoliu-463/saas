# Evidence Report

## Metadata

- Time: 2026-07-18 00:59:33 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 4faee3f8
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/pom.xml
backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/colonel/saas/common/result/ApiResult.java
backend/src/main/java/com/colonel/saas/common/web/RequestIdContext.java
backend/src/main/java/com/colonel/saas/common/web/RequestIdFilter.java
backend/src/main/java/com/colonel/saas/config/RuntimeExposurePolicy.java
backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityHealthIndicator.java
backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityProbe.java
backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityStartupGuard.java
backend/src/main/java/com/colonel/saas/controller/SchemaReadinessController.java
backend/src/main/resources/application.yml
backend/src/test/java/com/colonel/saas/common/web/RequestIdFilterTest.java
backend/src/test/resources/application-test.yml
github/workflows/ci.yml
~~~

## Owned Git Status

~~~text
M backend/pom.xml
 M backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java
 M backend/src/main/java/com/colonel/saas/common/result/ApiResult.java
 M backend/src/main/java/com/colonel/saas/config/RuntimeExposurePolicy.java
 M backend/src/main/resources/application.yml
 M backend/src/test/resources/application-test.yml
?? backend/src/main/java/com/colonel/saas/common/web/RequestIdContext.java
?? backend/src/main/java/com/colonel/saas/common/web/RequestIdFilter.java
?? backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityHealthIndicator.java
?? backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityProbe.java
?? backend/src/main/java/com/colonel/saas/config/SchemaCompatibilityStartupGuard.java
?? backend/src/test/java/com/colonel/saas/common/web/RequestIdFilterTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                                           IMAGE                                                                     COMMAND                  SERVICE             CREATED          STATUS                    PORTS
a19fc2195055_saas-active-postgres-real-pre-1   postgres:15-alpine                                                        "docker-entrypoint.s…"   postgres-real-pre   39 minutes ago   Up 39 minutes (healthy)   5432/tcp
saas-active-backend-real-pre-1                 colonel-saas/backend:real-pre                                             "sh -c 'java $JAVA_O…"   backend-real-pre    18 minutes ago   Up 18 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1                sha256:a0c02fe0b2486870f06d22454e31740a67427a88a20d2cb28471a95946a3aca4   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago      Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   redis:7-alpine                                                            "docker-entrypoint.s…"   redis-real-pre      2 days ago       Up 2 days (healthy)       6379/tcp
NAMES                                          STATUS                    PORTS
saas-active-backend-real-pre-1                 Up 18 minutes (healthy)   127.0.0.1:8081->8080/tcp
a19fc2195055_saas-active-postgres-real-pre-1   Up 39 minutes (healthy)   5432/tcp
saas-active-frontend-real-pre-1                Up 2 hours (healthy)      127.0.0.1:3001->80/tcp
saas-active-redis-real-pre-1                   Up 2 days (healthy)       6379/tcp
campus_frontend                                Up 3 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                 Up 3 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                Up 3 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                            Up 3 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
not collected
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

agent-do failed: Frontend build failed.

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.
