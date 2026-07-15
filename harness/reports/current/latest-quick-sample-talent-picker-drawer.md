# Evidence Report

## Metadata

- Time: 2026-07-15 18:42:35 +08:00
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Commit: edc64798
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
frontend/src/views/product/components/QuickSampleTalentPicker.test.ts
frontend/src/views/product/components/QuickSampleTalentPicker.vue
harness/reports/current/latest-content-retire.md
~~~

## Owned Git Status

~~~text
M frontend/src/views/product/components/QuickSampleTalentPicker.test.ts
 M frontend/src/views/product/components/QuickSampleTalentPicker.vue
 M harness/reports/current/latest-content-retire.md
~~~

## Build Result

~~~text
not collected
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    46 seconds ago   Up 38 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   42 seconds ago   Up 15 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 hours ago      Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago      Up 3 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 16 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 39 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 2 hours (healthy)      5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)      6379/tcp
campus_frontend                   Up 27 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 27 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
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
