# Evidence Report

## Metadata

- Time: 2026-07-14 17:06:54 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: df76fef6
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/controller/ProductController.java
backend/src/main/java/com/colonel/saas/service/ProductSampleSettingService.java
backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java
backend/src/test/java/com/colonel/saas/service/ProductSampleSettingServiceTest.java
docs/05-API契约总表.md
docs/superpowers/specs/2026-07-14-product-sample-setting-drawer-design.md
frontend/src/types/productManage.ts
frontend/src/views/product/components/SampleSettingModal.test.ts
frontend/src/views/product/components/SampleSettingModal.vue
frontend/src/views/product/sample-setting.test.ts
frontend/src/views/product/sample-setting.ts
harness/reports/current/latest-product-sample-setting-drawer.md
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    28 seconds ago   Up 24 seconds (healthy)      127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   26 seconds ago   Up 8 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   52 minutes ago   Up 52 minutes (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up About an hour (healthy)   6379/tcp
NAMES                             STATUS                         PORTS
saas-active-frontend-real-pre-1   Up 8 seconds (healthy)         127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 24 seconds (healthy)        127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 52 minutes (healthy)        5432/tcp
campus_frontend                   Up About an hour               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up About an hour (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up About an hour (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up About an hour (healthy)     6379/tcp
saas-test-backend-1               Up About an hour (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

agent-do failed: Business validation failed: npm run e2e:real-pre:p0:preflight

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.
