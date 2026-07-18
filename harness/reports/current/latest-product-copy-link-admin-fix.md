# Evidence Report

## Metadata

- Time: 2026-07-18 18:05:38 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/fix-product-copy-link-admin
- Commit: 2a0e51c5
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
frontend/src/views/product/ProductLibrary.test.ts
frontend/src/views/product/ProductLibrary.vue
harness/reports/current/latest-harness-limits-check.md
harness/reports/current/latest-product-copy-link-admin-fix.md
harness/scripts/commands/deploy-remote.ps1
harness/scripts/tests/deploy-remote.Tests.ps1
~~~

## Owned Git Status

~~~text
M harness/reports/current/latest-harness-limits-check.md
 M harness/reports/current/latest-product-copy-link-admin-fix.md
~~~

## Build Result

~~~text
PASS: frontend production build. PASS: ProductLibrary and ProductSelectionCard Vitest: 2 files / 30 tests. Full test:all PARTIAL: 14 of 18 steps passed; 4 failed because local 127.0.0.1:3000/8080 test services were unavailable (connection refused/reset).
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED         STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    4 minutes ago   Up 4 minutes (healthy)    127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   4 minutes ago   Up 4 minutes (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   7 minutes ago   Up 7 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago     Up 26 minutes (healthy)   6379/tcp
NAMES                             STATUS                             PORTS
saas-active-frontend-real-pre-1   Up 4 minutes (healthy)             127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 4 minutes (healthy)             127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 7 minutes (healthy)             5432/tcp
saas-active-redis-real-pre-1      Up 26 minutes (healthy)            6379/tcp
campus_frontend                   Up 26 minutes                      0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 26 minutes (healthy)            0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 26 minutes (healthy)            0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 37 seconds (health: starting)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 26 minutes (healthy)            0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 26 minutes (healthy)            6379/tcp
~~~

## Health Check Result

~~~text
PASS: local real-pre backend /api/system/health returned UP and frontend /healthz returned 111 107. PASS: remote backend returned UP and frontend returned ok; remote containers remain on the prior immutable 672baed image.
~~~

## Business Validation Result

~~~text
PASS: component tests prove product library exposes Copy Brief without frontend role inference and emits the promotion-link conversion request. PENDING: authenticated real-browser click was not run.
~~~

## Content Maintenance Result

~~~text
off
~~~

## Remote Deploy Result

~~~text
BLOCKED: remote deployment was stopped before image switch because GitHub origin/feature/auth-system=2a0e51c57a1b61b5b91897f1bed5edbcd4a98121 and Gitee feature/auth-system=7d71a8c343f9c28b1c8a0671f792ba1b0190a039 diverged. Two concurrent remote deployments changed the shared checkout; both were terminated before a mismatched image could be deployed.
~~~

## Retro Summary

Actionable improvement: deployment must acquire one remote lock before changing the shared checkout and require the Gitee mirror SHA to equal the GitHub authority SHA; verify the lock and both SHAs before backup/build.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
