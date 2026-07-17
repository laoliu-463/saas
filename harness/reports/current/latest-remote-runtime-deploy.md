# Evidence Report

## Metadata

- Time: 2026-07-17 21:40:55 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-remote-runtime-issues
- Commit: f7ef775d
- Owned worktree: report pending finalization
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
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago        Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago        Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 days ago           Up 2 days (healthy)           6379/tcp
NAMES                                                      STATUS                        PORTS
thirsty_lederberg                                          Up 21 seconds                 0.0.0.0:45543->5432/tcp, [::]:45543->5432/tcp
saas-active-frontend-real-pre-1                            Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1                             Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1                            Up About a minute (healthy)   5432/tcp
testcontainers-ryuk-570d0c79-ad59-481a-9e99-a26b9b981777   Up 4 minutes                  0.0.0.0:45831->8080/tcp, [::]:45831->8080/tcp
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
BLOCKED before remote mutation.
Expected commit: f7ef775d1814acdb97bacdba0f0bcd1451f81caf
Remote actual commit: 1ed7dd2abef5bcce86221da06ad9db4d21c81446
Cause: deploy-remote.ps1 fetches gitee/feature/auth-system and enforces exact commit match; Gitee mirror is behind origin.
Remote post-check: worktree clean; backend-real-pre, frontend-real-pre, postgres-real-pre and redis-real-pre healthy; backend health UP; frontend HTTP 200.
Remote outbox dispatch index: not present, as expected because deployment stopped before schema migration.
~~~

## Retro Summary

远端部署必须先完成 origin 到 Gitee 的镜像同步，或由维护者更新固定部署入口的镜像来源；本轮保留提交一致性门禁，没有绕过校验。

## Conclusion

PARTIAL：本地 full 构建、容器重启、健康检查和 P0 preflight 通过；远端部署在提交一致性门禁处阻断，远端服务保持原版本。

## Residual Risk

- 远端修复未生效；`input_snapshot` JSONB 修复和 outbox 索引仍待远端部署。
- Gitee `feature/auth-system` 当前为 `1ed7dd2a`，GitHub origin 对应修复提交为 `7d785c38`，部署尝试提交为 `f7ef775d`（仅新增部署 evidence）。
- 前端构建报告 npm audit 有 6 个依赖漏洞（2 high、2 critical）；不属于本次部署阻断根因。
