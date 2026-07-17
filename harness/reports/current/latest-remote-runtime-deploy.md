# Evidence Report

## Metadata

- Time: 2026-07-17 21:40:15 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-remote-runtime-issues
- Commit: 7d785c38
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
harness/reports/current/latest-remote-runtime-deploy.md
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
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up 54 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up 31 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago           Up 2 days (healthy)           6379/tcp
NAMES                                                      STATUS                        PORTS
trusting_dirac                                             Up 35 seconds                 0.0.0.0:45675->5432/tcp, [::]:45675->5432/tcp
saas-active-frontend-real-pre-1                            Up 31 seconds (healthy)       127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 54 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up About a minute (healthy)   5432/tcp
testcontainers-ryuk-570d0c79-ad59-481a-9e99-a26b9b981777   Up 3 minutes                  0.0.0.0:45831->8080/tcp, [::]:45831->8080/tcp
saas-active-redis-real-pre-1                               Up 2 days (healthy)           6379/tcp
campus_frontend                                            Up 3 days                     0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 3 days (healthy)           0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 3 days (healthy)           0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1                                        Up 3 days (unhealthy)         0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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

远端部署按固定入口执行；若 Gitee 镜像未同步，保持提交一致性门禁阻断，不绕过校验。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
