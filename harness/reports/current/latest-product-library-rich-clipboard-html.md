# Evidence Report

## Metadata

- Time: 2026-07-15 18:07:16 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 3016ae7c
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/领域/商品域.md
docs/验收/验收证据索引.md
frontend/src/utils/clipboard.test.ts
frontend/src/utils/clipboard.ts
harness/reports/current/latest-content-retire.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M docs/领域/商品域.md
 M docs/验收/验收证据索引.md
 M frontend/src/utils/clipboard.test.ts
 M frontend/src/utils/clipboard.ts
 M harness/reports/current/latest-content-retire.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
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
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    45 seconds ago   Up 41 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   43 seconds ago   Up 25 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   58 minutes ago   Up 58 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 hours ago      Up 2 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 25 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 41 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 58 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 2 hours (healthy)      6379/tcp
campus_frontend                   Up 26 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 26 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 26 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 26 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
