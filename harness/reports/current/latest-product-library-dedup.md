# Evidence Report

## Metadata

- Time: 2026-07-14 14:02:08 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: e01b5926
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
docs/决策/ADR-010-仓库阶段口径拍板为V2.md
docs/领域/商品域.md
harness/reports/current/latest-product-library-dedup.md
harness/rules/instructions/domain/product-domain.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Backend full tests: PASS (3197 tests, 0 failures, 0 errors, 3 skipped)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    10 minutes ago   Up 10 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   10 minutes ago   Up 10 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   31 minutes ago   Up 31 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago      Up 24 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 10 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 10 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 31 minutes (healthy)   5432/tcp
campus_frontend                   Up 24 hours               5173/tcp
campus_backend                    Up 24 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 24 hours (healthy)     6379/tcp
saas-test-backend-1               Up 23 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS (backend /api/system/health=200 UP; frontend /healthz=200)
~~~

## Business Validation Result

~~~text
FAIL/BLOCKED: real-pre preflight admin login returned HTTP 401 after 5 attempts; admin token unavailable; Douyin token readiness BLOCKED_AUTH. Business flow not executed.
~~~

## Content Maintenance Result

~~~text
Content maintenance: plan (no source retirement)
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

Real-pre authentication is the remaining blocker: admin login returned HTTP 401, so no real business-flow proof was collected. No additional Harness improvement was identified.

## Conclusion

FAIL

## Residual Risk

- Items marked as not collected are not proof of success.
