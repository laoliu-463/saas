# Evidence Report

## Metadata

- Time: 2026-07-18 18:29:58 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-product-copy-link-admin
- Commit: 498e1719
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/resources/db/alter-cso-dual-attribution-status-20260716.sql
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
harness/reports/current/latest-product-copy-link-admin-fix.md
harness/scripts/commands/deploy-remote.ps1
harness/scripts/tests/deploy-remote.Tests.ps1
scripts/check-real-pre-schema.sh
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-product-copy-link-admin-fix.md
~~~

## Build Result

~~~text
PASS: backend Maven package. PASS: frontend production build. PASS: ProductLibrary/ProductSelectionCard Vitest 30/30. PASS: deployment Pester 8/8. PASS: restored migration and schema-guard helper match their historical source hashes. Full agent-do business preflight was BLOCKED_AUTH only.
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    2 minutes ago   Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 minutes ago   Up 2 minutes (healthy)        6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 2 minutes (healthy)        127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      Up 2 minutes (healthy)        6379/tcp
campus_frontend                   Up 51 minutes                 0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 51 minutes (healthy)       0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 51 minutes (healthy)       0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Restarting (1) 1 second ago
saas-test-postgres-1              Up 51 minutes (healthy)       0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 51 minutes (healthy)       6379/tcp
~~~

## Health Check Result

~~~text
PASS: local real-pre backend returned UP and frontend healthz returned 111 107 after restart. PASS: remote backend returned UP and frontend returned ok; both remote containers healthy.
~~~

## Business Validation Result

~~~text
PASS: component coverage proves Copy Brief is available without frontend role inference and emits the conversion request. BLOCKED_AUTH: local real-pre Douyin status reported hasAccessToken=false and hasRefreshToken=false; authenticated real upstream click was not run.
~~~

## Content Maintenance Result

~~~text
off
~~~

## Remote Deploy Result

~~~text
PASS: deployed SHA 498e1719bfd96f276820c931f98c3cd51a50e3e9. Remote source head, backend image, frontend image, and both OCI revision labels match exactly. Database migration and read-only schema guard passed before the immutable image switch.
~~~

## Retro Summary

Actionable improvement: deploy preflight now verifies migration includes and helper scripts; retain a single remote deployment lock and require Gitee/GitHub SHA identity before changing the shared remote checkout.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
