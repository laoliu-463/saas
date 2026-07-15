# Evidence Report

## Metadata

- Time: 2026-07-15 17:31:51 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: bb5333f5
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
frontend/src/utils/clipboard.test.ts
frontend/src/utils/clipboard.ts
~~~

## Owned Git Status

~~~text
M frontend/src/utils/clipboard.test.ts
 M frontend/src/utils/clipboard.ts
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    27 seconds ago   Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   25 seconds ago   Up 7 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   23 minutes ago   Up 23 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 hours ago      Up 2 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 7 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 23 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 23 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 2 hours (healthy)      6379/tcp
campus_frontend                   Up 26 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 26 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 26 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 25 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

本次在 CSP 修复后通过真实浏览器继续定位到 JPEG ClipboardItem 不受支持，已增加 JPEG/WEBP/GIF 转 PNG；后续需在目标聊天软件中复核图文粘贴展示。

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
