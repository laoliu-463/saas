# Evidence Report

## Metadata

- Time: 2026-06-13 14:26:23 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/ddd/DDD-ORDER-006
- Commit: 8cd3a8c0
- Worktree: dirty
- Deploy remote: false

## Modified Files

~~~text
harness/reports/content-retire-20260613-142622.md
harness/reports/ddd-order-006-2026-06-13.md
harness/reports/latest-harness-limits-check.md
harness/reports/retro-20260613-142547.md
harness/rules/locks/DDD-ORDER-006.lock.md
harness/tasks/DDD-ORDER-006-handover.md
~~~

## Git Status

~~~text
M  harness/reports/ddd-order-006-2026-06-13.md
 M harness/reports/latest-harness-limits-check.md
M  harness/rules/locks/DDD-ORDER-006.lock.md
M  harness/tasks/DDD-ORDER-006-handover.md
?? harness/reports/content-retire-20260613-142622.md
?? harness/reports/retro-20260613-142547.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

### docker compose ps

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                            PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    24 seconds ago       Up 20 seconds (healthy)           127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   22 seconds ago       Up 5 seconds (health: starting)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      About a minute ago   Up About a minute (healthy)       6379/tcp
~~~

### docker ps

~~~text
NAMES                             STATUS                            PORTS
saas-active-frontend-real-pre-1   Up 5 seconds (health: starting)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 20 seconds (healthy)           127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)       5432/tcp
saas-active-redis-real-pre-1      Up About a minute (healthy)       6379/tcp
saas-test-frontend-1              Up 22 hours (healthy)             0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
saas-test-backend-1               Up 22 hours (healthy)             0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 26 hours (healthy)             0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 26 hours (healthy)             6379/tcp
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

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
- If real-pre lacks real orders or pick_source samples, record the result as PENDING or PARTIAL.
