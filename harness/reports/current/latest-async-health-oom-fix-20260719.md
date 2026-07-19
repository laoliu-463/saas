# Evidence Report

## Metadata

- Time: 2026-07-19 12:21:24 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 0c86f902
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/config/AsyncConfig.java
backend/src/test/java/com/colonel/saas/architecture/HealthcheckContractTest.java
backend/src/test/java/com/colonel/saas/config/AsyncConfigTest.java
docker-compose.real-pre.yml
docker-compose.test.yml
docker-compose.yml
harness/scripts/commands/deploy-remote.ps1
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
PASS: local backend Maven package and frontend Vite build; remote Maven clean package and immutable images built.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    17 minutes ago   Up 17 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   17 minutes ago   Up 16 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   17 hours ago     Up 17 hours (healthy)     5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      16 hours ago     Up 16 hours (healthy)     6379/tcp
NAMES                                                      STATUS                    PORTS
sleepy_meninsky                                            Up 14 seconds             0.0.0.0:44739->5432/tcp, [::]:44739->5432/tcp
testcontainers-ryuk-12ed82ae-205c-46e9-b3bb-9a4cc4999e66   Up 4 minutes              0.0.0.0:46055->8080/tcp, [::]:46055->8080/tcp
saas-active-frontend-real-pre-1                            Up 16 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up 17 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-redis-real-pre-1                               Up 16 hours (healthy)     6379/tcp
saas-test-frontend-1                                       Up 17 hours (healthy)     0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1                                        Up 17 hours (healthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1                                       Up 17 hours (healthy)     0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-active-postgres-real-pre-1                            Up 17 hours (healthy)     5432/tcp
campus_frontend                                            Up 19 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                                             Up 19 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                                            Up 19 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-redis-1                                          Up 19 hours (healthy)     6379/tcp
~~~

## Health Check Result

~~~text
PASS: local backend/frontend health; remote backend HTTP 200 status=UP, frontend /healthz=ok, Docker health healthy/failingStreak=0.
~~~

## Business Validation Result

~~~text
PARTIAL: local real-pre P0 preflight PASS; remote preflight generated /opt/saas/app/runtime/qa/out/real-pre-preflight-20260719-122033 but FAIL because admin login returned HTTP 401, so env guard failed and douyin token readiness is BLOCKED_AUTH.
~~~

## Content Maintenance Result

~~~text
Content maintenance off by task scope.
~~~

## Remote Deploy Result

~~~text
PASS: remote checkout, IMAGE_TAG, image revisions aligned to 0c86f902601b6d1cc8f85f6edfc6b19030fc4ac1; schema contract, Flyway, jar guard, container restart and health checks passed. PostgreSQL backup skipped by explicit user request via -SkipBackup.
~~~

## Retro Summary

根因修复已通过本地构建、重启、健康与 preflight；本次显式跳过远端备份并新增可审计 SkipBackup 开关。远端 admin 401 仍需凭据/账号责任人处理，不能由本次运行擅自修复。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
