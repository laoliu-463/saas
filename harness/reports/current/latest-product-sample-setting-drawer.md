# Evidence Report

## Metadata

- Time: 2026-07-14 15:57:51 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/product-sample-setting-drawer
- Commit: 0813235e
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/service/ProductSampleSettingService.java
backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
backend/src/test/java/com/colonel/saas/service/ProductSampleSettingServiceTest.java
docs/05-API契约总表.md
frontend/src/types/productManage.ts
frontend/src/views/product/components/SampleSettingModal.test.ts
frontend/src/views/product/components/SampleSettingModal.vue
frontend/src/views/product/sample-setting.test.ts
frontend/src/views/product/sample-setting.ts
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    41 seconds ago   Up 36 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   38 seconds ago   Up 9 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   6 minutes ago    Up 6 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 8 minutes (healthy)    6379/tcp
NAMES                             STATUS                     PORTS
saas-active-frontend-real-pre-1   Up 9 seconds (healthy)     127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 37 seconds (healthy)    127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 6 minutes (healthy)     5432/tcp
campus_frontend                   Up 8 minutes               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 8 minutes (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 8 minutes (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 8 minutes (healthy)     6379/tcp
saas-test-backend-1               Up 2 minutes (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
not collected
~~~

## Content Maintenance Result

~~~text
not collected
~~~

## Additional Verification

~~~text
Frontend targeted tests: PASS (7/7)
Frontend full tests: PASS (93 files, 709 tests)
Frontend typecheck: PASS (vue-tsc -b)
Backend targeted tests: PASS (ProductControllerTest + ProductSampleSettingServiceTest, 32 tests)
Git diff check: PASS
Harness limits: TASK_GATE=PASS; REPOSITORY_HEALTH=PARTIAL due to pre-existing harness/reports file-count debt
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Build and local health gates passed. Business validation remained blocked because real-pre admin login returned HTTP 401 after 5 attempts; no credentials were changed or inferred. Follow-up requires a valid authorized real-pre admin account before running the real product/sample-setting API or browser flow.

## Conclusion

FAIL

## Residual Risk

- Real-pre authenticated API/browser acceptance is not proven (`BLOCKED_AUTH`).
- The existing `saas-test-backend-1` container was reported unhealthy by compose status; it is outside this task's real-pre scope.
- Items marked as not collected are not proof of success.
