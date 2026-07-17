# Evidence Report

## Metadata

- Time: 2026-07-17 14:53:20 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 12144090
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/TalentController.java
backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
backend/src/main/java/com/colonel/saas/service/TalentService.java
backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java
frontend/src/router/index.test.ts
frontend/src/router/index.ts
frontend/src/router/menuTree.test.ts
frontend/src/router/menuTree.ts
frontend/src/views/dashboard/index.vue
frontend/src/views/data/index.vue
frontend/src/views/talent/components/TalentDetailModal.vue
frontend/src/views/talent/constants.test.ts
frontend/src/views/talent/constants.ts
frontend/src/views/talent/index.test.ts
frontend/src/views/talent/index.vue
harness/reports/current/latest-content-retire.md
runtime/qa/full-browser-e2e.cjs
tests/e2e/11-real-pre-role-business-flow.spec.ts
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/controller/TalentController.java
 M backend/src/main/java/com/colonel/saas/service/TalentQueryService.java
 M backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
 M backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java
 M frontend/src/router/index.test.ts
 M frontend/src/router/index.ts
 M frontend/src/router/menuTree.test.ts
 M frontend/src/router/menuTree.ts
 M frontend/src/views/dashboard/index.vue
 M frontend/src/views/talent/components/TalentDetailModal.vue
 M frontend/src/views/talent/constants.test.ts
 M frontend/src/views/talent/constants.ts
 M frontend/src/views/talent/index.test.ts
 M frontend/src/views/talent/index.vue
 M harness/reports/current/latest-content-retire.md
 M runtime/qa/full-browser-e2e.cjs
 M tests/e2e/11-real-pre-role-business-flow.spec.ts
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
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   39 seconds ago   Up 13 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   52 minutes ago   Up 52 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      47 hours ago     Up 47 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 14 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 37 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 52 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 47 hours (healthy)     6379/tcp
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

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
