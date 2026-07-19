# Evidence Report

## Metadata

- Time: 2026-07-14 00:06:10 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 1a0bb2e7
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/main/resources/db/alter-product-library-query-performance-20260625.sql
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
docs/performance/order-query-performance-baseline.md
docs/对接/活动商品同步.md
harness/reports/current/latest-retro-evidence-20260713.md
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/service/ProductService.java
 M harness/reports/current/latest-retro-evidence-20260713.md
~~~

## Build Result

~~~text
PASS: mvn -f backend/pom.xml -DskipTests package; standard targeted Maven regression 59 tests, 0 failures, 0 errors, 0 skipped.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                   PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago   Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 hours ago     Up 2 hours (healthy)     127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 days ago      Up 10 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago     Up 10 hours (healthy)    6379/tcp
NAMES                             STATUS                   PORTS
saas-active-backend-real-pre-1    Up 4 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 2 hours (healthy)     127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 10 hours (healthy)    5432/tcp
campus_frontend                   Up 10 hours              5173/tcp
campus_backend                    Up 10 hours (healthy)    0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 10 hours (healthy)    0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 10 hours (healthy)    6379/tcp
saas-test-backend-1               Up 9 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: latest restart-compose backend and verify-local returned HTTP 200 status UP.
~~~

## Business Validation Result

~~~text
PARTIAL: pre-change real-pre activity 3223881 fetched 100 rows but job took about 7066ms; page-mode unit test passed with retrieveMode=0 and 4 bounded page requests; post-latest-restart real-pre request pending user trigger. DB: count query changed from about 307ms Bitmap Heap Scan to about 13.8ms Index Only Scan after idx_ps_activity_active_count.
~~~

## Content Maintenance Result

~~~text
PASS task gate; repository health PARTIAL because pre-existing harness reports historical file-count and line-count debt remains.
~~~

## Remote Deploy Result

~~~text
Not deployed remotely; user did not request remote deployment.
~~~

## Retro Summary

已生成 latest-retro-evidence-20260713.md；本轮不新增 Harness 规则，历史报告清理需用户授权。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
