# Evidence Report

## Metadata

- Time: 2026-07-15 15:58:56 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 8a56bed3
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
runtime/qa/real-pre-env.cjs
runtime/qa/real-pre-env.test.cjs
runtime/qa/real-pre-p0.cjs
runtime/qa/real-pre-preflight.cjs
runtime/qa/real-pre-preflight.test.cjs
~~~

## Owned Git Status

~~~text
M runtime/qa/real-pre-env.cjs
 M runtime/qa/real-pre-env.test.cjs
 M runtime/qa/real-pre-p0.cjs
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                                                                     COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre                                             "sh -c 'java $JAVA_O…"   backend-real-pre    38 seconds ago   Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   sha256:ca8c5caac7dc760c80302458ff44f9443cdbb04c0e6d82eb576c036d3c6d39d8   "/docker-entrypoint.…"   frontend-real-pre   37 seconds ago   Up 8 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine                                                        "docker-entrypoint.s…"   postgres-real-pre   40 seconds ago   Up 35 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                                                            "docker-entrypoint.s…"   redis-real-pre      17 minutes ago   Up 17 minutes (healthy)   6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 8 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 35 seconds (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 17 minutes (healthy)   6379/tcp
campus_frontend                   Up 24 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 24 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 24 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

已根据审查补正凭据传播链：real-pre QA 管理员凭据只从显式 QA_ADMIN_PASSWORD 或本地 .env.real-pre ADMIN_PASSWORD 解析，并只注入实际消费管理员凭据的 E2E_ADMIN_PASSWORD；不再注入通用 E2E_DEFAULT_PASSWORD。验证方式为定向 Node 测试、real-pre preflight、构建、容器重载和健康检查。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
