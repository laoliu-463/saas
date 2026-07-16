# Evidence Report

## Metadata

- Time: 2026-07-16 23:19:50 +08:00
- Environment: real-pre
- Scope: docs
- Branch: feature/auth-system
- Commit: 4ec38579
- Owned worktree: clean
- Deploy remote: false

## Owned Files

~~~text
harness/scripts/probes/_admin-credential.ps1
harness/scripts/probes/backfill-async.ps1
harness/scripts/probes/product-library-phase2-dryrun.ps1
harness/scripts/tests/probe-admin-credential.Tests.ps1
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Scope=docs: application build/restart not required; PowerShell parser checks passed for all changed probe scripts.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    19 minutes ago   Up 19 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   19 minutes ago   Up 19 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   19 minutes ago   Up 19 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      32 hours ago     Up 32 hours (healthy)     6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 19 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 19 minutes (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 19 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 32 hours (healthy)     6379/tcp
campus_frontend                   Up 2 days                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 2 days (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 2 days (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 2 days (unhealthy)     0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
PASS: real-pre preflight confirmed local frontend/backend health and environment guard.
~~~

## Business Validation Result

~~~text
PASS: Pester harness/scripts/tests/probe-admin-credential.Tests.ps1: 6 passed, 0 failed. Contract verifies loopback reads the local environment file, remote targets reject local fallback, remote targets use only QA_REMOTE_ADMIN_PASSWORD, both admin-authenticated probes delegate to the resolver without inline credentials, and the database-comparison dry-run probe rejects remote targets. npm run e2e:real-pre:p0:preflight: PASS; frontend/backend health, local administrator login, real-pre environment guard, third-party token readiness, database schema, promotion mapping and cleanup-plan checks all passed.
~~~

## Content Maintenance Result

~~~text
PASS: agent-do content maintenance plan completed; no source/content retirement was performed.
~~~

## Remote Deploy Result

~~~text
Not deployed: this change affects local Harness credential resolution only.
~~~

## Retro Summary

回溯：两处 Harness 探针曾内联管理员密码。现统一为不落库的凭据解析器：本地回环读取本地环境文件，远端仅读取 QA_REMOTE_ADMIN_PASSWORD；数据库比对型探针拒绝远端 URL，避免 API 与数据库跨环境混用。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
