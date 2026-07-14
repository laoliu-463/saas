# Evidence Report

## Metadata

- Time: 2026-07-14 09:55:32 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 5ca19bfb
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/.dockerignore
backend/src/main/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinator.java
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/main/resources/db/alter-colonel-activity-list-sync.sql
backend/src/test/java/com/colonel/saas/service/ProductActivitySyncWriteCoordinatorTest.java
docs/openapi/apifox-sync.md
frontend/src/views/product/ActivityList.test.ts
frontend/src/views/product/ActivityList.vue
frontend/src/views/product/activity-list-display.test.ts
frontend/src/views/product/activity-list-display.ts
frontend/src/views/product/activity-sync.test.ts
frontend/src/views/product/activity-sync.ts
frontend/src/views/product/index.vue
scripts/sync-apifox.sh
scripts/verify-openapi-apifox.sh
~~~

## Owned Git Status

~~~text
M backend/src/main/resources/db/alter-colonel-activity-list-sync.sql
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    41 seconds ago   Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   39 seconds ago   Up 20 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 days ago       Up 20 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 20 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 21 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 20 hours (healthy)     5432/tcp
campus_frontend                   Up 20 hours               5173/tcp
campus_backend                    Up 20 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 20 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 20 hours (healthy)     6379/tcp
saas-test-backend-1               Up 19 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance skipped by -ContentMaintenance off.
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
