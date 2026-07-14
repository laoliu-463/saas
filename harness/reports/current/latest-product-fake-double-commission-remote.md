# Evidence Report

## Metadata

- Time: 2026-07-14 14:44:12 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: d19753a2
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/service/ProductService.java
backend/src/test/java/com/colonel/saas/service/ProductServiceActivityStatusIndependenceTest.java
backend/src/test/java/com/colonel/saas/service/ProductServiceFilterTest.java
docs/领域/商品域.md
frontend/src/views/product/components/ProductCard.test.ts
frontend/src/views/product/components/ProductCard.vue
frontend/src/views/product/ProductDetail.vue
harness/rules/instructions/domain/product-domain.md
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED             STATUS                       PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago       Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago       Up 2 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About an hour ago   Up About an hour (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 weeks ago         Up 25 hours (healthy)        6379/tcp
NAMES                             STATUS                       PORTS
saas-active-frontend-real-pre-1   Up 2 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About an hour (healthy)   5432/tcp
campus_frontend                   Up 25 hours                  5173/tcp
campus_backend                    Up 25 hours (healthy)        0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 25 hours (healthy)        0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-active-redis-real-pre-1      Up 25 hours (healthy)        6379/tcp
saas-test-backend-1               Up 24 hours (unhealthy)      0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Remote deploy: PASS
~~~

## Retro Summary

远端部署以跳过 real-pre 业务预检方式执行，原因是管理员登录 HTTP 401；部署后必须补跑真实业务预检。Harness 报告目录历史债务仍需单独清理。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
